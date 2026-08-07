package com.sheetmusic.pitch.keepalive

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * 保活：
 * 1) FLAG_KEEP_SCREEN_ON —— App 前台时尽量不息屏
 * 2) PARTIAL_WAKE_LOCK —— 息屏后 CPU 仍可跑录音/推送
 * 3) 前台服务 —— 降低系统杀后台概率
 */
object KeepAliveController {
    private const val WAKE_TAG = "sheetmusic:pitch"

    @Volatile private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var holdCount = 0
    private var activityRef: WeakReference<Activity>? = null

    @Synchronized
    fun bindActivity(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
    }

    /** App 打开期间保持亮屏（不依赖是否在检测） */
    fun setScreenAlwaysOn(activity: Activity?, enabled: Boolean) {
        val act = activity ?: activityRef?.get() ?: return
        act.runOnUiThread {
            if (enabled) {
                act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /** 开始检测 / 投屏时调用：亮屏 + 唤醒锁 + 前台服务 */
    @Synchronized
    fun acquire(context: Context, keepScreenOn: Boolean = true) {
        holdCount += 1
        if (holdCount > 1) return

        val app = context.applicationContext
        if (keepScreenOn) {
            setScreenAlwaysOn(activityRef?.get() ?: (context as? Activity), true)
        }

        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 最长 6 小时，防止泄漏
        }
        wakeLock = lock

        try {
            PitchForegroundService.start(app)
        } catch (_: Exception) {
            // 通知权限等失败时仍保留 wake lock
        }
    }

    @Synchronized
    fun release(context: Context) {
        if (holdCount <= 0) return
        holdCount -= 1
        if (holdCount > 0) return

        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null

        try {
            PitchForegroundService.stop(context.applicationContext)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun forceRelease(context: Context) {
        holdCount = 0
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
        try {
            PitchForegroundService.stop(context.applicationContext)
        } catch (_: Exception) {
        }
    }
}
