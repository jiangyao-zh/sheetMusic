package com.sheetmusic.pitch.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 检测运行中的前台服务：降低系统在息屏/后台杀掉录音与网络的概率。
 */
class PitchForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = if (launch != null) {
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音准检测运行中")
            .setContentText("正在收音并同步到 TV，息屏也会继续")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音准检测",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "检测与投屏保活通知"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "pitch_keepalive"
        private const val NOTIFICATION_ID = 9101
        const val ACTION_STOP = "com.sheetmusic.pitch.keepalive.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PitchForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PitchForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // ignore
            }
            try {
                context.stopService(Intent(context, PitchForegroundService::class.java))
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}
