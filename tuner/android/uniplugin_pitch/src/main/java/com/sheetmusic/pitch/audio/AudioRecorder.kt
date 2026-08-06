package com.sheetmusic.pitch.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioRecord 实时采集：44100Hz / PCM16 / MONO。
 * 会依次尝试多种 AudioSource，提高模拟器/真机兼容性。
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

    /** 实际使用的 AudioSource，便于调试页展示 */
    @Volatile
    var activeSourceName: String = "unknown"
        private set

    fun start(listener: FrameListener) {
        if (!running.compareAndSet(false, true)) return
        this.listener = listener
        lastCallbackAt = 0L

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            running.set(false)
            throw IllegalStateException("当前设备不支持 44100Hz / MONO / PCM16")
        }

        val bytesPerFrame = bufferSize * 2
        val recordBuf = maxOf(minBuf, bytesPerFrame * 2)
        val recorder = createRecorder(recordBuf)
            ?: run {
                running.set(false)
                throw IllegalStateException("AudioRecord 初始化失败，请检查麦克风权限")
            }

        audioRecord = recorder
        try {
            recorder.startRecording()
        } catch (e: Exception) {
            running.set(false)
            recorder.release()
            audioRecord = null
            throw IllegalStateException("startRecording 失败: ${e.message}", e)
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            running.set(false)
            recorder.release()
            audioRecord = null
            throw IllegalStateException("录音未进入 RECORDING 状态（模拟器常见）")
        }

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

    private fun createRecorder(recordBuf: Int): AudioRecord? {
        val sources = buildList {
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
            add(MediaRecorder.AudioSource.MIC to "MIC")
            add(MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER")
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION")
            if (Build.VERSION.SDK_INT >= 24) {
                add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
            }
        }
        for ((source, name) in sources) {
            try {
                val rec = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBuf,
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    activeSourceName = name
                    return rec
                }
                rec.release()
            } catch (_: Exception) {
                // try next
            }
        }
        return null
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
        const val DEFAULT_BUFFER_SIZE = 4096
        const val DEFAULT_CALLBACK_INTERVAL_MS = 66L
    }
}
