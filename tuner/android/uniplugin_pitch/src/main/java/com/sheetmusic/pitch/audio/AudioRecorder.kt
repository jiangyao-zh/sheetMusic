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
 * 会依次尝试多种 AudioSource；真机上 UNPROCESSED 常能初始化但读全 0，故 MIC 优先，
 * 并在连续全零帧时自动切换下一音源。
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

    private var recordBufferBytes = 0
    private var sourceStartIndex = 0

    fun start(listener: FrameListener) {
        if (!running.compareAndSet(false, true)) return
        this.listener = listener
        lastCallbackAt = 0L
        sourceStartIndex = 0

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
        recordBufferBytes = maxOf(minBuf, bytesPerFrame * 2)
        val recorder = openRecorderAt(sourceStartIndex)
            ?: run {
                running.set(false)
                throw IllegalStateException("AudioRecord 初始化失败，请检查麦克风权限")
            }

        audioRecord = recorder
        startRecordThread(bytesPerFrame)
    }

    private fun audioSources(): List<Pair<Int, String>> = buildList {
        // 真机优先 MIC：UNPROCESSED 常初始化成功但 PCM 全 0
        add(MediaRecorder.AudioSource.MIC to "MIC")
        add(MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER")
        if (Build.VERSION.SDK_INT >= 24) {
            add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
        }
        add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
        add(MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION")
    }

    private fun openRecorderAt(fromIndex: Int): AudioRecord? {
        val sources = audioSources()
        for (index in fromIndex until sources.size) {
            val (source, name) = sources[index]
            try {
                val rec = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferBytes,
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release()
                    continue
                }
                rec.startRecording()
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    rec.release()
                    continue
                }
                sourceStartIndex = index
                activeSourceName = name
                return rec
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }

    /** 连续全零 PCM（broken UNPROCESSED 等）时切换到下一音源 */
    private fun maybeSwitchSourceOnSilence(consecutiveAllZero: Int): AudioRecord? {
        if (consecutiveAllZero < ALL_ZERO_SWITCH_THRESHOLD) return null
        val nextIndex = sourceStartIndex + 1
        if (nextIndex >= audioSources().size) return null

        val old = audioRecord
        try {
            old?.stop()
        } catch (_: Exception) {
        }
        try {
            old?.release()
        } catch (_: Exception) {
        }

        val next = openRecorderAt(nextIndex) ?: return null
        audioRecord = next
        return next
    }

    private fun startRecordThread(bytesPerFrame: Int) {
        recordThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val readBuf = ByteArray(bytesPerFrame)
            var recorder = audioRecord ?: return@Thread
            var consecutiveAllZero = 0

            while (running.get()) {
                val read = recorder.read(readBuf, 0, readBuf.size)
                if (read <= 0) continue

                if (isAllZeroPcm(readBuf, read)) {
                    consecutiveAllZero++
                    val switched = maybeSwitchSourceOnSilence(consecutiveAllZero)
                    if (switched != null) {
                        recorder = switched
                        consecutiveAllZero = 0
                    }
                } else {
                    consecutiveAllZero = 0
                }

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

    /** 每个 sample 精确为 0 —— 区别于安静环境的底噪 */
    private fun isAllZeroPcm(buf: ByteArray, read: Int): Boolean {
        for (i in 0 until read) {
            if (buf[i] != 0.toByte()) return false
        }
        return read > 0
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
        /** 约 2s 连续全零帧后尝试下一 AudioSource */
        private const val ALL_ZERO_SWITCH_THRESHOLD = 30
    }
}
