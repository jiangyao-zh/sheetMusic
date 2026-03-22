#!/usr/bin/env node
import http from 'node:http';
import { URL } from 'node:url';

const port = Number(process.env.CONTROL_PORT || 9091);
const clientsBySession = new Map();

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function getSet(session) {
  if (!clientsBySession.has(session)) clientsBySession.set(session, new Set());
  return clientsBySession.get(session);
}

function sendEvent(res, event, data) {
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

const server = http.createServer((req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  const url = new URL(req.url, `http://${req.headers.host}`);

  if (req.method === 'GET' && url.pathname === '/events') {
    const session = url.searchParams.get('session') || 'default';
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive'
    });
    const set = getSet(session);
    set.add(res);
    sendEvent(res, 'ready', { session });

    const heartbeat = setInterval(() => sendEvent(res, 'ping', { t: Date.now() }), 15000);

    req.on('close', () => {
      clearInterval(heartbeat);
      set.delete(res);
    });
    return;
  }

  if (req.method === 'POST' && url.pathname === '/action') {
    const session = url.searchParams.get('session') || 'default';
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 10 * 1024 * 1024) req.destroy();
    });
    req.on('end', () => {
      let payload = {};
      try {
        payload = raw ? JSON.parse(raw) : {};
      } catch {
        res.statusCode = 400;
        res.end(JSON.stringify({ ok: false, error: 'invalid json' }));
        return;
      }
      const set = getSet(session);
      for (const client of set) {
        sendEvent(client, 'action', payload);
      }
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ ok: true, session, listeners: set.size }));
    });
    return;
  }

  res.statusCode = 404;
  res.end('Not Found');
});

server.listen(port, () => {
  console.log(`[control-server] listening on http://0.0.0.0:${port}`);
});
