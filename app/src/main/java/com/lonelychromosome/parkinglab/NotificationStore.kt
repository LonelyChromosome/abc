package com.lonelychromosome.parkinglab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only counts and timings. No external notification content or account data stored. */
object NotificationStore {
    private fun prefs(c: Context) = c.getSharedPreferences("listener_test", Context.MODE_PRIVATE)
    fun connected(c: Context): Boolean = prefs(c).getBoolean("connected", false)
    fun setConnected(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("connected", value).apply()
    }
    fun ownCount(c: Context): Int = prefs(c).getInt("fake_count", 0)
    fun externalCount(c: Context): Int = prefs(c).getInt("external_count", 0)
    fun phenikaaCount(c: Context): Int = prefs(c).getInt("phenikaa_count", 0)
    fun matchedCount(c: Context): Int = prefs(c).getInt("phenikaa_matched_count", 0)

    fun notePhenikaa(c: Context, matches: Boolean) {
        val p = prefs(c)
        val change = p.edit().putInt("phenikaa_count", phenikaaCount(c) + 1)
            .putLong("last_phenikaa_time", System.currentTimeMillis())
        if (matches) change.putInt("phenikaa_matched_count", matchedCount(c) + 1)
        change.commit()
    }

    fun record(c: Context, pkg: String, isFake: Boolean, postedAt: Long) {
        val p = prefs(c)
        val key = if (isFake) "fake_count" else "external_count"
        val previous = try { JSONArray(p.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        val now = System.currentTimeMillis()
        val item = JSONObject().put("time", now).put("package", pkg)
            .put("type", if (isFake) "FAKE" else "EXTERNAL")
            .put("latency_ms", (now - postedAt).coerceAtLeast(0))
        val next = JSONArray().put(item)
        for (i in 0 until minOf(previous.length(), 11)) next.put(previous.get(i))
        p.edit().putInt(key, p.getInt(key, 0) + 1)
            .putString("history", next.toString()).commit()
    }

    fun recentText(c: Context): String {
        val data = try { JSONArray(prefs(c).getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        if (data.length() == 0) return "Chưa nhận thông báo nào."
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val b = StringBuilder()
        for (i in 0 until data.length()) {
            val e = data.getJSONObject(i)
            b.append(fmt.format(Date(e.optLong("time"))))
                .append(" · ").append(e.optString("type"))
                .append("\n").append(e.optString("package"))
                .append(" · delay ").append(e.optLong("latency_ms")).append(" ms\n\n")
        }
        return b.toString()
    }
}
