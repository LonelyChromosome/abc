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

/** 2 circles of radius 100m, user-provided center points (not campus-wide boundary). */
object CampusFence {
    val CENTERS = arrayOf(
        doubleArrayOf(20.962668790883644, 105.74871859259113),
        doubleArrayOf(20.959607109088672, 105.74789745697416)
    )
    const val RADIUS_M = 100f
    private fun p(c: Context) = c.getSharedPreferences("parking_fence_v5", Context.MODE_PRIVATE)
    fun status(c: Context) = p(c).getString("status", "Chưa đăng ký") ?: ""
    fun event(c: Context) = p(c).getString("event", "Chưa nhận sự kiện") ?: ""
    fun note(c: Context, msg: String) { p(c).edit().putString("status", msg).apply() }
    fun record(c: Context, msg: String) {
        p(c).edit().putString("event", msg).putLong("eventAt", System.currentTimeMillis()).apply()
    }
    fun eventTime(c: Context): String {
        val t = p(c).getLong("eventAt", 0)
        return if (t == 0L) "" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(t))
    }
    fun ready(c: Context): Boolean = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED && (Build.VERSION.SDK_INT < 29 ||
            c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
    fun suppressed(c: Context) = p(c).getBoolean("suppressed", false)
    fun suppress(c: Context, enabled: Boolean) {
        p(c).edit().putBoolean("suppressed", enabled).apply()
    }
    private fun pending(c: Context): PendingIntent =
        PendingIntent.getBroadcast(c, 5106, Intent(c, CampusFenceReceiver::class.java)
            .setAction("parkinglab.GEOFENCE_V5"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    fun register(c: Context) {
        if (!ClassSchedule.inWindow(c) || !ready(c)) return
        val fences = CENTERS.mapIndexed { i, coordinate ->
            Geofence.Builder()
                .setRequestId("phenikaa_gate_${i + 1}")
                .setCircularRegion(coordinate[0], coordinate[1], RADIUS_M)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness(5_000)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        // No INITIAL_TRIGGER_ENTER: won't falsely start while already inside at arming time.
        val request = GeofencingRequest.Builder().addGeofences(fences).build()
        try {
            LocationServices.getGeofencingClient(c).addGeofences(request, pending(c))
                .addOnSuccessListener { note(c, "Google đã nhận 2 geofence · bán kính 100m") }
                .addOnFailureListener { note(c, "Đăng ký thất bại: " + it.message.orEmpty().take(90)) }
        } catch (e: Exception) { note(c, "Lỗi Google Geofence: " + e.javaClass.simpleName) }
    }
    fun remove(c: Context) {
        try {
            LocationServices.getGeofencingClient(c).removeGeofences(pending(c))
                .addOnSuccessListener { note(c, "Ngoài khung giờ · tắt chờ") }
        } catch (_: Exception) {}
    }
}
