package com.lonelychromosome.parkinglab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single externally visible state for Find My Bike:
 * pending alarm -> listening -> tracking -> stored, or explicit problem/timeout.
 * Reuses the same notification ID as the location foreground service.
 */
object BikeProgressNotification {
    const val FGS_ID = 3001
    private const val SCHEDULE_ID = 7720
    private const val CHANNEL = "bike_progress_v041"

    fun ensureChannel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Find My Bike · tiến trình",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tình trạng đặt lịch, nhận quẹt thẻ, GPS và xác định xe"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }

    private fun nowTime() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    private fun hhmm(timestamp: Long): String =
        SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(timestamp))

    private fun openApp(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 7721, Intent(c, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun make(c: Context, title: String, line: String, more: String,
                     ongoing: Boolean): Notification {
        ensureChannel(c)
        val full = line + "\n" + more + "\nTrạng thái ghi nhận lúc " + nowTime()
        return Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(full))
            .setSubText("Cập nhật " + nowTime())
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(openApp(c))
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    fun foreground(c: Context, title: String, line: String, more: String): Notification =
        make(c, title, line, more, true)

    fun updateForeground(c: Context, title: String, line: String, more: String) {
        c.getSystemService(NotificationManager::class.java)
            .notify(FGS_ID, foreground(c, title, line, more))
    }

    fun scheduled(c: Context, at: Long, seconds: Int) {
        post(c, SCHEDULE_ID, "1/4 · ĐÃ HẸN CHỜ QUẸT THẺ",
            "Tới " + hhmm(at) + " sẽ bắt đầu chờ Phenikaa Uni",
            "Cửa sổ chờ: " + seconds + " giây. GPS đang TẮT. " +
                "Thông báo này chỉ xác nhận đã lưu lịch, chưa phải service đang chạy.", true)
    }

    fun clearScheduled(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(SCHEDULE_ID)
    }

    fun event(c: Context, title: String, line: String, more: String,
              ongoing: Boolean = false) {
        post(c, SCHEDULE_ID, title, line, more, ongoing)
    }

    private fun post(c: Context, id: Int, title: String, line: String,
                     more: String, ongoing: Boolean) {
        c.getSystemService(NotificationManager::class.java).notify(
            id, make(c, title, line, more, ongoing))
    }
}
