package com.sheetmusic.pitch.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioRecord 实时采集：44100Hz / PCM16 / MONO / 2048 samples。
 * 独立线程读取，主线程 Handler 节流回调（默认 ~15Hz）。
 */
class AudioRecorder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val callbackIntervalMs: Long = DEFAULT_CALLBACK_INTERVAL_MS,
) {
    fun interface FrameListener {
        fun onFrame(pcmBytes: ByteArray, sampleCount: Int)
    }

    private val running = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var listener: FrameListener? = null

    @Volatile
    private var lastCallbackAt = 0L

    fun start(listener: FrameListener) {
        if (!running.compareAndSet(false, true)) return
        this.listener = listener
        lastCallbackAt = 0L

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bytesPerFrame = bufferSize * 2
        val recordBuf = maxOf(minBuf, bytesPerFrame * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBuf,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            running.set(false)
            recorder.release()
            throw IllegalStateException("AudioRecord 初始化失败，请检查麦克风权限")
        }
        audioRecord = recorder
        recorder.startRecording()

        recordThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val readBuf = ByteArray(bytesPerFrame)
            while (running.get()) {
                val read = recorder.read(readBuf, 0, readBuf.size)
                if (read <= 0) continue
                val now = System.currentTimeMillis()
                if (now - lastCallbackAt < callbackIntervalMs) continue
                lastCallbackAt = now
                val copy = readBuf.copyOf(read)
                val samples = read / 2
                mainHandler.post {
                    if (running.get()) {
                        this.listener?.onFrame(copy, samples)
                    }
                }
            }
        }, "pitch-audio-record").also { it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        listener = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        recordThread?.interrupt()
        recordThread = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
        /** 4096 @ 44100Hz ≈ 93ms，兼顾小提琴高音区 YIN 精度与 <100ms 延迟 */
        const val DEFAULT_BUFFER_SIZE = 4096
        const val DEFAULT_CALLBACK_INTERVAL_MS = 66L
    }
}
