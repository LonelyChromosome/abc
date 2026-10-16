package com.lonelychromosome.parkinglab

import android.content.Context
import android.location.Location
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** GPS route and last motorbike stop are kept distinct from the subsequent walking route. */
object TripStore {
    const val WAIT = "WAIT"
    const val TRACK = "TRACK"
    const val STOP = "STOP"
    const val SAVED = "SAVED"

    private fun p(c: Context) = c.getSharedPreferences("parking_trip_v5", Context.MODE_PRIVATE)
    private fun active(c: Context) = File(c.filesDir, "active_route_v5.csv")
    private fun folder(c: Context) = File(c.filesDir, "trips").apply { mkdirs() }
    private fun num(c: Context, key: String) = p(c).getString(key, null)?.toDoubleOrNull()
    fun stage(c: Context) = p(c).getString("stage", WAIT) ?: WAIT
    fun running(c: Context) = stage(c) == TRACK || stage(c) == STOP
    fun count(c: Context) = p(c).getInt("count", 0)
    fun metres(c: Context) = p(c).getFloat("metres", 0f)
    fun accuracy(c: Context) = p(c).getFloat("accuracy", -1f)
    fun lastFix(c: Context) = p(c).getLong("lastFix", 0L)
    fun message(c: Context) = p(c).getString("message", "") ?: ""
    fun latitude(c: Context) = num(c, "lat")
    fun longitude(c: Context) = num(c, "lon")
    fun parkLat(c: Context) = num(c, "parkLat")
    fun parkLon(c: Context) = num(c, "parkLon")
    fun hasSaved(c: Context) = parkLat(c) != null && parkLon(c) != null
    fun savedRoute(c: Context) = p(c).getString("savedRoute", null)
    fun autoSaved(c: Context) = p(c).getBoolean("autoSaved", false)
    fun stepCount(c: Context) = p(c).getInt("steps", 0)
    fun walkElapsed(c: Context): Long {
        val start = p(c).getLong("walkStartMono", 0L)
        return if (start == 0L) 0L else ((SystemClock.elapsedRealtime() - start) / 1000L).coerceAtLeast(0L)
    }
    fun parkTime(c:Context)=p(c).getLong("parkTime",0L)
    fun savedDate(c: Context): String {
        val t = p(c).getLong("parkTime", 0L)
        return if (t == 0L) "" else SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(t))
    }
    @Synchronized fun begin(c: Context) {
        active(c).writeText("time,lat,lon,accuracy,speed,provider\n")
        p(c).edit().putString("stage", TRACK).putLong("start", System.currentTimeMillis())
            .putInt("count", 0).putInt("steps", 0).putFloat("metres", 0f)
            .remove("lat").remove("lon").remove("trackLat").remove("trackLon")
            .remove("trackMono").remove("fixMono").remove("stillLat").remove("stillLon")
            .remove("stillMono").remove("parkCandidateLat").remove("parkCandidateLon")
            .remove("parkCandidateWall").remove("walkStartMono").remove("walkStartWall")
            .remove("stepLastMono").remove("message").remove("lastFix").apply()
    }
    @Synchronized fun cancel(c: Context) {
        active(c).delete()
        p(c).edit().putString("stage", WAIT).remove("message").apply()
    }
    @Synchronized fun alert(c: Context, value: String) {
        p(c).edit().putString("message", value).apply()
    }
    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0]
    }

    @Synchronized fun onFix(c: Context, loc: Location) {
        if (!running(c)) return
        if (!loc.hasAccuracy() || loc.accuracy > 35f || !loc.accuracy.isFinite()) {
            alert(c, "Đang chờ GPS đủ chính xác (≤35 m)")
            return
        }
        val mono = loc.elapsedRealtimeNanos / 1_000_000L
        val age = SystemClock.elapsedRealtime() - mono
        if (age > 12_000L || age < -2_000L) return
        val oldLat = latitude(c)
        val oldLon = longitude(c)
        val lastMono = p(c).getLong("fixMono", 0L)
        val delta = if (oldLat == null || oldLon == null) 0f
            else distance(oldLat, oldLon, loc.latitude, loc.longitude)
        if (lastMono > 0L) {
            if (mono <= lastMono) return
            val dt = ((mono - lastMono) / 1000f).coerceAtLeast(0.2f)
            if (delta / dt > 42f) return
        }
        val speed = if (loc.hasSpeed() && loc.speed.isFinite()) loc.speed.coerceAtLeast(0f)
            else if (lastMono > 0L) delta / ((mono - lastMono) / 1000f).coerceAtLeast(0.2f)
            else 0f
        val prefs = p(c)
        val edit = prefs.edit()
            .putString("lat", loc.latitude.toString())
            .putString("lon", loc.longitude.toString())
            .putLong("fixMono", mono)
            .putLong("lastFix", System.currentTimeMillis())
            .putFloat("accuracy", loc.accuracy)
            .remove("message")

        val trackLat = num(c, "trackLat")
        val trackLon = num(c, "trackLon")
        val trackMono = prefs.getLong("trackMono", 0L)
        val d = if (trackLat == null || trackLon == null) 0f
            else distance(trackLat, trackLon, loc.latitude, loc.longitude)
        if (trackLat == null || d >= 5f || mono - trackMono >= 8_000L) {
            try {
                active(c).appendText(String.format(Locale.US,
                    "%d,%.8f,%.8f,%.2f,%.2f,%s\n",
                    loc.time, loc.latitude, loc.longitude, loc.accuracy, speed, loc.provider ?: "gps"))
            } catch (ex: Exception) {
                alert(c, "Không lưu GPS: " + ex.javaClass.simpleName)
                return
            }
            edit.putInt("count", count(c) + 1)
                .putString("trackLat", loc.latitude.toString())
                .putString("trackLon", loc.longitude.toString())
                .putLong("trackMono", mono)
            if (d in 5f..90f && (speed >= 1.4f || d >= 12f)) {
                edit.putFloat("metres", metres(c) + d)
            }
        }

        val stillLat = num(c, "stillLat")
        val stillLon = num(c, "stillLon")
        val stillStart = prefs.getLong("stillMono", 0L)
        val movedFromStill = if (stillLat == null || stillLon == null) 0f
            else distance(stillLat, stillLon, loc.latitude, loc.longitude)

        if (stage(c) == TRACK) {
            if (speed > 2.0f || (stillLat != null && movedFromStill > 12f)) {
                edit.putString("stillLat", loc.latitude.toString())
                    .putString("stillLon", loc.longitude.toString())
                    .putLong("stillMono", mono)
            } else if (stillLat == null || stillStart == 0L) {
                edit.putString("stillLat", loc.latitude.toString())
                    .putString("stillLon", loc.longitude.toString())
                    .putLong("stillMono", mono)
            } else if (metres(c) >= 35f && speed < 1.4f &&
                mono - stillStart >= 6_000L) {
                // Freeze the bike-stop candidate BEFORE the user walks away.
                edit.putString("stage", STOP)
                    .putString("parkCandidateLat", stillLat.toString())
                    .putString("parkCandidateLon", stillLon.toString())
                    .putLong("parkCandidateWall", System.currentTimeMillis())
                    .putInt("steps", 0)
                    .remove("walkStartMono").remove("walkStartWall").remove("stepLastMono")
            }
        } else {
            // Movement of a motorcycle is much faster than walking.
            // Do NOT cancel a stop just because the pedestrian leaves the candidate by 15m.
            val stepLast = prefs.getLong("stepLastMono", 0L)
            if (speed > 5f && (stepLast == 0L || mono - stepLast > 4_000L)) {
                edit.putString("stage", TRACK).putString("stillLat", loc.latitude.toString())
                    .putString("stillLon", loc.longitude.toString())
                    .putLong("stillMono", mono)
                    .remove("parkCandidateLat").remove("parkCandidateLon")
                    .remove("parkCandidateWall").remove("walkStartMono")
                    .remove("walkStartWall").remove("stepLastMono").putInt("steps", 0)
            }
        }
        edit.apply()
    }

    /** Called once per TYPE_STEP_DETECTOR event (not by a 1-second software timer). */
    @Synchronized fun onStep(c: Context, sensorMonoMs: Long): Boolean {
        if (stage(c) != STOP) return false
        val prefs = p(c)
        val previous = prefs.getLong("stepLastMono", 0L)
        val start = prefs.getLong("walkStartMono", 0L)
        val fresh = previous == 0L || sensorMonoMs < previous || sensorMonoMs - previous > 3_500L
        val first = if (fresh) sensorMonoMs else if (start > 0L) start else sensorMonoMs
        val steps = if (fresh) 1 else prefs.getInt("steps", 0) + 1
        prefs.edit().putLong("stepLastMono", sensorMonoMs)
            .putLong("walkStartMono", first)
            .putLong("walkStartWall", System.currentTimeMillis() - (sensorMonoMs - first))
            .putInt("steps", steps).apply()
        // At least 10 actual seconds of consecutive step events + 8 footsteps.
        return sensorMonoMs - first >= 10_000L && steps >= 8 && save(c, true)
    }

    /** Reverse-scan the recorded stop segment to exclude any subsequently walked GPS fixes. */
    private fun inferParkedCoordinate(c: Context): Pair<Double, Double>? {
        val prefs = p(c)
        val candidateLat = num(c, "parkCandidateLat") ?: return null
        val candidateLon = num(c, "parkCandidateLon") ?: return null
        val walkWall = prefs.getLong("walkStartWall", Long.MAX_VALUE)
        val end = if (walkWall > 0) walkWall else Long.MAX_VALUE
        val data = try { active(c).readLines() } catch (_: Exception) { emptyList() }
        val nearby = ArrayList<Pair<Double, Double>>()
        for (line in data.drop(1).asReversed()) {
            val fields = line.split(',')
            if (fields.size < 5) continue
            val time = fields[0].toLongOrNull() ?: continue
            val lat = fields[1].toDoubleOrNull() ?: continue
            val lon = fields[2].toDoubleOrNull() ?: continue
            val speed = fields[4].toFloatOrNull() ?: continue
            if (time > end) continue
            // Prefer the low-speed cluster immediately before walking; reject far points.
            if (speed <= 1.7f && distance(candidateLat, candidateLon, lat, lon) <= 18f) {
                nearby.add(lat to lon)
                if (nearby.size >= 9) break
            } else if (nearby.isNotEmpty()) break
        }
        if (nearby.size < 2) return candidateLat to candidateLon
        val lat = nearby.map { it.first }.sorted()[nearby.size / 2]
        val lon = nearby.map { it.second }.sorted()[nearby.size / 2]
        return lat to lon
    }

    @Synchronized fun save(c: Context, automatic: Boolean = false): Boolean {
        if (!running(c) || count(c) == 0) return false
        val location = if (automatic) inferParkedCoordinate(c) else
            (latitude(c)?.let { a -> longitude(c)?.let { b -> a to b } })
        val lat = location?.first ?: return false
        val lon = location.second
        val dest = File(folder(c), "trip_" + p(c).getLong("start", System.currentTimeMillis()) + ".csv")
        try {
            active(c).copyTo(dest, overwrite = true)
            if (dest.length() <= 40) return false
        } catch (e: Exception) {
            alert(c, "Không lưu được hành trình: " + e.javaClass.simpleName)
            return false
        }
        p(c).edit().putString("stage", SAVED)
            .putString("parkLat", lat.toString()).putString("parkLon", lon.toString())
            .putLong("parkTime", System.currentTimeMillis())
            .putString("savedRoute", dest.absolutePath)
            .putBoolean("autoSaved", automatic).remove("message").apply()
        active(c).delete()
        FieldStatus.tracked(c,accuracy(c))
        return true
    }

    fun gpx(c: Context): String? {
        val path = savedRoute(c) ?: return null
        val file = File(path)
        if (!file.exists() || !file.canonicalPath.startsWith(folder(c).canonicalPath + File.separator)) return null
        val buffer = StringBuilder()
        file.useLines { lines ->
            lines.drop(1).forEach { row ->
                val x = row.split(',')
                val lat = x.getOrNull(1)?.toDoubleOrNull()
                val lon = x.getOrNull(2)?.toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    buffer.append("<trkpt lat=\"").append(lat)
                        .append("\" lon=\"").append(lon).append("\"/>\n")
                }
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<gpx version=\"1.1\" creator=\"BPA Parking Lab\" xmlns=\"http://www.topografix.com/GPX/1/1\">" +
            "<trk><trkseg>" + buffer + "</trkseg></trk></gpx>"
    }
}
