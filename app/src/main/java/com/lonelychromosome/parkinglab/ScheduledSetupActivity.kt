package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * One-time start-time and configurable notification wait; the legacy immediate
 * start/manual controls continue to work in MainActivity.
 */
class ScheduledSetupActivity : Activity() {
    private val pageBg = Color.rgb(8, 13, 22)
    private val panel = Color.rgb(23, 34, 49)
    private val accent = Color.rgb(60, 229, 176)
    private val labelColor = Color.rgb(239, 246, 255)
    private val muted = Color.rgb(161, 177, 192)
    private val clock = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
    private var targetMillis: Long = clock.timeInMillis
    private lateinit var scheduleText: TextView
    private lateinit var permText: TextView
    private lateinit var chosenText: TextView
    private lateinit var messageText: TextView
    private lateinit var waitSeconds: EditText

    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
    }
    private fun text(value: String, size: Float, color: Int = labelColor,
                     bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun add(parent: LinearLayout, child: android.view.View, top: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) })
    }
    private fun button(name: String, main: Boolean = false, callback: () -> Unit) =
        Button(this).apply {
            text = name
            isAllCaps = false
            textSize = 14f
            background = rounded(if (main) accent else Color.rgb(45, 61, 79))
            setTextColor(if (main) pageBg else labelColor)
            setOnClickListener { callback() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
            setBackgroundColor(pageBg)
        }
        add(root, button("‹ Trở về Find My Bike") { finish() })
        add(root, text("BỘ CHỜ TỰ ĐỘNG", 12f, accent, true), 23)
        add(root, text("Hẹn giờ nhận thông báo xe vào", 25f, labelColor, true), 8)
        add(root, text("Tới giờ hẹn, BPA chờ thông báo quẹt thẻ của Phenikaa Uni. " +
            "Chỉ khi khớp xe vào mới bật GPS. Nút thủ công ở màn chính vẫn hoạt động.", 13f, muted), 9)

        val settings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = rounded(panel)
        }
        add(settings, text("GIỜ BẮT ĐẦU CHỜ", 12f, accent, true))
        chosenText = text("", 20f, labelColor, true)
        add(settings, chosenText, 9)
        add(settings, button("Chọn ngày & giờ") { chooseTime() }, 10)
        add(settings, text("CHỜ THÔNG BÁO TRONG BAO LÂU?", 12f, accent, true), 19)
        add(settings, text("Nhập số giây (10–14400). Ví dụ: 600 = 10 phút.", 12f, muted), 5)
        waitSeconds = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(ScheduledGateController.waitSeconds(this@ScheduledSetupActivity).toString())
            textSize = 18f
            setTextColor(labelColor)
            setHintTextColor(muted)
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        add(settings, waitSeconds, 5)
        add(root, settings, 17)

        add(root, button("Đặt lịch chờ tự động", true) { schedule() }, 13)
        add(root, button("Hủy lịch đã đặt") {
            ScheduledGateController.cancel(this)
            updateState()
        }, 7)

        add(root, text("QUYỀN CẦN CẤP", 12f, accent, true), 26)
        permText = text("", 13f, muted)
        add(root, permText, 7)
        add(root, button("Cấp quyền vị trí & hoạt động") { askRuntimePermissions() }, 10)
        add(root, button("Cấp vị trí mọi lúc · trong Cài đặt ứng dụng") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName)))
        }, 6)
        add(root, button("Cấp quyền báo thức chính xác") { openExactAlarmSettings() }, 6)
        add(root, button("Cấp quyền đọc thông báo") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, 6)

        add(root, text("TRẠNG THÁI LỊCH HẸN", 12f, accent, true), 25)
        scheduleText = text("", 15f, labelColor, true)
        add(root, scheduleText, 8)
        messageText = text("", 12f, muted)
        add(root, messageText, 6)
        add(root, button("Làm mới trạng thái") { updateState() }, 9)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        updateState()
    }

    private fun chooseTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = targetMillis }
        val date = DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                cal.set(year, month, day, hour, minute, 0)
                cal.set(Calendar.MILLISECOND, 0)
                targetMillis = cal.timeInMillis
                updateState()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        date.datePicker.minDate = System.currentTimeMillis() - 86_400_000L
        date.show()
    }

    private fun readWait(): Int? = waitSeconds.text.toString().trim().toIntOrNull()
        ?.takeIf { it in 10..14400 }

    private fun hasListenerAccess(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        return if (Build.VERSION.SDK_INT >= 27) manager.isNotificationListenerAccessGranted(
            ComponentName(this, ParkingNotificationListener::class.java))
        else (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "")
            .contains(packageName)
    }

    private fun hasNotices(): Boolean = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasMotion(): Boolean = Build.VERSION.SDK_INT < 29 ||
        checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun schedule() {
        val seconds = readWait()
        val problem = when {
            targetMillis <= System.currentTimeMillis() -> "Hãy chọn thời điểm trong tương lai."
            seconds == null -> "Thời gian chờ phải từ 10 đến 14400 giây."
            !ScheduledGateController.hasForegroundLocation(this) -> "Cần quyền vị trí trước khi hẹn giờ."
            !ScheduledGateController.hasBackgroundLocation(this) -> "Cần quyền vị trí MỌI LÚC để tự bật GPS khi khóa màn hình."
            !ScheduledGateController.hasExactAlarm(this) -> "Cần quyền BÁO THỨC CHÍNH XÁC để đúng giờ."
            !hasListenerAccess() -> "Chưa cấp quyền đọc thông báo Phenikaa Uni."
            !hasNotices() -> "Cần cho phép thông báo để nhận trạng thái và cảnh báo."
            else -> null
        }
        if (problem != null) {
            messageText.text = problem
            updateState(false)
            return
        }
        val accepted = ScheduledGateController.schedule(this, targetMillis, seconds!!)
        updateState()
        messageText.text = if (accepted)
            "Đã lên lịch. M có thể khóa màn hình; BPA sẽ chờ thông báo khi đến giờ."
        else "Không thể đặt báo thức. Hãy kiểm tra quyền và thử lại."
    }

    private fun askRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (!ScheduledGateController.hasForegroundLocation(this)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29 && !hasMotion())
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33 && !hasNotices())
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 400)
        else messageText.text = "Đã có quyền cơ bản. Vị trí mọi lúc cần cấp trong Cài đặt ứng dụng."
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31 && !ScheduledGateController.hasExactAlarm(this)) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + packageName)))
        } else updateState()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateState()
    }

    private fun updateState(alsoMessage: Boolean = true) {
        if (!::scheduleText.isInitialized) return
        chosenText.text = ScheduledGateController.format(targetMillis)
        val scheduledAt = ScheduledGateController.time(this)
        val current = FindMyBikeStore.mode(this)
        scheduleText.text = if (scheduledAt > System.currentTimeMillis()) {
            "Đã hẹn: " + ScheduledGateController.format(scheduledAt) + "\n" +
                "Thời gian chờ: " + ScheduledGateController.waitSeconds(this) + " giây"
        } else if (current == FindMyBikeStore.WAITING) {
            "Đã tới giờ · đang chờ xe vào (" +
                (FindMyBikeStore.remainingWaitMs(this) / 1000L) + " giây còn lại)"
        } else if (current == FindMyBikeStore.TRACKING) {
            "Đã nhận sự kiện xe vào · đang tracking"
        } else ScheduledGateController.lastEvent(this)

        permText.text =
            "Vị trí khi dùng: " + yes(ScheduledGateController.hasForegroundLocation(this)) +
            "\nVị trí mọi lúc: " + yes(ScheduledGateController.hasBackgroundLocation(this)) +
            "\nBáo thức chính xác: " + yes(ScheduledGateController.hasExactAlarm(this)) +
            "\nNotification Access: " + yes(hasListenerAccess()) +
            "\nCho phép thông báo: " + yes(hasNotices()) +
            "\nCảm biến đi bộ: " + yes(hasMotion()) + " (tùy thiết bị)"
        if (alsoMessage) messageText.text = "Bộ chờ không lấy GPS trước khi có sự kiện xe vào. " +
            "Kiểm tra thực tế trên máy để xác nhận Android cho phép tự chạy từ nền."
    }

    private fun yes(ok: Boolean) = if (ok) "ĐÃ CẤP" else "CHƯA CẤP"
    override fun onResume() {
        super.onResume()
        if (::scheduleText.isInitialized) updateState()
    }
}
