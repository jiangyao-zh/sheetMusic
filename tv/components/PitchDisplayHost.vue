<template>
  <PitchDisplay
    class="pitch-display-host"
    :result="pitchResult"
    :socket-status="socketStatus"
    :last-msg-at="lastMsgAt"
    :now="now"
  />
</template>

<script>
import PitchDisplay from '@/components/PitchDisplay.vue'
import { pitchSocket } from '@/src/services/pitchSocket'
import { ensurePitchSession } from '@/src/utils/pitchSession'

/**
 * 音准订阅与渲染隔离层：状态变化只重绘本组件，不触发整页（乐谱大图）重绘。
 */
export default {
  name: 'PitchDisplayHost',
  components: { PitchDisplay },
  data() {
    return {
      pitchResult: null,
      socketStatus: 'idle',
      lastMsgAt: 0,
      now: Date.now(),
      unsub: null,
      staleTimer: null,
      lastMetaKey: '',
    }
  },
  mounted() {
    this.connect()
  },
  beforeDestroy() {
    this.disconnect()
  },
  methods: {
    connect() {
      if (this.unsub) return
      const session = ensurePitchSession()
      pitchSocket.configure({ session })
      this.emitMeta({ session, phoneHost: '', status: 'idle' })
      this.unsub = pitchSocket.onUpdate((snap) => {
        this.socketStatus = snap.status
        this.pitchResult = snap.latest
        this.lastMsgAt = snap.lastMsgAt || 0
        this.emitMeta({
          session: snap.session,
          phoneHost: snap.phoneHost || snap.lanIp || '',
          status: snap.status,
        })
      })
      pitchSocket.connect()
      this.startStaleClock()
    },
    disconnect() {
      this.stopStaleClock()
      if (this.unsub) {
        this.unsub()
        this.unsub = null
      }
      pitchSocket.disconnect()
    },
    emitMeta({ session, phoneHost, status }) {
      const key = `${session}|${phoneHost}|${status}`
      if (key === this.lastMetaKey) return
      this.lastMetaKey = key
      this.$emit('meta', { session, phoneHost, status })
    },
    startStaleClock() {
      this.stopStaleClock()
      this.now = Date.now()
      this.staleTimer = setInterval(() => {
        this.now = Date.now()
      }, 400)
    },
    stopStaleClock() {
      if (this.staleTimer) {
        clearInterval(this.staleTimer)
        this.staleTimer = null
      }
    },
  },
}
</script>

<style scoped>
.pitch-display-host {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
