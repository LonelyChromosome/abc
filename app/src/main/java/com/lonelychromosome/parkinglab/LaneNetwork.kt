package com.lonelychromosome.parkinglab

import android.content.Context
import org.json.JSONObject
import kotlin.math.*
import java.util.PriorityQueue

/** Preview graph digitized from the user's lane annotations. No Google routing API. */
data class GeoPoint(val lat: Double, val lon: Double) {
    fun distance(other: GeoPoint): Double {
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(other.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 12742017.6 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

class LaneNetwork private constructor(
    val lanes: List<List<GeoPoint>>,
    val simulatedStarts: List<Pair<String, GeoPoint>>,
    val estimatedLinks: List<Pair<GeoPoint, GeoPoint>>,
    val isDraft: Boolean,
    val pixelPerMeter: Double,
    private val vertices: List<GeoPoint>,
    private val adjacent: List<Map<Int, Double>>
) {
    data class Nearest(val index: Int, val distance: Double)
    data class Route(val points: List<GeoPoint>, val lengthMetres: Double)

    val vertexCount: Int get() = vertices.size
    val componentCount: Int by lazy {
        val seen = BooleanArray(vertices.size)
        var components = 0
        for (i in vertices.indices) {
            if (seen[i]) continue
            components++
            val queue = ArrayDeque<Int>()
            queue.add(i)
            seen[i] = true
            while (queue.isNotEmpty()) {
                val u = queue.removeFirst()
                adjacent[u].keys.forEach { v ->
                    if (!seen[v]) { seen[v] = true; queue.add(v) }
                }
            }
        }
        components
    }

    fun nearest(point: GeoPoint): Nearest? {
        if (vertices.isEmpty()) return null
        var best = 0
        var distance = Double.POSITIVE_INFINITY
        vertices.forEachIndexed { i, v ->
            val d = point.distance(v)
            if (d < distance) { distance = d; best = i }
        }
        return Nearest(best, distance)
    }

    /** A* on the independent lane graph. Returns null for disconnected roads or remote GPS. */
    fun route(from: GeoPoint, to: GeoPoint, maxOffroadMetres: Double = 75.0): Route? {
        val start = nearest(from) ?: return null
        val goal = nearest(to) ?: return null
        if (start.distance > maxOffroadMetres || goal.distance > maxOffroadMetres) return null
        val size = vertices.size
        val costs = DoubleArray(size) { Double.POSITIVE_INFINITY }
        val parents = IntArray(size) { -1 }
        val closed = BooleanArray(size)
        val open = PriorityQueue(compareBy<Pair<Int, Double>> { it.second })
        costs[start.index] = 0.0
        open.add(start.index to vertices[start.index].distance(vertices[goal.index]))
        while (open.isNotEmpty()) {
            val u = open.poll().first
            if (closed[u]) continue
            if (u == goal.index) break
            closed[u] = true
            adjacent[u].forEach { (v, weight) ->
                val cost = costs[u] + weight
                if (!closed[v] && cost < costs[v]) {
                    costs[v] = cost
                    parents[v] = u
                    open.add(v to (cost + vertices[v].distance(vertices[goal.index])))
                }
            }
        }
        if (!costs[goal.index].isFinite()) return null
        val ids = mutableListOf<Int>()
        var cursor = goal.index
        while (cursor >= 0) { ids.add(cursor); cursor = parents[cursor] }
        ids.reverse()
        return Route(ids.map { vertices[it] }, costs[goal.index])
    }

    companion object {
        fun load(context: Context): LaneNetwork {
            val json = JSONObject(context.assets.open("phenikaa_lanes_draft.json").bufferedReader().use { it.readText() })
            val anchorPx = json.getJSONArray("originPixel")
            val anchorGeo = json.getJSONArray("originLatLon")
            val originX = anchorPx.getDouble(0)
            val originY = anchorPx.getDouble(1)
            val lat0 = anchorGeo.getDouble(0)
            val lon0 = anchorGeo.getDouble(1)
            val scale = json.getDouble("pixelPerMeter")
            val metresPerLon = 111320.0 * cos(Math.toRadians(lat0))
            fun project(x: Double, y: Double): GeoPoint =
                GeoPoint(lat0 + (originY - y) / scale / 111320.0,
                         lon0 + (x - originX) / scale / metresPerLon)
            data class PixelNode(val x: Double, val y: Double, val point: GeoPoint)
            val nodes = mutableListOf<PixelNode>()
            val connections = mutableListOf<MutableMap<Int, Double>>()
            val inferredJunctions = mutableListOf<Pair<GeoPoint,GeoPoint>>()
            // Densify the user-drawn lanes; merge close junctions, not entire parking areas.
            fun node(x: Double, y: Double): Int {
                val found = nodes.indexOfFirst { hypot(it.x - x, it.y - y) <= 8.0 }
                if (found >= 0) {
                    // A small gap between independently drawn roads is a possible link,
                    // NOT a confirmed piece of road. Show this bridge as a dashed segment.
                    if (found != nodes.lastIndex && hypot(nodes[found].x-x, nodes[found].y-y) > 3.0) {
                        inferredJunctions.add(nodes[found].point to project(x,y))
                    }
                    return found
                }
                nodes.add(PixelNode(x, y, project(x, y)))
                connections.add(mutableMapOf())
                return nodes.lastIndex
            }
            fun connect(a: Int, b: Int) {
                if (a == b) return
                val length = nodes[a].point.distance(nodes[b].point)
                if (length < 0.2) return
                connections[a][b] = min(connections[a][b] ?: Double.POSITIVE_INFINITY, length)
                connections[b][a] = min(connections[b][a] ?: Double.POSITIVE_INFINITY, length)
            }
            val lanes = mutableListOf<List<GeoPoint>>()
            val roads = json.getJSONArray("roads")
            for (r in 0 until roads.length()) {
                val coordinates = roads.getJSONArray(r)
                val points = mutableListOf<Pair<Double, Double>>()
                for (k in 0 until coordinates.length()) {
                    val xy = coordinates.getJSONArray(k)
                    points.add(xy.getDouble(0) to xy.getDouble(1))
                }
                if (points.size < 2) continue
                lanes.add(points.map { project(it.first, it.second) })
                var lastNode = -1
                for (k in 0 until points.size - 1) {
                    val (ax, ay) = points[k]
                    val (bx, by) = points[k + 1]
                    val subdivisions = max(1, ceil(hypot(bx - ax, by - ay) / 10.0).toInt())
                    for (j in 0..subdivisions) {
                        if (k > 0 && j == 0) continue
                        val t = j.toDouble() / subdivisions
                        val next = node(ax + (bx - ax) * t, ay + (by - ay) * t)
                        if (lastNode >= 0) connect(lastNode, next)
                        lastNode = next
                    }
                }
            }
            val starts = json.optJSONArray("testStarts")?.let { entries ->
                (0 until entries.length()).map { k ->
                    val entry = entries.getJSONObject(k)
                    val pixel = entry.getJSONArray("pixel")
                    entry.getString("name") to project(pixel.getDouble(0), pixel.getDouble(1))
                }
            } ?: emptyList()
            return LaneNetwork(lanes, starts, inferredJunctions, true, scale,
                nodes.map { it.point }, connections.map { it.toMap() })
        }
    }
}
