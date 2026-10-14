package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Standalone Parking Lab v0.3. Google Maps in-app needs an Android-restricted Maps API key. */
class MainActivity : Activity() {
    companion object {
        const val EXTRA_MANUAL_PROMPT = "parkinglab.manual_prompt"
    }

    private val tick = Handler(Looper.getMainLooper())
    private var permissionPending = ""
    private var mapView: MapView? = null
    private var googleMap: com.google.android.gms.maps.GoogleMap? = null
    private var shownMapPoint: String? = null

    private lateinit var state: TextView
    private lateinit var countdown: TextView
    private lateinit var gpsStatus: TextView
    private lateinit var savedStatus: TextView
    private lateinit var listenerStatus: TextView
    private lateinit var logStatus: TextView
    private lateinit var timerStatus: TextView
    private lateinit var startBike: Button
    private lateinit var manual: Button
    private lateinit var fakeGate: Button
    private lateinit var saveBike: Button
    private lateinit var cancelBike: Button
    private lateinit var navigation: Button
    private lateinit var standbyStart: Button
    private lateinit var standbyStop: Button

    private val loop = object : Runnable {
        override fun run() {
            refresh()
            tick.postDelayed(this, 1000L)
        }
    }

    private val pageBg = Color.rgb(8, 13, 22)
    private val panelBg = Color.rgb(23, 34, 49)
    private val mint = Color.rgb(60, 229, 176)
    private val white = Color.rgb(239, 246, 255)
    private val muted = Color.rgb(161, 177, 192)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(14).toFloat()
    }
    private fun label(title: String, size: Float, color: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = title
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun spacing(top: Int, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, dp(bottom)) }

    private fun action(title: String, bright: Boolean = false, onTap: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(if (bright) pageBg else white)
            background = rounded(if (bright) mint else Color.rgb(45, 61, 79))
            setOnClickListener { onTap() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
            setBackgroundColor(pageBg)
        }
        content.addView(label("BETTER PHENIKAA · PARKING LAB", 12f, mint, true))
        content.addView(label("Find My Bike", 30f, white, true), spacing(6))
        content.addView(label("Phenikaa Uni kích hoạt · BPA xác định điểm đỗ · Google Maps dẫn đường",
            13f, muted), spacing(6))

        val heading = label("PHIÊN THEO DÕI", 12f, mint, true)
        content.addView(heading, spacing(26))
        val statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panelBg)
        }
        state = label("", 17f, white, true)
        countdown = label("", 13f, mint)
        gpsStatus = label("", 13f, muted)
        statusPanel.addView(state)
        statusPanel.addView(countdown, spacing(6))
        statusPanel.addView(gpsStatus, spacing(7))
        content.addView(statusPanel, spacing(9))

        startBike = action("Bật Find My Bike · chờ 10 giây", true) { requestBikeStart(false) }
        content.addView(startBike, spacing(12))
        manual = action("Đánh dấu thủ công · bật tracking") { requestBikeStart(true) }
        content.addView(manual, spacing(8))
        fakeGate = action("TEST: Giả lập tín hiệu vào cổng") {
            if (FindMyBikeStore.mode(this) == FindMyBikeStore.WAITING) {
                startService(Intent(this, BikeTrackingService::class.java).setAction(BikeTrackingService.GATE))
            }
        }
        content.addView(fakeGate, spacing(8))
        saveBike = action("Lưu vị trí dựng xe ngay") {
            startService(Intent(this, BikeTrackingService::class.java).setAction(BikeTrackingService.SAVE))
        }
        content.addView(saveBike, spacing(8))
        cancelBike = action("Dừng phiên / Hủy") {
            startService(Intent(this, BikeTrackingService::class.java).setAction(BikeTrackingService.CANCEL))
        }
        content.addView(cancelBike, spacing(8))

        content.addView(label("ĐIỂM DỰNG XE", 12f, mint, true), spacing(25))
        savedStatus = label("", 14f, white)
        content.addView(savedStatus, spacing(8))

        if (BuildConfig.MAPS_READY) {
            mapView = MapView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
            }
            mapView?.onCreate(savedInstanceState)
            mapView?.getMapAsync { map ->
                googleMap = map
                map.uiSettings.isZoomControlsEnabled = true
                updateMap()
            }
            mapView?.let { content.addView(it, spacing(12)) }
        } else {
            content.addView(label(
                "Bản đồ Google trong BPA: cần cấu hình MAPS_API_KEY để hiển thị. " +
                "Chức năng lưu tọa độ và mở Google Maps vẫn hoạt động.",
                13f, muted), spacing(10))
        }

        navigation = action("Dẫn đường đi bộ bằng Google Maps", true) { navigateToBike() }
        content.addView(navigation, spacing(12))

        content.addView(label("BỘ ĐỌC THÔNG BÁO · V0.2", 12f, mint, true), spacing(27))
        val openPermission = action("Mở quyền truy cập thông báo") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        content.addView(openPermission, spacing(9))
        listenerStatus = label("", 13f, muted)
        content.addView(listenerStatus, spacing(8))
        val fakeNotice = action("TEST: Gửi thông báo Android giả") { requestFakeNotification() }
        content.addView(fakeNotice, spacing(8))
        logStatus = label("", 12f, muted)
        content.addView(logStatus, spacing(11))

        content.addView(label("BÀI TEST STANDBY · V0.1", 12f, mint, true), spacing(26))
        standbyStart = action("Bắt đầu test nền") { runStandby() }
        content.addView(standbyStart, spacing(8))
        standbyStop = action("Dừng test nền") {
            TimerStore.stop(this)
            startService(Intent(this, StandbyService::class.java).setAction(StandbyService.STOP))
            refresh()
        }
        content.addView(standbyStop, spacing(8))
        timerStatus = label("", 12f, muted)
        content.addView(timerStatus, spacing(9, 24))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content)
        setContentView(scroll)
        refresh()
        if (intent?.getBooleanExtra(EXTRA_MANUAL_PROMPT, false) == true) {
            state.text = "Không nhận được push · đánh dấu thủ công để bắt đầu"
        }
    }

    private fun hasPositionPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestBikeStart(fromManual: Boolean) {
        val permissions = mutableListOf<String>()
        if (!hasPositionPermission()) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionPending = if (fromManual) "MANUAL" else "START"
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 81)
        else completeBikeStart()
    }

    private fun completeBikeStart() {
        if (!hasPositionPermission()) {
            state.text = "Cần quyền vị trí để ghi điểm dựng xe"
            return
        }
        try {
            val mode = FindMyBikeStore.mode(this)
            val action = if (permissionPending == "MANUAL") BikeTrackingService.MANUAL
                         else BikeTrackingService.START
            if (action == BikeTrackingService.MANUAL &&
                mode != FindMyBikeStore.WAITING && mode != FindMyBikeStore.NEEDS_MANUAL) return
            startForegroundService(Intent(this, BikeTrackingService::class.java).setAction(action))
            refresh()
        } catch (ex: Exception) {
            state.text = "Không thể khởi chạy GPS: " + ex.javaClass.simpleName
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            81 -> completeBikeStart()
            82 -> if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) postFakeNotification()
            83 -> runStandby()
        }
    }

    private fun notificationGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= 27) {
            return getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(
                    ComponentName(this, ParkingNotificationListener::class.java))
        }
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == packageName
        }
    }

    private fun requestFakeNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 82)
        } else postFakeNotification()
    }

    private fun postFakeNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "parkinglab_test", "Parking Lab test", NotificationManager.IMPORTANCE_DEFAULT))
        val n = Notification.Builder(this, "parkinglab_test")
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("FAKE: XE ĐÃ VÀO CỔNG")
            .setContentText("Kiểm tra listener, không giả danh Phenikaa Uni")
            .setAutoCancel(true).build()
        manager.notify(2001, n)
    }

    private fun runStandby() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 83)
            return
        }
        try {
            if (!TimerStore.active(this)) TimerStore.start(this)
            startForegroundService(Intent(this, StandbyService::class.java).setAction(StandbyService.START))
        } catch (ex: Exception) {
            TimerStore.stop(this)
            timerStatus.text = "Lỗi test nền: " + ex.javaClass.simpleName
        }
    }

    private fun navigateToBike() {
        val fix = FindMyBikeStore.saved(this) ?: return
        val destination = fix.first.toString() + "," + fix.second.toString()
        val nativeMap = Intent(Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=" + destination + "&mode=w"))
            .setPackage("com.google.android.apps.maps")
        try {
            startActivity(nativeMap)
        } catch (_: Exception) {
            try {
                val web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" +
                    destination + "&travelmode=walking")
                startActivity(Intent(Intent.ACTION_VIEW, web))
            } catch (_: Exception) {
                state.text = "Không tìm thấy ứng dụng có thể mở Google Maps"
            }
        }
    }

    private fun updateMap() {
        val fix = FindMyBikeStore.saved(this) ?: return
        val map = googleMap ?: return
        val key = fix.first.toString() + "," + fix.second.toString()
        if (key == shownMapPoint) return
        shownMapPoint = key
        val position = LatLng(fix.first, fix.second)
        map.clear()
        map.addMarker(MarkerOptions().position(position).title("Xe của m ở đây"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 18f))
    }

    private fun refresh() {
        if (!::state.isInitialized) return
        val current = FindMyBikeStore.mode(this)
        state.text = FindMyBikeStore.describe(this)
        countdown.text = if (current == FindMyBikeStore.WAITING) {
            val remaining = (10_000L -
                (android.os.SystemClock.elapsedRealtime() - FindMyBikeStore.waitingSince(this)))
                .coerceAtLeast(0L)
            "Đang chờ: " + ((remaining + 999L) / 1000L) + " giây"
        } else "Nguồn: Phenikaa Uni · dự phòng: đánh dấu thủ công"
        val last = FindMyBikeStore.lastFix(this)
        gpsStatus.text = if (current == FindMyBikeStore.TRACKING) {
            if (last == null) "GPS đang tìm tọa độ đầu tiên..."
            else "GPS gần nhất: ±" + last.third.toInt() + " m · giữ 10 giây cuối"
        } else "Không lấy GPS khi đang chờ thông báo"

        val saved = FindMyBikeStore.saved(this)
        savedStatus.text = if (saved == null) "Chưa có điểm đỗ." else {
            "Tọa độ: " + FindMyBikeStore.coords(this) + "\n" +
            "Cách ghi: " + FindMyBikeStore.savedMethod(this) + "\n" +
            "Lúc: " + SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
                .format(Date(FindMyBikeStore.savedTime(this)))
        }
        startBike.isEnabled = current == FindMyBikeStore.IDLE || current == FindMyBikeStore.SAVED
        manual.isEnabled = current == FindMyBikeStore.WAITING || current == FindMyBikeStore.NEEDS_MANUAL
        fakeGate.isEnabled = current == FindMyBikeStore.WAITING
        saveBike.isEnabled = current == FindMyBikeStore.TRACKING
        cancelBike.isEnabled = current == FindMyBikeStore.WAITING ||
            current == FindMyBikeStore.NEEDS_MANUAL || current == FindMyBikeStore.TRACKING
        navigation.isEnabled = saved != null
        updateMap()

        listenerStatus.text = "Notification Access: " +
            (if (notificationGranted()) "ĐÃ CẤP" else "CHƯA CẤP") +
            " · Listener: " + (if (NotificationStore.connected(this)) "đang kết nối" else "chưa kết nối") +
            "\nPhenikaa Uni: " + NotificationStore.phenikaaCount(this) +
            " thông báo · khớp xe vào: " + NotificationStore.matchedCount(this) +
            "\nNguồn theo dõi: " + GateNotificationMatcher.PHENIKAA_PACKAGE
        logStatus.text = NotificationStore.recentText(this)

        val active = TimerStore.active(this)
        val age = TimerStore.beatAge(this)
        timerStatus.text = "Thời gian: " + TimerStore.time(TimerStore.elapsed(this)) +
            " · nhịp: " + TimerStore.beats(this) +
            " · cuối: " + (if (age < 0) "chưa có" else age.toString() + "s") +
            "\nRestart: " + TimerStore.restarts(this) +
            " · vuốt khỏi đa nhiệm: " + TimerStore.removedAt(this)
        standbyStart.isEnabled = !active
        standbyStop.isEnabled = active
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        tick.removeCallbacks(loop)
        tick.post(loop)
    }
    override fun onPause() {
        tick.removeCallbacks(loop)
        mapView?.onPause()
        super.onPause()
    }
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        tick.removeCallbacks(loop)
        mapView?.onDestroy()
        super.onDestroy()
    }
}
