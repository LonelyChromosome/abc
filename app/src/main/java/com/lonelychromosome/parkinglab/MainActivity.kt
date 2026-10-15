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

/** One-tap trip start, one-tap final parking confirmation, local track and Maps. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val bg = Color.rgb(8, 13, 22)
    private val mint = Color.rgb(72, 231, 184)
    private val textColor = Color.rgb(235, 243, 250)
    private val muted = Color.rgb(156, 169, 185)
    private lateinit var stageLabel: TextView
    private lateinit var explanation: TextView
    private lateinit var gpsInfo: TextView
    private lateinit var hint: TextView
    private lateinit var mainButton: Button
    private lateinit var cancelButton: Button
    private lateinit var mapsButton: Button
    private lateinit var gpxButton: Button
    private lateinit var fenceInfo: TextView
    private lateinit var fenceButton: Button
    private val updater = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
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
    private fun margin(top: Int, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(top), 0, dp(bottom))
        }
    private fun fill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
    }
    private fun btn(title: String, main: Boolean, action: () -> Unit) =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 16f
            setTextColor(if (main) bg else textColor)
            background = fill(if (main) mint else Color.rgb(36, 51, 65))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { action() }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(24), dp(40), dp(24), dp(28))
        }
        content.addView(label("BPA  /  PARKING LAB", 13f, mint, true))
        content.addView(label("Chỗ đỗ của t", 29f, textColor, true), margin(12))
        content.addView(label("Google phát hiện vào vùng 100m → GPS tự ghi đường đi.", 14f, muted), margin(9))
        stageLabel = label("CHỜ XÁC NHẬN", 20f, mint, true)
        content.addView(stageLabel, margin(35))
        explanation = label("", 15f, textColor)
        content.addView(explanation, margin(13))
        gpsInfo = label("", 14f, muted)
        content.addView(gpsInfo, margin(16))
        fenceInfo = label("", 13f, muted)
        content.addView(fenceInfo, margin(16))
        fenceButton = btn("BẬT TỰ ĐỘNG 100M", false) { toggleFence() }
        content.addView(fenceButton, margin(12))
        content.addView(btn("XEM GHIM GOOGLE MAPS", false) { openSchoolPin() }, margin(9))
        mainButton = btn("XÁC NHẬN BẮT ĐẦU", true) { onMainClick() }
        content.addView(mainButton, margin(27))
        cancelButton = btn("HỦY TRACKING", false) { confirmCancel() }
        content.addView(cancelButton, margin(10))
        mapsButton = btn("DẪN GOOGLE MAPS", true) { launchMaps() }
        content.addView(mapsButton, margin(26))
        gpxButton = btn("XUẤT HÀNH TRÌNH .GPX", false) { exportTrack() }
        content.addView(gpxButton, margin(10))
        hint = label("", 13f, muted)
        content.addView(hint, margin(25, 25))
        val scroller = ScrollView(this)
        scroller.isFillViewport = true
        scroller.addView(content)
        setContentView(scroller)
        render()
    }
    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updater)
        handler.post(updater)
        if (CampusFence.enabled(this) && CampusFence.canRun(this)) CampusFence.register(this)
    }
    override fun onPause() {
        handler.removeCallbacks(updater)
        super.onPause()
    }
    private fun openSchoolPin() {
        val target = "https://www.google.com/maps/place/?q=place_id:" + CampusFence.GOOGLE_PLACE_ID
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    }
    private fun toggleFence() {
        if (CampusFence.enabled(this)) {
            CampusFence.setEnabled(this, false)
            render()
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION), 31)
            return
        }
        CampusFence.setEnabled(this, true)
        if (!CampusFence.canRun(this)) {
            AlertDialog.Builder(this)
                .setTitle("Cho phép vị trí mọi lúc")
                .setMessage("Android yêu cầu quyền 'Cho phép mọi lúc' để Google đánh thức GPS lúc điện thoại khóa. Mở Quyền ứng dụng → Vị trí → Cho phép mọi lúc, rồi quay lại.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + packageName)))
                }.setNegativeButton("Để sau", null).show()
        }
        render()
    }
    private fun onMainClick() {
        if (TripStore.running(this)) saveParking() else askStart()
    }
    private fun askStart() {
        val ask = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.ACCESS_FINE_LOCATION)
            ask.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ask.isNotEmpty()) {
            requestPermissions(ask.toTypedArray(), 30)
            return
        }
        startTracking()
    }
    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 31) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                toggleFence()
            } else Toast.makeText(this, "Cần Vị trí chính xác để đặt geofence 100m", Toast.LENGTH_LONG).show()
        }
        if (requestCode == 30) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) startTracking()
            else AlertDialog.Builder(this)
                .setMessage("Cần quyền Vị trí chính xác để ghi hành trình và lưu chỗ đỗ.")
                .setPositiveButton("OK", null).show()
        }
    }
    private fun startTracking() {
        val gps = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!gps.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this).setMessage("Bật GPS/Vị trí trước khi bắt đầu.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.setNegativeButton("Hủy", null).show()
            return
        }
        try {
            startForegroundService(Intent(this, TrackingService::class.java)
                .setAction(TrackingService.START))
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setMessage("Không bật được tracking: ${e.javaClass.simpleName}")
                .setPositiveButton("OK", null).show()
        }
        render()
    }
    private fun stopService() {
        try {
            startService(Intent(this, TrackingService::class.java)
                .setAction(TrackingService.STOP))
        } catch (_: Exception) { }
    }
    private fun saveParking() {
        if (TripStore.count(this) == 0) {
            Toast.makeText(this, "Chưa có tọa độ GPS chính xác để lưu.", Toast.LENGTH_LONG).show()
            return
        }
        if (!TripStore.save(this)) {
            Toast.makeText(this, "Lỗi lưu: ${TripStore.message(this)}", Toast.LENGTH_LONG).show()
            return
        }
        stopService()
        render()
    }
    private fun confirmCancel() {
        AlertDialog.Builder(this).setTitle("Hủy hành trình?")
            .setMessage("Hành trình đang ghi sẽ bị xóa. Chỗ đỗ đã lưu trước đó vẫn còn.")
            .setNegativeButton("Quay lại", null)
            .setPositiveButton("Hủy") { _, _ ->
                TripStore.cancel(this)
                stopService()
                render()
            }.show()
    }
    private fun launchMaps() {
        val lat = TripStore.parkLat(this) ?: return
        val lon = TripStore.parkLon(this) ?: return
        val dest = String.format(Locale.US, "%.7f,%.7f", lat, lon)
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=$dest&mode=w"))
                .setPackage("com.google.android.apps.maps"))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$dest&travelmode=walking")))
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được Google Maps", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun exportTrack() {
        if (TripStore.savedRoute(this) == null) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_TITLE, "parking-route.gpx")
        startActivityForResult(intent, 51)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 51 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val xml = TripStore.gpx(this) ?: return
                contentResolver.openOutputStream(uri)?.use {
                    it.write(xml.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "Đã xuất hành trình", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi xuất GPX: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun render() {
        if (!::stageLabel.isInitialized) return
        val stage = TripStore.stage(this)
        when (stage) {
            TripStore.TRACK -> {
                stageLabel.text = "ĐANG TRACKING"
                explanation.text = "Đang ghi lại đường xe đi. App tự nhận diện trạng thái dừng; m cũng có thể lưu thủ công."
                mainButton.text = "XÁC NHẬN ĐÃ ĐỖ · LƯU"
            }
            TripStore.STOP -> {
                stageLabel.text = "XE CÓ VẺ ĐÃ DỪNG"
                explanation.text = "Vị trí GPS đã ổn định. Nếu đây là chỗ đỗ, xác nhận lưu. Nếu chỉ dừng tạm thì cứ tiếp tục đi."
                mainButton.text = "LƯU VỊ TRÍ ĐỖ"
            }
            TripStore.SAVED -> {
                stageLabel.text = "ĐÃ LƯU ĐIỂM ĐỖ"
                explanation.text = "Đã lưu lúc ${TripStore.savedDate(this)}. Dùng Google Maps để đi bộ quay lại xe."
                mainButton.text = "BẮT ĐẦU LẦN MỚI"
            }
            else -> {
                stageLabel.text = "CHỜ XÁC NHẬN"
                explanation.text = "Google theo dõi vùng 100m quanh ghim trường. Khi vào vùng sẽ tự bật tracking; vẫn có thể bắt đầu thủ công."
                mainButton.text = "XÁC NHẬN BẮT ĐẦU"
            }
        }
        cancelButton.visibility = if (TripStore.running(this)) View.VISIBLE else View.GONE
        mapsButton.visibility = if (TripStore.hasSaved(this)) View.VISIBLE else View.GONE
        gpxButton.visibility = if (TripStore.savedRoute(this) != null) View.VISIBLE else View.GONE
        val age = if (TripStore.lastFix(this) <= 0L) "Chưa có"
            else "${((System.currentTimeMillis() - TripStore.lastFix(this)) / 1000L).coerceAtLeast(0L)} giây trước"
        val acc = if (TripStore.accuracy(this) < 0) "—" else "${TripStore.accuracy(this).toInt()} m"
        gpsInfo.text = if (TripStore.running(this) || stage == TripStore.SAVED)
            "${TripStore.count(this)} điểm GPS  ·  ${TripStore.metres(this).toInt()} m\n" +
                "GPS cập nhật: $age  ·  Sai số: $acc"
        else "GPS đang tắt."
        fenceButton.text = if (CampusFence.enabled(this)) "TẮT TỰ ĐỘNG 100M" else "BẬT TỰ ĐỘNG 100M"
        fenceInfo.text = "Ghim trường: 20.96110, 105.74746 · bán kính 100m\\n" +
            "Đăng ký: " + CampusFence.status(this) + "\\n" +
            "Sự kiện: " + CampusFence.event(this) + " " + CampusFence.eventTime(this)
        hint.text = TripStore.message(this).takeIf { it.isNotBlank() }
            ?: "Lưu trên điện thoại. Google Maps dẫn tới tọa độ đã lưu, không tự chạy theo đường GPS gốc."
    }
}
