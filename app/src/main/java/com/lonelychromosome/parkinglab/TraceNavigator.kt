package com.lonelychromosome.parkinglab

import android.content.Context
import java.io.File

/** The lane network brings the walker to the lane matched by the recorded arrival trace.
 *  The trace (not any predefined parking polygon) supplies the final approach to the bike.
 */
object TraceNavigator {
    data class BikeTrip(val bike: GeoPoint, val trace: List<GeoPoint>, val demo: Boolean)
    data class Plan(
        val lanePart: List<GeoPoint>,
        val approachPart: List<GeoPoint>,
        val walkToLane: List<GeoPoint>,
        val metres: Double,
        val laneMatchGap: Double,
        val traceEntryIndex: Int
    )

    fun savedTrip(context: Context): BikeTrip? {
        val lat = TripStore.parkLat(context) ?: return null
        val lon = TripStore.parkLon(context) ?: return null
        val bike = GeoPoint(lat, lon)
        val routeName = TripStore.savedRoute(context) ?: return BikeTrip(bike, emptyList(), false)
        val route = try { File(routeName).canonicalFile } catch (_: Exception) { return BikeTrip(bike, emptyList(), false) }
        val root = File(context.filesDir, "trips").canonicalFile
        if (!route.path.startsWith(root.path + File.separator) || !route.isFile) {
            return BikeTrip(bike, emptyList(), false)
        }
        val trace = try {
            route.readLines().drop(1).mapNotNull { line ->
                val fields = line.split(',')
                val a = fields.getOrNull(1)?.toDoubleOrNull()
                val b = fields.getOrNull(2)?.toDoubleOrNull()
                if (a != null && b != null && a in -90.0..90.0 && b in -180.0..180.0) GeoPoint(a,b)
                else null
            }
        } catch (_: Exception) { emptyList() }
        return BikeTrip(bike, trace, false)
    }

    /** Legacy reference example removed: real GPS tracking only. */
    fun referenceTrip(context: Context): BikeTrip {
        val points = context.assets.open("reference_trace_29.txt").bufferedReader().use { reader ->
            reader.readLines().mapNotNull { row ->
                val values = row.split(',')
                val lat = values.getOrNull(0)?.toDoubleOrNull()
                val lon = values.getOrNull(1)?.toDoubleOrNull()
                if (lat == null || lon == null) null else GeoPoint(lat,lon)
            }
        }
        return BikeTrip(GeoPoint(20.959505, 105.747637), points, true)
    }

    fun plan(network: LaneNetwork, user: GeoPoint, trip: BikeTrip): Plan? {
        val bike = trip.bike
        if (trip.trace.size < 2) {
            // Manual save or missing trace: only allow a short, visible last stretch to the bike.
            val nearest = network.nearest(bike) ?: return null
            if (nearest.distance > 15.0) return null
            val route = network.route(user, bike) ?: return null
            val last = route.points.lastOrNull() ?: return null
            return Plan(route.points, listOf(last, bike), listOf(user, route.points.first()),
                route.lengthMetres + nearest.distance, nearest.distance, -1)
        }
        val closestToBike = trip.trace.indices.minByOrNull { trip.trace[it].distance(bike) }
            ?: return null
        // Do not navigate along the pedestrian movement recorded *after* the bike stop.
        val cutoff = closestToBike
        var entry = -1
        for (i in cutoff downTo 0) {
            val gap = network.nearest(trip.trace[i])?.distance ?: continue
            // Deliberately enter the parking approach at least 18 m before the bike.
            // Matching the lane at the bike itself would skip the recorded final turn.
            if (gap <= 12.0 && trip.trace[i].distance(bike) >= 18.0) {
                entry = i
                break
            }
        }
        if (entry < 0) return null
        val first = trip.trace[entry]
        val gap = network.nearest(first)?.distance ?: return null
        if (gap > 12.0) return null
        val road = network.route(user, first) ?: return null
        val finalPart = mutableListOf<GeoPoint>()
        finalPart.add(road.points.last())
        if (first.distance(finalPart.last()) > 0.2) finalPart.add(first)
        for (i in entry+1..cutoff) finalPart.add(trip.trace[i])
        if (finalPart.last().distance(bike) > 0.2) finalPart.add(bike)
        val approachMetres = finalPart.zipWithNext().sumOf { (a,b) -> a.distance(b) }
        val firstLane = road.points.first()
        return Plan(road.points, finalPart, listOf(user, firstLane),
            user.distance(firstLane) + road.lengthMetres + approachMetres, gap, entry)
    }
}
