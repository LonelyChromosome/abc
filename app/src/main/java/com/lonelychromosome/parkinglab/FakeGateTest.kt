package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A test console, not a Phenikaa Uni impersonation.
 * Creates a REAL Android notification from Parking Lab, then sends matching
 * synthetic content through the same parser/FGS action as a real notification.
 */
object FakeGateTest {
    enum class Kind { ENTRY, EXIT, UNRELATED }

    private const val CHANNEL = "parkinglab_fake_gate"
    private fun prefs(c: Context) = c.getSharedPreferences("fake_gate_panel", Context.MODE_PRIVATE)

    fun status(c: Context): String =
        prefs(c).getString("status",
            "Chưa chạy thử. Hãy bật Find My Bike, sau đó gửi FAKE XE VÀO.") ?: ""

    private fun report(c: Context, message: String) {
        val whenText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        prefs(c).edit().putString("status", whenText + " · " + message).apply()
    }

    fun noteNoPush(c: Context) {
        report(c, "Không phát thông báo. Bộ chờ tự chuyển sang thủ công sau 10 giây.")
    }

    fun noteScheduled(c: Context) {
        report(c, "Đã đặt lịch FAKE xe vào sau 12 giây. Nếu hết hạn chờ, GPS không được bật.")
    }

    fun emit(c: Context, kind: Kind): String {
        val title: String
        val body: String
        when (kind) {
            Kind.ENTRY -> {
                title = "FAKE · Phenikaa Uni / XE VÀO"
                body = "Xe đã vào cổng trường · thử nghiệm Parking Lab"
            }
            Kind.EXIT -> {
                title = "FAKE · Phenikaa Uni / XE RA"
                body = "Xe đã ra cổng trường · thử nghiệm Parking Lab"
            }
            Kind.UNRELATED -> {
                title = "FAKE · Phenikaa Uni / TIN KHÁC"
                body = "Thông báo lịch học · không liên quan đến xe"
            }
        }

        val manager = c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "FAKE thông báo cổng trường", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true).build()
        val postingGranted = Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (postingGranted) {
            manager.notify(4200 + kind.ordinal,
                notification)
        }

        val matched = GateNotificationMatcher.isGateEntry(
            GateNotificationMatcher.PHENIKAA_PACKAGE, notification.extras)
        val modeBefore = FindMyBikeStore.mode(c)
        val withinWindow = SystemClock.elapsedRealtime() -
            FindMyBikeStore.waitingSince(c) <= 10_000L
        val accepted = matched && modeBefore == FindMyBikeStore.WAITING && withinWindow
        if (accepted) {
            // We do not spoof Android's source package. Test input enters at the
            // parser result boundary, with identical service action to a real listener.
            c.startService(Intent(c, BikeTrackingService::class.java)
                .setAction(BikeTrackingService.GATE))
        }
        val result = when {
            accepted -> "KHỚP xe vào → yêu cầu bật tracking GPS"
            !matched -> "KHÔNG KHỚP xe vào → bỏ qua"
            modeBefore == FindMyBikeStore.WAITING -> "ĐẾN TRỄ (quá 10 giây) → bỏ qua"
            else -> "ĐÃ NHẬN nhưng trạng thái $modeBefore → không ghi đè"
        }
        report(c, result + if (postingGranted) " · đã đăng thông báo FAKE"
            else " · thông báo hệ thống bị tắt, chỉ chạy test nội bộ")
        return result
    }
}
