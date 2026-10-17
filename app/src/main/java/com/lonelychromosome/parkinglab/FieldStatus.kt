package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Truthful state updates. Waiting is an armed geofence, NOT continuous GPS sampling. */
object FieldStatus {
    private const val CHANNEL = "findbike_field_state_1"
    private const val NOTIFICATION_ID = 7113
    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)
    fun publish(c: Context, name: String, detail: String, persistent: Boolean = false) {
        val nm = manager(c)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "Find My Bike · trạng thái thực địa", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, 71, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Find My Bike · " + name)
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(pi)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(persistent)
            .setOngoing(persistent)
            .build()
        nm.notify(NOTIFICATION_ID, notice)
    }
    fun scheduled(c: Context, description: String) =
        publish(c, "HẸN GIỜ", description)
    fun waiting(c: Context) =
        publish(c, "CHỜ 100 M", "Đã đăng ký 2 vùng 100 m · chờ Google báo vào vùng. Chưa bật GPS liên tục.", true)
    fun tracking(c: Context) =
        publish(c, "TRACKING", "Đã vào vùng · ghi GPS thật và phát hiện dừng xe.", true)
    fun stopped(c: Context, steps: Int, seconds: Long) =
        publish(c, "NGHI DỪNG XE", "Chưa xác nhận đỗ. $steps bước / $seconds giây; cần GPS xác nhận đã rời xe.", true)
    fun tracked(c: Context, accuracy: Float) =
        publish(c, "TRACKED", "Đã lưu tọa độ xe từ GPS/trace. Mở app → Dẫn đường (sai số GPS cuối $accuracy m).")
    fun navigating(c: Context, remain: Float, accuracy: Float) =
        publish(c, "DẪN ĐƯỜNG", "Còn khoảng ${remain.toInt()} m theo GPS · sai số vị trí ${accuracy.toInt()} m.", true)
    fun ended(c: Context) =
        publish(c, "END", "Đã kết thúc phiên dẫn đường. Điểm TRACKED vẫn được lưu để xem lại.")
    fun error(c: Context, message: String) =
        publish(c, "CẦN KIỂM TRA", message)
    fun disabled(c: Context) { manager(c).cancel(NOTIFICATION_ID) }
}
