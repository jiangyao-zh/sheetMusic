package com.sheetmusic.pitch.algorithm

/**
 * TV 节拍同步 + 本地瞬态门控：在节拍点击窗口内暂停音高分析/发布。
 */
class MetronomeGate(
    private val defaultSuppressMs: Long = 120L,
) {
    @Volatile
    private var suppressUntilMs: Long = 0L

    fun notifyBeat(tsMs: Long = System.currentTimeMillis(), suppressMs: Long = defaultSuppressMs) {
        suppressUntilMs = maxOf(suppressUntilMs, tsMs + suppressMs)
    }

    fun isSuppressed(nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs < suppressUntilMs
    }

    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long {
        return (suppressUntilMs - nowMs).coerceAtLeast(0L)
    }

    fun reset() {
        suppressUntilMs = 0L
    }
}
