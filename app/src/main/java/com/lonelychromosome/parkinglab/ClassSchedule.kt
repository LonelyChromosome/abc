package com.lonelychromosome.parkinglab

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Student-entered weekly class-start times; GPS/geofences idle until -30 minutes. */
object ClassSchedule {
    private fun p(c: Context) = c.getSharedPreferences("class_schedule_v5", Context.MODE_PRIVATE)
    private val pattern = Regex("^(T[2-7]|CN)\\s+([01]?\\d|2[0-3]):([0-5]\\d)$", RegexOption.IGNORE_CASE)
    private data class Course(val weekday: Int, val hour: Int, val minute: Int)
    fun raw(c: Context) = p(c).getString("weekly", "") ?: ""
    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, enabled: Boolean) {
        p(c).edit().putBoolean("enabled", enabled).apply()
        reconcile(c)
    }
    private fun parse(text: String): List<Course>? {
        val output = mutableListOf<Course>()
        for (line in text.lineSequence().map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotBlank() }) {
            val m = pattern.matchEntire(line) ?: return null
            val day = if (m.groupValues[1] == "CN") Calendar.SUNDAY
                else m.groupValues[1].drop(1).toInt()
            output.add(Course(day, m.groupValues[2].toInt(), m.groupValues[3].toInt()))
        }
        return if (output.isNotEmpty()) output.distinct() else null
    }
    fun save(c: Context, text: String): Boolean {
        val values = parse(text) ?: return false
        p(c).edit().putString("weekly", text.trim()).apply()
        reconcile(c)
        return values.isNotEmpty()
    }
    private fun alarm(c: Context) = PendingIntent.getBroadcast(c, 5105,
        Intent(c, ScheduleAlarmReceiver::class.java).setAction("parkinglab.SCHEDULE_TICK"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private data class Window(val start: Long, val end: Long)
    private fun windows(c: Context, now: Long): List<Window> {
        val classes = parse(raw(c)) ?: return emptyList()
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val result = mutableListOf<Window>()
        for (offset in -1..8) {
            val day = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            for (lesson in classes) {
                if (day.get(Calendar.DAY_OF_WEEK) != lesson.weekday) continue
                day.set(Calendar.HOUR_OF_DAY, lesson.hour)
                day.set(Calendar.MINUTE, lesson.minute)
                val start = day.timeInMillis
                // 30 minutes before, up to 60 minutes after class starts (late arrivals).
                result.add(Window(start - 30L * 60_000, start + 60L * 60_000))
            }
        }
        return result
    }
    fun inWindow(c: Context, now: Long = System.currentTimeMillis()): Boolean =
        enabled(c) && windows(c, now).any { now in it.start..it.end }

    /** Arms two Google geofences only in lesson windows. Does not request GPS points. */
    fun reconcile(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarm(c))
        if (!enabled(c)) {
            CampusFence.remove(c)
            return
        }
        val now = System.currentTimeMillis()
        val relevant = windows(c, now)
        if (relevant.any { now in it.start..it.end }) {
            if (CampusFence.ready(c)) CampusFence.register(c)
            else CampusFence.note(c, "Chưa có quyền vị trí chính xác / mọi lúc")
        } else CampusFence.remove(c)

        val next = relevant.flatMap { listOf(it.start, it.end + 1000L) }
            .filter { it > now + 1000L }.minOrNull()
        if (next != null) {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm(c))
            else if (Build.VERSION.SDK_INT < 31)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm(c))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm(c))
        }
    }
    fun nextStatus(c: Context): String {
        if (!enabled(c)) return "Chưa bật tự động theo lịch"
        val now = System.currentTimeMillis()
        if (inWindow(c, now)) return "ĐANG CHỜ • 2 vùng 100m"
        val next = windows(c, now).map { it.start }.filter { it > now }.minOrNull()
        return if (next == null) "Chưa có lịch hợp lệ"
            else "Sẽ bật chờ: " + SimpleDateFormat("EEE HH:mm", Locale("vi", "VN")).format(Date(next))
    }
}
