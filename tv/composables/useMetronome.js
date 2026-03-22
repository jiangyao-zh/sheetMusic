import { computed, onUnmounted, ref } from "vue";

export function useMetronome() {
  const bpm = ref(80);
  const beatsPerBar = ref(4);
  const enabled = ref(false);
  const currentBeat = ref(0);

  let ctx = null;
  let timer = null;
  let nextNoteTime = 0;
  let beatIndex = 0;

  const secondsPerBeat = computed(() => 60 / bpm.value);

  function ensureAudioContext() {
    if (!ctx) {
      const g = typeof window !== "undefined" ? window : globalThis;
      const AudioContextClass = g.AudioContext || g.webkitAudioContext;
      if (!AudioContextClass) {
        throw new Error("当前环境不支持 AudioContext");
      }
      ctx = new AudioContextClass();
    }
    if (ctx.state === "suspended") {
      ctx.resume();
    }
  }

  function playClick(time, high) {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "triangle";
    osc.frequency.setValueAtTime(high ? 1600 : 1000, time);
    gain.gain.setValueAtTime(0.0001, time);
    gain.gain.exponentialRampToValueAtTime(0.2, time + 0.002);
    gain.gain.exponentialRampToValueAtTime(0.0001, time + 0.08);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(time);
    osc.stop(time + 0.09);
  }

  function scheduleBeat() {
    const lookAhead = 0.1;
    while (nextNoteTime < ctx.currentTime + lookAhead) {
      const isFirst = beatIndex % beatsPerBar.value === 0;
      playClick(nextNoteTime, isFirst);
      currentBeat.value = (beatIndex % beatsPerBar.value) + 1;
      nextNoteTime += secondsPerBeat.value;
      beatIndex += 1;
    }
  }

  function start() {
    if (enabled.value) return;
    ensureAudioContext();
    enabled.value = true;
    beatIndex = 0;
    currentBeat.value = 1;
    nextNoteTime = ctx.currentTime + 0.02;
    timer = setInterval(scheduleBeat, 25);
  }

  function stop() {
    enabled.value = false;
    currentBeat.value = 0;
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  function toggle() {
    if (enabled.value) stop();
    else start();
  }

  function setBpm(value) {
    const next = Math.max(40, Math.min(240, Number(value) || 80));
    bpm.value = next;
  }

  function adjustBpm(delta) {
    setBpm(bpm.value + delta);
  }

  function setBeatsPerBar(value) {
    const next = Math.max(2, Math.min(12, Number(value) || 4));
    beatsPerBar.value = next;
  }

  onUnmounted(() => {
    stop();
    if (ctx) {
      ctx.close();
      ctx = null;
    }
  });

  return {
    bpm,
    beatsPerBar,
    enabled,
    currentBeat,
    start,
    stop,
    toggle,
    setBpm,
    adjustBpm,
    setBeatsPerBar
  };
}
