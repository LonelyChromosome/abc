package com.lonelychromosome.parkinglab

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * A full GPS breadcrumb log for each Find My Bike session.
 * Every accepted fix is appended to a private UTF-8 CSV file.
 * No rolling 10-second buffer and no sampling/truncation of accepted fixes.
 */
object RouteStore {
    data class Point(val timeMs: Long, val lat: Double, val lon: Double, val accuracy: Float)
    private const val PREF = "find_my_bike_routes"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "bike_routes").apply { mkdirs() }
    private fun routeFile(c: Context, name: String?): File? {
        if (name == null || !Regex("^route_[0-9]+\\.csv$").matches(name)) return null
        return File(dir(c), name)
    }

    @Synchronized
    fun begin(c: Context) {
        val name = "route_${System.currentTimeMillis()}.csv"
        val file = routeFile(c, name) ?: return
        file.writeText("time_ms,latitude,longitude,accuracy_m\n")
        prefs(c).edit().putString("active", name).putInt("count", 0).commit()
    }

    @Synchronized
    fun append(c: Context, timeMs: Long, lat: Double, lon: Double, acc: Float): Boolean {
        val p = prefs(c)
        val file = routeFile(c, p.getString("active", null)) ?: return false
        if (!file.exists() || !lat.isFinite() || !lon.isFinite() || !acc.isFinite()) return false
        return try {
            file.appendText(String.format(Locale.US, "%d,%.8f,%.8f,%.2f\n", timeMs, lat, lon, acc))
            p.edit().putInt("count", p.getInt("count", 0) + 1).apply()
            true
        } catch (_: Exception) { false }
    }

    fun activeCount(c: Context): Int = prefs(c).getInt("count", 0)
    fun savedCount(c: Context): Int = prefs(c).getInt("saved_count", 0)
    fun hasActive(c: Context): Boolean = routeFile(c, prefs(c).getString("active", null))?.exists() == true

    @Synchronized
    fun finish(c: Context) {
        val p = prefs(c)
        val name = p.getString("active", null) ?: return
        p.edit().putString("saved", name).putInt("saved_count", activeCount(c))
            .remove("active").remove("count").commit()
    }

    @Synchronized
    fun abandon(c: Context) {
        // Keep the CSV in private app storage even when the user cancels.
        // It can be inspected later; only remove the pointer for this session.
        prefs(c).edit().remove("active").remove("count").commit()
    }

    fun savedPoints(c: Context): List<Point> {
        val file = routeFile(c, prefs(c).getString("saved", null)) ?: return emptyList()
        if (!file.exists()) return emptyList()
        val result = ArrayList<Point>()
        try {
            file.bufferedReader().use { reader ->
                reader.readLine() // CSV heading
                reader.forEachLine { line ->
                    val fields = line.split(',')
                    if (fields.size == 4) {
                        val t = fields[0].toLongOrNull()
                        val lat = fields[1].toDoubleOrNull()
                        val lon = fields[2].toDoubleOrNull()
                        val acc = fields[3].toFloatOrNull()
                        if (t != null && lat != null && lon != null && acc != null &&
                            lat in -90.0..90.0 && lon in -180.0..180.0) {
                            result.add(Point(t, lat, lon, acc))
                        }
                    }
                }
            }
        } catch (_: Exception) { /* keep any valid points read so far */ }
        return result
    }
}
