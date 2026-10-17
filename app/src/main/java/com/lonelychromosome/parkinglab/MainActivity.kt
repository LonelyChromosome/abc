package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

/** Separate app. Weekly class times are entered manually; no QLDT or BPA integration. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val bg = Color.rgb(8, 13, 22)
    private val mint = Color.rgb(72, 231, 184)
    private val white = Color.rgb(234, 243, 250)
    private val muted = Color.rgb(156, 169, 185)
    private lateinit var stageLabel: TextView
    private lateinit var explanation: TextView
    private lateinit var gpsInfo: TextView
    private lateinit var classInfo: TextView
    private lateinit var hint: TextView
    private lateinit var classInput: EditText
    private lateinit var autoButton: Button
    private lateinit var mainButton: Button
    private lateinit var cancelButton: Button
    private lateinit var mapButton: Button
    private lateinit var gpxButton: Button
    private val ui = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000L)
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun margin(top: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(top), 0, 0)
        }
    private fun background(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
    }
    private fun button(title: String, main: Boolean = false, callback: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 15f
            setTextColor(if (main) bg else white)
            background = background(if (main) mint else Color.rgb(34, 49, 63))
            setOnClickListener { callback() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(22), dp(32), dp(22), dp(32))
        }
        root.addView(label("BPA / PARKING LAB", 13f, mint, true))
        root.addView(label("Tìm xe trong trường", 25f, white, true), margin(12))
        root.addView(label("Lịch custom → 2 vùng 100m → GPS → dừng xe → đi bộ 10 giây.", 14f, muted), margin(10))
        stageLabel = label("CHỜ", 20f, mint, true)
        root.addView(stageLabel, margin(28))
        explanation = label("", 14f, white)
        root.addView(explanation, margin(10))
        gpsInfo = label("", 13f, muted)
        root.addView(gpsInfo, margin(13))
        mainButton = button("XÁC NHẬN BẮT ĐẦU", true) { if (TripStore.running(this)) save() else requestStart() }
        root.addView(mainButton, margin(18))
        cancelButton = button("HỦY TRACKING") { cancel() }
        root.addView(cancelButton, margin(9))
        mapButton = button("DẪN ĐƯỜNG · VỊ TRÍ GPS → TRACKED", true) { launchFindBike() }
        root.addView(mapButton, margin(22))
        gpxButton = button("XUẤT TRACK GPX") { exportGpx() }
        root.addView(gpxButton, margin(8))
        root.addView(label("LỊCH HỌC CUSTOM", 14f, mint, true), margin(27))
        root.addView(label("Một môn mỗi dòng: T2 07:30, T4 13:00, CN 08:00", 13f, muted), margin(7))
        classInput = EditText(this).apply {
            setText(ClassSchedule.raw(this@MainActivity))
            setTextColor(white)
            setHintTextColor(muted)
            hint = "T2 07:30 / T4 13:00"
            setSingleLine(false)
            minLines = 3
            maxLines = 7
            textSize = 15f
            background = background(Color.rgb(28, 42, 57))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(classInput, margin(9))
        root.addView(button("LƯU LỊCH CUSTOM") { saveSchedule() }, margin(9))
        autoButton = button("BẬT CHỜ THEO LỊCH") { toggleAuto() }
        root.addView(autoButton, margin(9))
        classInfo = label("", 13f, muted)
        root.addView(classInfo, margin(12))
        root.addView(button("XEM 2 TỌA ĐỘ TRÊN GOOGLE MAPS") { showPinMap() }, margin(10))
        hint = label("", 13f, muted)
        root.addView(hint, margin(25))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)
        setContentView(scroll)
        render()
    }
    override fun onResume() {
        super.onResume()
        ClassSchedule.reconcile(this)
        handler.removeCallbacks(ui)
        handler.post(ui)
    }
    override fun onPause() {
        handler.removeCallbacks(ui)
        super.onPause()
    }
    private fun saveSchedule() {
        if (!ClassSchedule.save(this, classInput.text.toString())) {
            Toast.makeText(this, "Sai định dạng, ví dụ: T2 07:30", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Đã lưu lịch custom", Toast.LENGTH_SHORT).show()
        render()
    }
    private fun toggleAuto() {
        if (ClassSchedule.enabled(this)) {
            ClassSchedule.setEnabled(this, false)
            render()
            return
        }
        if (ClassSchedule.raw(this).isBlank()) {
            Toast.makeText(this, "Nhập giờ học rồi bấm Lưu lịch", Toast.LENGTH_LONG).show()
            return
        }
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 32)
            return
        }
        ClassSchedule.setEnabled(this, true)
        if (!CampusFence.ready(this)) requestAllTimeLocation()
        render()
    }
    private fun requestAllTimeLocation() {
        AlertDialog.Builder(this)
            .setTitle("Cấp Vị trí mọi lúc")
            .setMessage("Để Google phát hiện 2 vùng khi màn hình khóa: Cài đặt → Quyền → Vị trí → Cho phép mọi lúc. Cần bật Vị trí chính xác và Hoạt động thể chất.")
            .setPositiveButton("Mở cài đặt") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)))
            }.setNegativeButton("Để sau", null).show()
    }
    private fun requestStart() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 31)
            return
        }
        startGps()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        val locationOk = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!locationOk) {
            Toast.makeText(this, "Cần Vị trí chính xác", Toast.LENGTH_LONG).show()
            return
        }
        when (requestCode) {
            31 -> startGps()
            32 -> {
                ClassSchedule.setEnabled(this, true)
                if (!CampusFence.ready(this)) requestAllTimeLocation()
            }
        }
        render()
    }
    private fun startGps() {
        val gps = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!gps.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this).setMessage("Bật GPS/Vị trí trước khi bắt đầu")
                .setPositiveButton("Cài đặt") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.setNegativeButton("Hủy", null).show()
            return
        }
        try {
            startForegroundService(Intent(this, TrackingService::class.java)
                .setAction(TrackingService.START))
        } catch (e: Exception) {
            Toast.makeText(this, "GPS lỗi: " + e.javaClass.simpleName, Toast.LENGTH_LONG).show()
        }
    }
    private fun stopGps() {
        try { startService(Intent(this, TrackingService::class.java)
            .setAction(TrackingService.STOP)) } catch (_: Exception) {}
    }
    private fun save() {
        if (TripStore.count(this) == 0) {
            Toast.makeText(this, "Chưa có GPS để lưu", Toast.LENGTH_SHORT).show()
            return
        }
        if (TripStore.save(this)) { stopGps(); FieldStatus.tracked(this,TripStore.accuracy(this)) }
        else Toast.makeText(this, TripStore.message(this), Toast.LENGTH_LONG).show()
        render()
    }
    private fun cancel() {
        AlertDialog.Builder(this).setTitle("Hủy hành trình?")
            .setPositiveButton("Hủy") { _, _ -> TripStore.cancel(this); stopGps(); FieldStatus.ended(this); render() }
            .setNegativeButton("Quay lại", null).show()
    }
    private fun showPinMap() {
        val a = CampusFence.CENTERS[0].joinToString(",")
        val b = CampusFence.CENTERS[1].joinToString(",")
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.google.com/maps/dir/?api=1&origin=$a&destination=$b")))
    }
    private fun launchFindBike() {
        startActivity(Intent(this, FindBikeActivity::class.java))
    }
    private fun launchMaps() {
        val a = TripStore.parkLat(this) ?: return
        val b = TripStore.parkLon(this) ?: return
        val pos = String.format(Locale.US, "%.7f,%.7f", a, b)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$pos&mode=w"))
                .setPackage("com.google.android.apps.maps"))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$pos&travelmode=walking")))
        }
    }
    private fun exportGpx() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_TITLE, "parking-route.gpx")
        startActivityForResult(intent, 51)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 51 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val gpx = TripStore.gpx(this) ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(gpx.toByteArray()) }
            Toast.makeText(this, "Đã xuất GPX", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi xuất GPX", Toast.LENGTH_LONG).show()
        }
    }
    private fun render() {
        if (!::stageLabel.isInitialized) return
        when (TripStore.stage(this)) {
            TripStore.TRACK -> {
                stageLabel.text = "ĐANG TRACKING"
                explanation.text = "Đang ghi GPS. Mỗi lần dừng chỉ tạo ứng viên; chạy tiếp sẽ bỏ ứng viên cũ. Đã xét ${TripStore.stopsObserved(this)} điểm nghi dừng."
                mainButton.text = "LƯU ĐIỂM ĐỖ THỦ CÔNG"
            }
            TripStore.STOP -> {
                stageLabel.text = "NGHI DỪNG XE · CHƯA CHỐT"
                explanation.text = "Ứng viên dừng #${TripStore.stopsObserved(this)} · ${TripStore.stepCount(this)} bước / ${TripStore.walkElapsed(this)}s · xác nhận GPS rời xe ${TripStore.departureFixes(this)}/2.\n" +
                    TripStore.departureNote(this)
                mainButton.text = "LƯU ĐIỂM ĐỖ THỦ CÔNG"
            }
            TripStore.SAVED -> {
                stageLabel.text = if(NavState.endedAt(this)>TripStore.parkTime(this)) "END · ĐÃ KẾT THÚC DẪN ĐƯỜNG" else "TRACKED · ĐÃ LƯU VỊ TRÍ XE"
                explanation.text = (if (TripStore.autoSaved(this)) "Tự xác minh đi bộ 10s. " else "Lưu thủ công. ") +
                    "Lưu lúc ${TripStore.savedDate(this)}"
                mainButton.text = "BẮT ĐẦU HÀNH TRÌNH MỚI"
            }
            else -> {
                stageLabel.text = if (ClassSchedule.enabled(this)) (if (ClassSchedule.inWindow(this)) "CHỜ 100 M" else "HẸN GIỜ") else "CHƯA BẬT HẸN GIỜ"
                explanation.text = "Tự động chờ từ 30 phút trước giờ học đã nhập, chỉ bật GPS sau khi vào vùng 100m."
                mainButton.text = "BẮT ĐẦU GPS THỦ CÔNG"
            }
        }
        cancelButton.visibility = if (TripStore.running(this)) View.VISIBLE else View.GONE
        mapButton.visibility = if (TripStore.running(this) || TripStore.hasSaved(this)) View.VISIBLE else View.GONE
        mapButton.text = if (TripStore.running(this))
            "XEM TRACE GPS TRỰC TIẾP TRÊN MAP"
        else "DẪN ĐƯỜNG · GPS HIỆN TẠI → TRACKED"
        gpxButton.visibility = if (TripStore.savedRoute(this) != null) View.VISIBLE else View.GONE
        val gpsAge = if (TripStore.lastFix(this) == 0L) "Chưa có"
            else "${((System.currentTimeMillis() - TripStore.lastFix(this)) / 1000L).coerceAtLeast(0L)}s trước"
        gpsInfo.text = "${TripStore.count(this)} điểm GPS · ${TripStore.metres(this).toInt()}m" +
            "\nGPS: $gpsAge · sai số: ${TripStore.accuracy(this).toInt()}m"
        autoButton.text = if (ClassSchedule.enabled(this)) "TẮT CHỜ THEO LỊCH" else "BẬT CHỜ THEO LỊCH"
        classInfo.text = ClassSchedule.nextStatus(this) + "\n" +
            "Vùng 1: 20.962668790883644, 105.74871859259113\n" +
            "Vùng 2: 20.959607109088672, 105.74789745697416\n" +
            "Google: " + CampusFence.status(this) + "\n" +
            "Lần cuối: " + CampusFence.event(this) + " " + CampusFence.eventTime(this)
        hint.text = TripStore.message(this).takeIf { it.isNotBlank() }
            ?: "Lịch nhập thủ công. Track chỉ lưu trong máy. Android có thể báo geofence muộn."
    }
}
