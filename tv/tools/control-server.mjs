#!/usr/bin/env node
/**
 * 局域网控制 + 音准 WebSocket 中继
 *
 * 兼容旧接口：
 *   GET  /events?session=xxx   SSE（H5 管理台）
 *   POST /action?session=xxx   HTTP 广播（H5 管理台）
 *
 * 新增：
 *   WS   /ws/pitch?session=xxx&role=phone|tv
 *        phone 发布 PitchResult，tv 订阅显示
 */
import http from 'node:http'
import os from 'node:os'
import { URL } from 'node:url'
import { WebSocketServer } from 'ws'

const port = Number(process.env.CONTROL_PORT || 9091)
const clientsBySession = new Map()
/** @type {Map<string, Set<import('ws').WebSocket>>} */
const pitchRooms = new Map()

function listLanIps() {
  const ips = []
  const nets = os.networkInterfaces()
  for (const name of Object.keys(nets || {})) {
    for (const net of nets[name] || []) {
      if (net.family !== 'IPv4' && net.family !== 4) continue
      if (net.internal) continue
      ips.push(net.address)
    }
  }
  return ips
}

function primaryLanIp() {
  const ips = listLanIps()
  return (
    ips.find((ip) => ip.startsWith('192.168.')) ||
    ips.find((ip) => ip.startsWith('10.')) ||
    ips.find((ip) => /^172\.(1[6-9]|2\d|3[0-1])\./.test(ip)) ||
    ips[0] ||
    ''
  )
}

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type')
}

function getSet(session) {
  if (!clientsBySession.has(session)) clientsBySession.set(session, new Set())
  return clientsBySession.get(session)
}

function getPitchRoom(session) {
  if (!pitchRooms.has(session)) pitchRooms.set(session, new Set())
  return pitchRooms.get(session)
}

function sendEvent(res, event, data) {
  res.write(`event: ${event}\n`)
  res.write(`data: ${JSON.stringify(data)}\n\n`)
}

function safeSend(ws, payload) {
  if (ws.readyState !== 1) return false
  try {
    ws.send(typeof payload === 'string' ? payload : JSON.stringify(payload))
    return true
  } catch {
    return false
  }
}

function broadcastPitch(session, payload, except) {
  const room = pitchRooms.get(session)
  if (!room) return 0
  let n = 0
  for (const client of room) {
    if (client === except) continue
    if (client.role === 'phone') continue
    if (safeSend(client, payload)) n += 1
  }
  return n
}

/** TV 发布节拍事件 → 转发给同 session 的 phone */
function broadcastBeat(session, payload, except) {
  const room = pitchRooms.get(session)
  if (!room) return 0
  let n = 0
  for (const client of room) {
    if (client === except) continue
    if (client.role !== 'phone') continue
    if (safeSend(client, payload)) n += 1
  }
  return n
}

const server = http.createServer((req, res) => {
  cors(res)
  if (req.method === 'OPTIONS') {
    res.statusCode = 204
    res.end()
    return
  }

  const url = new URL(req.url, `http://${req.headers.host}`)

  if (req.method === 'GET' && url.pathname === '/health') {
    const lanIps = listLanIps()
    const lanIp = primaryLanIp()
    res.setHeader('Content-Type', 'application/json')
    res.end(
      JSON.stringify({
        ok: true,
        port,
        lanIp,
        lanIps,
        sseSessions: clientsBySession.size,
        pitchSessions: pitchRooms.size,
      }),
    )
    return
  }

  if (req.method === 'GET' && url.pathname === '/events') {
    const session = url.searchParams.get('session') || 'default'
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
    })
    const set = getSet(session)
    set.add(res)
    sendEvent(res, 'ready', { session })

    const heartbeat = setInterval(() => sendEvent(res, 'ping', { t: Date.now() }), 15000)

    req.on('close', () => {
      clearInterval(heartbeat)
      set.delete(res)
    })
    return
  }

  if (req.method === 'POST' && url.pathname === '/action') {
    const session = url.searchParams.get('session') || 'default'
    let raw = ''
    req.on('data', (chunk) => {
      raw += chunk
      if (raw.length > 10 * 1024 * 1024) req.destroy()
    })
    req.on('end', () => {
      let payload = {}
      try {
        payload = raw ? JSON.parse(raw) : {}
      } catch {
        res.statusCode = 400
        res.end(JSON.stringify({ ok: false, error: 'invalid json' }))
        return
      }
      const set = getSet(session)
      for (const client of set) {
        sendEvent(client, 'action', payload)
      }
      // 同步桥接到 WebSocket TV（便于统一协议调试）
      if (payload && payload.type === 'pitch') {
        broadcastPitch(session, payload, null)
      }
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({ ok: true, session, listeners: set.size }))
    })
    return
  }

  res.statusCode = 404
  res.end('Not Found')
})

