package com.lonelychromosome.parkinglab

import android.content.Context
import android.location.Location
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TripStore {
    const val WAIT = "WAIT"
    const val TRACK = "TRACK"
    const val STOP = "STOP"
    const val SAVED = "SAVED"

    private fun p(c: Context) = c.getSharedPreferences("parking_trip_v3", Context.MODE_PRIVATE)
    private fun active(c: Context) = File(c.filesDir, "active_route.csv")
    private fun folder(c: Context) = File(c.filesDir, "trips").apply { mkdirs() }
    fun stage(c: Context): String = p(c).getString("stage", WAIT) ?: WAIT
    fun running(c: Context) = stage(c) == TRACK || stage(c) == STOP
    fun count(c: Context) = p(c).getInt("count", 0)
    fun metres(c: Context) = p(c).getFloat("metres", 0f)
    fun accuracy(c: Context) = p(c).getFloat("accuracy", -1f)
    fun lastFix(c: Context) = p(c).getLong("lastFix", 0L)
    fun message(c: Context) = p(c).getString("message", "") ?: ""
    private fun number(c: Context, key: String) = p(c).getString(key, null)?.toDoubleOrNull()
    fun latitude(c: Context) = number(c, "lat")
    fun longitude(c: Context) = number(c, "lon")
    fun parkLat(c: Context) = number(c, "parkLat")
    fun parkLon(c: Context) = number(c, "parkLon")
    fun hasSaved(c: Context) = parkLat(c) != null && parkLon(c) != null
    fun savedRoute(c: Context) = p(c).getString("savedRoute", null)
    fun savedDate(c: Context): String {
        val t = p(c).getLong("parkTime", 0L)
        return if (t == 0L) "" else SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(t))
    }

    @Synchronized fun begin(c: Context) {
        active(c).writeText("time,lat,lon,accuracy,speed,provider\n")
        p(c).edit().putString("stage", TRACK).putLong("start", System.currentTimeMillis())
            .putInt("count", 0).putFloat("metres", 0f)
            .remove("lat").remove("lon").remove("trackLat").remove("trackLon")
            .remove("trackMono").remove("fixMono").remove("stillLat").remove("stillLon")
            .remove("stillMono").remove("message").remove("lastFix").apply()
    }
    @Synchronized fun cancel(c: Context) {
        active(c).delete()
        p(c).edit().putString("stage", WAIT).remove("message").apply()
    }
    @Synchronized fun alert(c: Context, value: String) {
        p(c).edit().putString("message", value).apply()
    }
    private fun between(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, r)
        return r[0]
    }
    @Synchronized fun onFix(c: Context, loc: Location) {
        if (!running(c)) return
        if (!loc.hasAccuracy() || loc.accuracy > 35f || !loc.accuracy.isFinite()) {
            alert(c, "Đang chờ tín hiệu GPS chính xác (≤35m)")
            return
        }
        val mono = loc.elapsedRealtimeNanos / 1_000_000L
        val age = SystemClock.elapsedRealtime() - mono
        if (age > 12000 || age < -2000) return
        val oldLat = latitude(c)
        val oldLon = longitude(c)
        val lastMono = p(c).getLong("fixMono", 0L)
        val delta = if (oldLat == null || oldLon == null) 0f
            else between(oldLat, oldLon, loc.latitude, loc.longitude)
        if (lastMono > 0L) {
            if (mono <= lastMono) return
            val dt = ((mono - lastMono) / 1000f).coerceAtLeast(0.2f)
            if (delta / dt > 42f) return
        }
        val speed = if (loc.hasSpeed() && loc.speed.isFinite()) loc.speed.coerceAtLeast(0f)
            else if (lastMono > 0) delta / ((mono - lastMono) / 1000f).coerceAtLeast(0.2f)
            else 0f
        val pref = p(c)
        val edit = pref.edit().putString("lat", loc.latitude.toString())
            .putString("lon", loc.longitude.toString())
            .putLong("fixMono", mono).putLong("lastFix", System.currentTimeMillis())
            .putFloat("accuracy", loc.accuracy).remove("message")
        val tl = number(c, "trackLat")
        val tn = number(c, "trackLon")
        val tMono = pref.getLong("trackMono", 0L)
        val d = if (tl == null || tn == null) 0f
            else between(tl, tn, loc.latitude, loc.longitude)
        if (tl == null || d > 5f || mono - tMono > 8_000L) {
            try {
                active(c).appendText("${loc.time},${loc.latitude},${loc.longitude},${loc.accuracy},${speed},${loc.provider}\n")
            } catch (e: Exception) {
                alert(c, "Không ghi được hành trình: ${e.javaClass.simpleName}")
                return
            }
            edit.putInt("count", count(c) + 1)
                .putString("trackLat", loc.latitude.toString())
                .putString("trackLon", loc.longitude.toString())
                .putLong("trackMono", mono)
            if (d in 5f..90f && (speed > 1.4f || d > 12f))
                edit.putFloat("metres", metres(c) + d)
        }
        val stillLat = number(c, "stillLat")
        val stillLon = number(c, "stillLon")
        val stillMono = pref.getLong("stillMono", 0L)
        val fromAnchor = if (stillLat == null || stillLon == null) 0f
            else between(stillLat, stillLon, loc.latitude, loc.longitude)
        if (speed > 2.2f || (stillLat != null && fromAnchor > 14f)) {
            edit.putString("stillLat", loc.latitude.toString())
                .putString("stillLon", loc.longitude.toString())
                .putLong("stillMono", mono)
            if (stage(c) == STOP) edit.putString("stage", TRACK)
        } else if (stillLat == null || stillMono == 0L) {
            edit.putString("stillLat", loc.latitude.toString())
                .putString("stillLon", loc.longitude.toString())
                .putLong("stillMono", mono)
        } else if (stage(c) == TRACK && metres(c) >= 35f &&
            speed < 1.4f && mono - stillMono >= 25_000L) {
            edit.putString("stage", STOP)
        }
        edit.apply()
    }
    @Synchronized fun save(c: Context): Boolean {
        if (!running(c) || count(c) == 0) return false
        val lat = latitude(c) ?: return false
        val lon = longitude(c) ?: return false
        val to = File(folder(c), "trip_${p(c).getLong("start", System.currentTimeMillis())}.csv")
        try {
            active(c).copyTo(to, overwrite = true)
            if (to.length() <= 40) return false
        } catch (e: Exception) {
            alert(c, "Lưu thất bại: ${e.javaClass.simpleName}")
            return false
        }
        p(c).edit().putString("stage", SAVED)
            .putString("parkLat", lat.toString()).putString("parkLon", lon.toString())
            .putLong("parkTime", System.currentTimeMillis())
            .putString("savedRoute", to.absolutePath).remove("message").apply()
        active(c).delete()
        return true
    }
    fun gpx(c: Context): String? {
        val path = savedRoute(c) ?: return null
        val file = File(path)
        if (!file.exists() || !file.canonicalPath.startsWith(folder(c).canonicalPath + File.separator)) return null
        val points = StringBuilder()
        file.useLines { lines ->
            lines.drop(1).forEach { line ->
                val bits = line.split(",")
                val lat = bits.getOrNull(1)?.toDoubleOrNull()
                val lon = bits.getOrNull(2)?.toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0)
                    points.append("<trkpt lat=\"$lat\" lon=\"$lon\"/>\n")
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"BPA Parking Lab\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>$points</trkseg></trk></gpx>"
    }
}
