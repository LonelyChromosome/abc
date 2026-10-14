package com.lonelychromosome.parkinglab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small, local-only test history. External notification contents are never stored. */
object NotificationStore {
    private fun prefs(c: Context) = c.getSharedPreferences("listener_test", Context.MODE_PRIVATE)
    fun connected(c: Context) = prefs(c).getBoolean("connected", false)
    fun setConnected(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("connected", value).apply()
    }
    fun ownCount(c: Context): Int = prefs(c).getInt("fake_count", 0)
    fun externalCount(c: Context): Int = prefs(c).getInt("external_count", 0)

    fun record(c: Context, pkg: String, isFake: Boolean, postedAt: Long) {
        val p = prefs(c)
        val key = if (isFake) "fake_count" else "external_count"
        val previous = try { JSONArray(p.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        val now = System.currentTimeMillis()
        val item = JSONObject()
            .put("time", now)
            .put("package", pkg)
            .put("type", if (isFake) "FAKE" else "EXTERNAL")
            .put("latency_ms", (now - postedAt).coerceAtLeast(0))
        val next = JSONArray().put(item)
        for (i in 0 until minOf(previous.length(), 11)) next.put(previous.get(i))
        p.edit()
            .putInt(key, p.getInt(key, 0) + 1)
            .putString("history", next.toString())
            .commit()
    }

    fun recentText(c: Context): String {
        val data = try { JSONArray(prefs(c).getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        if (data.length() == 0) return "Chưa nhận thông báo thử nghiệm nào.\n\n" +
            "Bật quyền, gửi fake hoặc gửi thông báo từ app khác. " +
            "Sau đó khóa màn, vuốt app khỏi đa nhiệm và gửi thử lần nữa."
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val b = StringBuilder()
        for (i in 0 until data.length()) {
            val e = data.getJSONObject(i)
            b.append(fmt.format(Date(e.optLong("time"))))
                .append(" · ").append(e.optString("type"))
                .append("\n").append(e.optString("package"))
                .append(" · độ trễ khoảng ").append(e.optLong("latency_ms"))
                .append(" ms\n\n")
        }
        return b.toString()
    }
}
