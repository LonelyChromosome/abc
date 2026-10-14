package com.lonelychromosome.parkinglab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** User-initiated foreground stopwatch. No GPS, polling or wake lock. */
class StandbyService : Service() {
    companion object {
        const val START = "parkinglab.START"
        const val STOP = "parkinglab.STOP"
        private const val CHANNEL = "standby"
        private const val NOTIFICATION = 1001
    }
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!TimerStore.active(this@StandbyService)) return
            val current = TimerStore.elapsed(this@StandbyService)
            if (current < 0) {
                TimerStore.stop(this@StandbyService)
                stopSelf()
                return
            }
            TimerStore.beat(this@StandbyService)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION, notification(current))
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Parking Lab - standby", NotificationManager.IMPORTANCE_LOW)
        ch.description = "Bộ đếm khi tắt app và khóa màn hình"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            TimerStore.stop(this)
            handler.removeCallbacks(ticker)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == START && !TimerStore.active(this)) TimerStore.start(this)
        if (!TimerStore.active(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != START) TimerStore.restart(this)
        val current = TimerStore.elapsed(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification(current.coerceAtLeast(0)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION, notification(current.coerceAtLeast(0)))
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }
    private fun notification(ms: Long): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Parking Lab - bộ chờ đang chạy")
            .setContentText("${TimerStore.time(ms)} · nhịp ${TimerStore.beats(this)}")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pending)
            .setCategory(Notification.CATEGORY_SERVICE).build()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        TimerStore.taskRemoved(this)
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
