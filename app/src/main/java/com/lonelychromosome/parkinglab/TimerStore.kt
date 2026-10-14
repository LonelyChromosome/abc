package com.lonelychromosome.parkinglab

import android.content.Context
import android.os.SystemClock
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

/** Elapsed time and service heartbeats are tracked separately. */
object TimerStore {
    private fun p(c: Context) = c.getSharedPreferences("standby_timer", Context.MODE_PRIVATE)
    fun active(c: Context) = p(c).getBoolean("active", false)
    fun start(c: Context) {
        val wall = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtime()
        p(c).edit().putBoolean("active", true).putLong("start_mono", mono)
            .putLong("boot_origin", wall - mono).putLong("heartbeat_wall", wall)
            .putLong("heartbeats", 0).putLong("restarts", 0)
            .putLong("task_removed", 0).apply()
    }
    fun stop(c: Context) { p(c).edit().putBoolean("active", false).apply() }
    fun elapsed(c: Context): Long {
        if (!active(c)) return 0
        val bootOrigin = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (abs(bootOrigin - p(c).getLong("boot_origin", 0)) > 120_000) return -1
        return (SystemClock.elapsedRealtime() - p(c).getLong("start_mono", 0)).coerceAtLeast(0)
    }
    fun beat(c: Context) {
        val prefs = p(c)
        prefs.edit().putLong("heartbeats", prefs.getLong("heartbeats", 0) + 1)
            .putLong("heartbeat_wall", System.currentTimeMillis()).apply()
    }
    fun restart(c: Context) {
        val prefs = p(c)
        prefs.edit().putLong("restarts", prefs.getLong("restarts", 0) + 1).apply()
    }
    fun taskRemoved(c: Context) {
        p(c).edit().putLong("task_removed", System.currentTimeMillis()).apply()
    }
    fun beats(c: Context) = p(c).getLong("heartbeats", 0L)
    fun restarts(c: Context) = p(c).getLong("restarts", 0L)
    fun beatAge(c: Context): Long {
        val last = p(c).getLong("heartbeat_wall", 0L)
        return if (last <= 0) -1 else ((System.currentTimeMillis() - last) / 1000).coerceAtLeast(0)
    }
    fun removedAt(c: Context): String {
        val t = p(c).getLong("task_removed", 0L)
        return if (t == 0L) "Chưa có" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(t))
    }
    fun time(ms: Long): String {
        if (ms < 0) return "REBOOT"
        val s = ms / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }
}
