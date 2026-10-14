package com.lonelychromosome.parkinglab

import android.content.Context
import android.os.SystemClock
import java.util.Locale

/** Session is local-only. Elapsed realtime is used only within the same boot. */
object FindMyBikeStore {
    const val IDLE = "IDLE"
    const val WAITING = "WAITING"
    const val NEEDS_MANUAL = "NEEDS_MANUAL"
    const val TRACKING = "TRACKING"
    const val SAVED = "SAVED"

    private fun p(context: Context) = context.getSharedPreferences("find_my_bike_v03", Context.MODE_PRIVATE)

    fun mode(context: Context): String = p(context).getString("mode", IDLE) ?: IDLE
    fun waitingSince(context: Context): Long = p(context).getLong("waiting_since", 0L)
    fun waitingDeadline(context: Context) = p(context).getLong("waiting_deadline", 0L)
    fun waitingStartedWall(context: Context) = p(context).getLong("waiting_started_wall", 0L)
    fun waitWindowMs(context: Context) = p(context).getLong("wait_window_ms", 10_000L)
    fun remainingWaitMs(context: Context) = (waitingDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)
    fun withinWindow(context: Context) = mode(context) == WAITING &&
        waitingSince(context) > 0L && remainingWaitMs(context) > 0L &&
        SystemClock.elapsedRealtime() >= waitingSince(context)
    fun serviceReady(context: Context) = p(context).getBoolean("service_ready", false)
    fun setServiceReady(context: Context, ready: Boolean) {
        p(context).edit().putBoolean("service_ready", ready).commit()
    }
    fun startWaiting(context: Context, waitMillis: Long = 10_000L) {
        p(context).edit().putString("mode", WAITING)
            .putLong("waiting_since", SystemClock.elapsedRealtime())
            .putLong("waiting_started_wall", System.currentTimeMillis())
            .putLong("wait_window_ms", waitMillis.coerceIn(10_000L, 14_400_000L))
            .putLong("waiting_deadline", System.currentTimeMillis() + waitMillis.coerceIn(10_000L, 14_400_000L))
            .putBoolean("service_ready", false)
            .remove("last_lat").remove("last_lon").commit()
    }
    fun needManual(context: Context) {
        if (mode(context) == WAITING) p(context).edit().putString("mode", NEEDS_MANUAL).commit()
    }
    fun track(context: Context) {
        p(context).edit().putString("mode", TRACKING).commit()
    }
    fun lastFix(context: Context, lat: Double, lon: Double, accuracy: Float) {
        p(context).edit().putLong("last_lat", lat.toBits())
            .putLong("last_lon", lon.toBits())
            .putFloat("last_acc", accuracy).apply()
    }
    fun lastFix(context: Context): Triple<Double, Double, Float>? {
        val v = p(context)
        if (!v.contains("last_lat") || !v.contains("last_lon")) return null
        return Triple(Double.fromBits(v.getLong("last_lat", 0)),
            Double.fromBits(v.getLong("last_lon", 0)), v.getFloat("last_acc", 999f))
    }
    fun save(context: Context, lat: Double, lon: Double, accuracy: Float, method: String) {
        p(context).edit().putLong("park_lat", lat.toBits())
            .putLong("park_lon", lon.toBits()).putFloat("park_acc", accuracy)
            .putLong("park_time", System.currentTimeMillis())
            .putString("park_method", method).putString("mode", SAVED).commit()
    }
    fun saved(context: Context): Triple<Double, Double, Float>? {
        val v = p(context)
        if (!v.contains("park_lat") || !v.contains("park_lon")) return null
        return Triple(Double.fromBits(v.getLong("park_lat", 0)),
            Double.fromBits(v.getLong("park_lon", 0)), v.getFloat("park_acc", 999f))
    }
    fun savedTime(context: Context): Long = p(context).getLong("park_time", 0L)
    fun savedMethod(context: Context): String = p(context).getString("park_method", "") ?: ""
    fun reset(context: Context) {
        p(context).edit().putString("mode", IDLE).remove("last_lat").remove("last_lon").commit()
    }
    fun describe(context: Context): String = when (mode(context)) {
        WAITING -> "Đang chờ thông báo Phenikaa Uni"
        NEEDS_MANUAL -> "Hết thời gian chờ. Hãy chọn ĐÁNH DẤU THỦ CÔNG."
        TRACKING -> "Đang theo dõi. Dừng xe rồi đi bộ để tự lưu."
        SAVED -> "Đã lưu vị trí dựng xe"
        else -> "Chưa bắt đầu Find My Bike"
    }
    fun coords(context: Context): String {
        val v = saved(context) ?: return "Chưa có điểm đỗ"
        return String.format(Locale.US, "%.6f, %.6f (±%.0f m)", v.first, v.second, v.third)
    }
}