const wss = new WebSocketServer({ noServer: true })

server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url || '/', `http://${req.headers.host}`)
  if (url.pathname !== '/ws/pitch') {
    socket.destroy()
    return
  }
  wss.handleUpgrade(req, socket, head, (ws) => {
    wss.emit('connection', ws, req, url)
  })
})

wss.on('connection', (ws, _req, url) => {
  const session = url.searchParams.get('session') || 'default'
  const role = url.searchParams.get('role') === 'phone' ? 'phone' : 'tv'
  ws.role = role
  ws.session = session

  const room = getPitchRoom(session)
  room.add(ws)

  safeSend(ws, {
    type: 'ready',
    session,
    role,
    peers: room.size,
    ts: Date.now(),
  })

  console.log(`[pitch-ws] + ${role} session=${session} peers=${room.size}`)

  ws.on('message', (buf) => {
    let msg
    try {
      msg = JSON.parse(String(buf))
    } catch {
      safeSend(ws, { type: 'error', error: 'invalid json' })
      return
    }

    if (msg?.type === 'ping') {
      safeSend(ws, { type: 'pong', ts: Date.now() })
      return
    }

    if (msg?.type === 'hello') {
      safeSend(ws, {
        type: 'ready',
        session,
        role,
        peers: room.size,
        ts: Date.now(),
      })
      return
    }

    // TV 发布节拍同步 → 转发给 phone
    if (msg?.type === 'beat') {
      if (role !== 'tv') {
        safeSend(ws, { type: 'error', error: 'only tv can publish beat' })
        return
      }
      const envelope = {
        type: 'beat',
        session,
        ts: Number(msg.ts) || Date.now(),
        bpm: Number(msg.bpm) || 0,
        beatIndex: Number(msg.beatIndex) || 0,
        beatsPerBar: Number(msg.beatsPerBar) || 4,
        suppressMs: Number(msg.suppressMs) || 120,
      }
      broadcastBeat(session, envelope, ws)
      return
    }

    // 仅 phone 发布音准；tv 只接收
    if (msg?.type === 'pitch') {
      if (role !== 'phone') {
        safeSend(ws, { type: 'error', error: 'tv cannot publish pitch' })
        return
      }
      const envelope = {
        type: 'pitch',
        session,
        seq: Number(msg.seq) || 0,
        ts: Number(msg.ts) || Date.now(),
        a4: Number(msg.a4) || 440,
        result: msg.result || null,
      }
      const n = broadcastPitch(session, envelope, ws)
      // 可选 ack，便于手机侧统计
      if (msg.ack) {
        safeSend(ws, { type: 'ack', seq: envelope.seq, delivered: n, ts: Date.now() })
      }
      return
    }

    safeSend(ws, { type: 'error', error: `unknown type: ${msg?.type}` })
  })

  ws.on('close', () => {
    room.delete(ws)
    if (room.size === 0) pitchRooms.delete(session)
    console.log(`[pitch-ws] - ${role} session=${session} peers=${room.size}`)
  })

  ws.on('error', () => {
    room.delete(ws)
  })
})

server.listen(port, '0.0.0.0', () => {
  const lanIp = primaryLanIp()
  console.log(`[control-server] http://0.0.0.0:${port}`)
  console.log(`[control-server] lan ip     ${lanIp || '(未检测到)'}`)
  console.log(`[control-server] pitch ws   ws://${lanIp || '0.0.0.0'}:${port}/ws/pitch?session=xxx&role=phone|tv`)
})
