package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 100m around the mapped Phenikaa University reference point, NOT its boundary. */
object CampusFence {
    const val LAT = 20.96110
    const val LON = 105.74746
    const val RADIUS_M = 100f
    const val GOOGLE_PLACE_ID = "ChIJ40w5_-9SNDERZORbMtQ5Gjk"
    private const val FENCE_ID = "phenikaa_university_100m_v1"
    private fun prefs(c: Context) = c.getSharedPreferences("parking_fence_v1", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun status(c: Context) = prefs(c).getString("status", "Chưa kích hoạt vùng tự động") ?: ""
    fun event(c: Context) = prefs(c).getString("event", "Chưa nhận sự kiện") ?: ""
    fun eventTime(c: Context): String {
        val t = prefs(c).getLong("event_time", 0L)
        return if (t == 0L) "" else SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(t))
    }
    private fun setStatus(c: Context, text: String) {
        prefs(c).edit().putString("status", text).apply()
    }
    fun record(c: Context, text: String) {
        prefs(c).edit().putString("event", text).putLong("event_time", System.currentTimeMillis()).apply()
    }
    fun canRun(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        (Build.VERSION.SDK_INT < 29 ||
            c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
    fun suppressed(c: Context) = prefs(c).getBoolean("suppressed", false)
    fun suppress(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("suppressed", value).apply()
    }
    fun setEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("enabled", value).apply()
        if (value) register(c) else remove(c)
    }
    private fun pending(c: Context): PendingIntent {
        val intent = Intent(c, CampusFenceReceiver::class.java).setAction("parkinglab.GEOFENCE")
        return PendingIntent.getBroadcast(c, 4100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
    fun register(c: Context) {
        if (!enabled(c)) return
        if (!canRun(c)) {
            setStatus(c, "Chưa cấp Vị trí chính xác / Cho phép mọi lúc")
            return
        }
        val region = Geofence.Builder()
            .setRequestId(FENCE_ID)
            .setCircularRegion(LAT, LON, RADIUS_M)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(5_000)
            .build()
        val req = GeofencingRequest.Builder()
            .addGeofence(region)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .build()
        try {
            LocationServices.getGeofencingClient(c).addGeofences(req, pending(c))
                .addOnSuccessListener { setStatus(c, "Google Geofence đã đăng ký (100m)") }
                .addOnFailureListener {
                    setStatus(c, "Không đăng ký được geofence: " + it.javaClass.simpleName)
                }
        } catch (e: Exception) {
            setStatus(c, "Lỗi đăng ký geofence: " + e.javaClass.simpleName)
        }
    }
    fun remove(c: Context) {
        try {
            LocationServices.getGeofencingClient(c).removeGeofences(pending(c))
                .addOnSuccessListener { setStatus(c, "Đã tắt geofence") }
                .addOnFailureListener { setStatus(c, "Không xóa được geofence") }
        } catch (e: Exception) { setStatus(c, "Lỗi tắt geofence: " + e.javaClass.simpleName) }
    }
}
