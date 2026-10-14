package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single-shot, user-defined activation of Find My Bike.
 * No location readings occur until a valid gate notification or a manual start.
 */
object ScheduledGateController {
    const val ACTION_ARM = "com.lonelychromosome.parkinglab.scheduled.ARM"
    const val ACTION_EXPIRE = "com.lonelychromosome.parkinglab.scheduled.EXPIRE"
    private const val EXTRA_AT = "scheduled_at"
    private const val PREF = "find_my_bike_schedule_v04"
    private const val CHANNEL = "find_my_bike_schedule_alerts"
    private const val REQUEST_ARM = 7711
    private const val REQUEST_EXPIRE = 7712
    private const val REQUEST_OPEN = 7713
    private const val NOTIFICATION_ID = 7715

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun time(c: Context) = prefs(c).getLong("at", 0L)
    fun waitSeconds(c: Context) = prefs(c).getInt("wait_seconds", 600)
    fun lastEvent(c: Context) = prefs(c).getString("last", "Chưa đặt lịch") ?: "Chưa đặt lịch"
    fun isPending(c: Context) = time(c) > System.currentTimeMillis()
    fun isExpired(c: Context) = time(c) != 0L && time(c) <= System.currentTimeMillis()

    fun format(at: Long): String =
        SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(at))

    fun setEvent(c: Context, event: String) {
        prefs(c).edit().putString("last", event).apply()
    }

    fun hasExactAlarm(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun hasBackgroundLocation(c: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasForegroundLocation(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pending(c: Context, action: String, at: Long, request: Int): PendingIntent {
        val intent = Intent(c, ScheduledGateReceiver::class.java).setAction(action)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(c, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(c: Context, at: Long, waitSeconds: Int): Boolean {
        if (at <= System.currentTimeMillis() || waitSeconds !in 10..14400 || !hasExactAlarm(c)) return false
        return try {
            cancel(c)
            val alarm = c.getSystemService(AlarmManager::class.java)
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                pending(c, ACTION_ARM, at, REQUEST_ARM))
            val saved = prefs(c).edit().putLong("at", at).putInt("wait_seconds", waitSeconds)
                .putString("last", "Đã hẹn giờ: " + format(at)).commit()
            if (saved) BikeProgressNotification.scheduled(c, at, waitSeconds)
            saved
        } catch (_: SecurityException) {
            setEvent(c, "Không có quyền báo thức chính xác")
            false
        }
    }

    fun cancel(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(
            pending(c, ACTION_ARM, time(c), REQUEST_ARM))
        prefs(c).edit().remove("at").putString("last", "Đã hủy lịch chờ").commit()
        BikeProgressNotification.clearScheduled(c)
    }

    fun markFired(c: Context, at: Long): Boolean {
        if (time(c) != at || at <= 0L) return false
        prefs(c).edit().remove("at").putString("last",
            "Đến giờ hẹn · bắt đầu chờ thông báo xe vào").commit()
        BikeProgressNotification.event(c, "2/4 · ĐÃ ĐẾN GIỜ HẸN",
            "Đang kích hoạt bộ chờ quẹt thẻ",
            "Đã đánh thức bộ hẹn giờ · GPS chưa bật. Chờ dịch vụ xác nhận.", true)
        return true
    }

    fun scheduleExpire(c: Context, deadlineMs: Long) {
        if (!hasExactAlarm(c)) return
        try {
            c.getSystemService(AlarmManager::class.java)
                .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMs,
                    pending(c, ACTION_EXPIRE, deadlineMs, REQUEST_EXPIRE))
        } catch (_: SecurityException) { /* Handler in running service also enforces timeout */ }
    }

    fun cancelExpire(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(
            pending(c, ACTION_EXPIRE, 0L, REQUEST_EXPIRE))
    }

    fun notifyAction(c: Context, title: String, description: String) {
        val mgr = c.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(
            CHANNEL, "Find My Bike · lịch chờ", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val intent = PendingIntent.getActivity(c, REQUEST_OPEN, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mgr.notify(NOTIFICATION_ID, Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title).setContentText(description)
            .setAutoCancel(true).setContentIntent(intent).build())
    }

    /** Re-arm after a reboot, if the one-shot schedule is still in the future. */
    fun restoreIfPending(c: Context) {
        val at = time(c)
        if (at <= System.currentTimeMillis()) {
            if (at > 0L) {
                prefs(c).edit().remove("at").putString("last",
                    "Giờ hẹn đã qua khi máy tắt · cần đặt lại").commit()
            }
            return
        }
        if (!hasExactAlarm(c)) {
            setEvent(c, "Quyền báo thức chính xác bị thu hồi · cần cấp lại")
            return
        }
        try {
            c.getSystemService(AlarmManager::class.java)
                .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                    pending(c, ACTION_ARM, at, REQUEST_ARM))
            setEvent(c, "Đã khôi phục lịch: " + format(at))
            BikeProgressNotification.scheduled(c, at, waitSeconds(c))
        } catch (_: SecurityException) {
            setEvent(c, "Không thể khôi phục lịch")
        }
    }

    fun matchesPending(c: Context, at: Long) = time(c) == at && at > 0L
    fun deadlineIntentMatches(c: Context, at: Long): Boolean {
        return FindMyBikeStore.mode(c) == FindMyBikeStore.WAITING &&
            FindMyBikeStore.waitingDeadline(c) == at && System.currentTimeMillis() >= at
    }

    fun fireWaiting(c: Context, at: Long) {
        if (!markFired(c, at)) return
        if (FindMyBikeStore.mode(c) == FindMyBikeStore.TRACKING ||
            FindMyBikeStore.mode(c) == FindMyBikeStore.WAITING) {
            setEvent(c, "Đã bỏ lịch vì Find My Bike đang hoạt động")
            return
        }
        val waitMillis = waitSeconds(c) * 1000L
        FindMyBikeStore.startWaiting(c, waitMillis)
        scheduleExpire(c, FindMyBikeStore.waitingDeadline(c))
        if (!hasBackgroundLocation(c) || !hasForegroundLocation(c)) {
            setEvent(c, "Thiếu quyền vị trí nền; đang chờ push, nhưng cần mở app để bật GPS")
            BikeProgressNotification.event(c, "CẦN THAO TÁC · CHƯA BẬT GPS",
                "Giờ hẹn đã tới nhưng thiếu quyền vị trí nền",
                "Dịch vụ GPS chưa xác nhận hoạt động. Mở app để cấp quyền.")
            notifyAction(c, "Lịch Find My Bike đã đến", "Cần cấp quyền vị trí mọi lúc để tự bật GPS.")
            return
        }
        try {
            c.startForegroundService(Intent(c, BikeTrackingService::class.java)
                .setAction(BikeTrackingService.START_SCHEDULED))
            setEvent(c, "Đã đến giờ; kích hoạt service chờ thông báo xe vào")
        } catch (e: Exception) {
            setEvent(c, "Android chặn service nền: " + e.javaClass.simpleName)
            BikeProgressNotification.event(c, "LỖI · DỊCH VỤ NỀN BỊ CHẶN",
                "Android không cho kích hoạt bộ chờ tự động",
                "Lỗi: " + e.javaClass.simpleName + ". Mở BPA để bật thủ công.")
            notifyAction(c, "Find My Bike cần thao tác", "Chạm mở app để tiếp tục chờ quẹt thẻ.")
        }
    }

    fun expireWaiting(c: Context, at: Long) {
        if (!deadlineIntentMatches(c, at)) return
        FindMyBikeStore.needManual(c)
        setEvent(c, "Hết thời gian chờ · không có push xe vào hợp lệ")
        BikeProgressNotification.event(c, "HẾT GIỜ · KHÔNG NHẬN XE VÀO",
            "Không phát hiện thông báo quẹt thẻ hợp lệ",
            "GPS chưa được bật. Có thể mở app để bắt đầu thủ công.")
        notifyAction(c, "Chưa nhận thông báo xe vào", "Mở app nếu muốn bật tracking thủ công.")
        c.stopService(Intent(c, BikeTrackingService::class.java))
    }
}

class ScheduledGateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ScheduledGateController.ACTION_ARM -> {
                val at = intent.getLongExtra("scheduled_at", 0L)
                ScheduledGateController.fireWaiting(context, at)
            }
            ScheduledGateController.ACTION_EXPIRE -> {
                val deadline = intent.getLongExtra("scheduled_at", 0L)
                ScheduledGateController.expireWaiting(context, deadline)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                ScheduledGateController.restoreIfPending(context)
        }
    }
}
