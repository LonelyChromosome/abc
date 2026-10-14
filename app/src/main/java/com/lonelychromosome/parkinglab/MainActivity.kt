package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.provider.Settings
import android.widget.ScrollView
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val timer = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var clock: TextView
    private lateinit var info: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var listenerInfo: TextView
    private lateinit var eventsInfo: TextView
    private lateinit var fakeButton: Button
    private var fakeSequence = 0
    private val refreshLoop = object : Runnable {
        override fun run() {
            refresh()
            timer.postDelayed(this, 1000)
        }
    }
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun margin(top: Int, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(top), 0, dp(bottom))
        }
    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mint = Color.rgb(60, 229, 176)
        val white = Color.rgb(238, 246, 255)
        val muted = Color.rgb(150, 166, 184)
        window.statusBarColor = Color.rgb(8, 13, 22)
        window.navigationBarColor = Color.rgb(8, 13, 22)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(38), dp(24), dp(24))
            setBackgroundColor(Color.rgb(8, 13, 22))
        }
        root.addView(text("PARKING LAB", 14f, mint, true))
        root.addView(text("Notification Test v0.2", 26f, white, true), margin(14))
        root.addView(text("Giữ bài test chạy nền v0.1. Cấp quyền đọc thông báo rồi gửi thông báo thử, kể cả khi khóa màn hình.", 14f, muted), margin(12))
        status = text("CHƯA KÍCH HOẠT", 14f, mint, true)
        root.addView(status, margin(32))
        clock = text("00:00:00", 52f, white, true).apply { gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(clock, margin(18))
        root.addView(text("THỜI GIAN TỪ LÚC KÍCH HOẠT", 11f, muted, true)
            .apply { gravity = Gravity.CENTER_HORIZONTAL }, margin(8, 12))
        start = Button(this).apply {
            this.text = "KÍCH HOẠT"
            setTextColor(Color.rgb(3, 25, 22))
            background = rounded(mint)
            setOnClickListener { requestStart() }
        }
        root.addView(start, margin(25))
        stop = Button(this).apply {
            this.text = "DỪNG / ĐẶT LẠI"
            setTextColor(white)
            background = rounded(Color.rgb(41, 54, 72))
            setOnClickListener {
                TimerStore.stop(this@MainActivity)
                startService(Intent(this@MainActivity, StandbyService::class.java)
                    .setAction(StandbyService.STOP))
                refresh()
            }
        }
        root.addView(stop, margin(10))
        info = text("", 13f, muted)
        root.addView(info, margin(24))
        root.addView(text("BỘ NHẬN THÔNG BÁO", 15f, mint, true), margin(26))
        root.addView(text(
            "Thông báo FAKE ở đây là thông báo Android thật từ Parking Lab. " +
            "Thông báo từ app khác sẽ được ghi nhận riêng.", 13f, muted), margin(8))
        val accessButton = Button(this).apply {
            text = "CẤP QUYỀN ĐỌC THÔNG BÁO"
            setTextColor(white)
            background = rounded(Color.rgb(41, 54, 72))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(accessButton, margin(16))
        listenerInfo = text("", 13f, muted)
        root.addView(listenerInfo, margin(10))
        fakeButton = Button(this).apply {
            text = "GỬI THÔNG BÁO FAKE"
            setTextColor(Color.rgb(3, 25, 22))
            background = rounded(mint)
            setOnClickListener { requestFakeNotification() }
        }
        root.addView(fakeButton, margin(18))
        root.addView(text("NHẬT KÝ NHẬN THÔNG BÁO", 14f, white, true), margin(26))
        eventsInfo = text("", 13f, muted)
        root.addView(eventsInfo, margin(8, 30))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)
        setContentView(scroll)
        refresh()
    }
    private fun listenerGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= 27) {
            return getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(this, ParkingNotificationListener::class.java))
        }
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == packageName
        }
    }
    private fun requestFakeNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 92)
        } else postFakeNotification()
    }
    private fun postFakeNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "parkinglab_test", "Parking Lab - fake notifications", NotificationManager.IMPORTANCE_DEFAULT))
        fakeSequence += 1
        val notice = Notification.Builder(this, "parkinglab_test")
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("FAKE: XE ĐÃ VÀO CỔNG")
            .setContentText("BPA Parking Lab thử nghiệm số $fakeSequence")
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .build()
        manager.notify(2001, notice)
    }
    private fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 91)
        } else runService()
    }
    override fun onRequestPermissionsResult(req: Int, permissions: Array<out String>, result: IntArray) {
        super.onRequestPermissionsResult(req, permissions, result)
        if (req == 91) runService()
        if (req == 92) {
            if (result.isNotEmpty() && result[0] == PackageManager.PERMISSION_GRANTED) {
                postFakeNotification()
            } else {
                listenerInfo.text = "Chưa cấp quyền đăng thông báo, không thể gửi fake."
            }
        }
    }
    private fun runService() {
        try {
            if (!TimerStore.active(this)) TimerStore.start(this)
            startForegroundService(Intent(this, StandbyService::class.java)
                .setAction(StandbyService.START))
            refresh()
        } catch (ex: Exception) {
            TimerStore.stop(this)
            status.text = "LỖI: ${ex.javaClass.simpleName}"
        }
    }
    private fun refresh() {
        if (!::status.isInitialized) return
        val active = TimerStore.active(this)
        val elapsed = TimerStore.elapsed(this)
        clock.text = TimerStore.time(elapsed)
        status.text = when {
            !active -> "CHƯA KÍCH HOẠT"
            elapsed < 0 -> "PHÁT HIỆN KHỞI ĐỘNG LẠI MÁY"
            else -> "STANDBY ĐÃ KÍCH HOẠT"
        }
        start.isEnabled = !active
        stop.isEnabled = active
        val age = TimerStore.beatAge(this)
        info.text = "Nhịp dịch vụ: ${TimerStore.beats(this)}\n" +
            "Lần cập nhật cuối: ${if (age < 0) "Chưa có" else "$age giây trước"}\n" +
            "Số lần service khởi động lại: ${TimerStore.restarts(this)}\n" +
            "Đã vuốt khỏi đa nhiệm: ${TimerStore.removedAt(this)}\n\n" +
            "Timer dùng thời gian monotonic; nhịp dịch vụ xác nhận hoạt động nền. " +
            "Android có thể trì hoãn nhịp khi ngủ sâu. Buộc dừng sẽ dừng service."
        if (::listenerInfo.isInitialized) {
            val granted = listenerGranted()
            val connection = NotificationStore.connected(this)
            listenerInfo.text = "Quyền Notification Access: " +
                (if (granted) "ĐÃ CẤP" else "CHƯA CẤP") +
                "\\nListener đang kết nối: " + (if (connection) "CÓ" else "CHƯA") +
                "\\nĐã nhận fake: " + NotificationStore.ownCount(this) +
                " | App khác: " + NotificationStore.externalCount(this)
        }
        if (::eventsInfo.isInitialized) {
            eventsInfo.text = NotificationStore.recentText(this)
        }
    }
    override fun onResume() {
        super.onResume()
        timer.removeCallbacks(refreshLoop)
        timer.post(refreshLoop)
    }
    override fun onPause() {
        timer.removeCallbacks(refreshLoop)
        super.onPause()
    }
}
