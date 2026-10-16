package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.*

/** Native Find Bike. Google supplies only the basemap, every route is computed locally. */
class FindBikeActivity : Activity(), OnMapReadyCallback, LocationListener {
    private lateinit var network: LaneNetwork
    private lateinit var mapContainer: FrameLayout
    private lateinit var info: TextView
    private lateinit var action: Button
    private lateinit var dataButton: Button
    private var googleMapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private var preview: LanePreviewView? = null
    private var trip: TraceNavigator.BikeTrip? = null
    private var position: GeoPoint? = null
    private var plan: TraceNavigator.Plan? = null
    private var navigating = false
    private var fitted = false
    private val dynamicOverlays = mutableListOf<Polyline>()

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private fun latLng(p: GeoPoint) = LatLng(p.lat, p.lon)
    private fun control(label: String, color: Int, click: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { click() }
        }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        network = LaneNetwork.load(this)
        trip = TraceNavigator.savedTrip(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 18, 30))
        }
        val header = TextView(this).apply {
            text = "FIND BIKE  /  CAMPUS LANES"
            textSize = 17f
            setTextColor(Color.rgb(113, 246, 208))
            setPadding(dp(15), dp(14), dp(12), dp(12))
        }
        root.addView(header)
        mapContainer = FrameLayout(this)
        root.addView(mapContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        info = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(236, 241, 250))
            setPadding(dp(16), dp(12), dp(16), dp(10))
        }
        root.addView(info)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(7), 0, dp(7), dp(12))
        }
        action = control("TÌM ĐƯỜNG ĐẾN XE", Color.rgb(10, 110, 96)) {
            navigating = true
            refresh()
        }
        dataButton = control("THỬ GPX 29 ĐIỂM", Color.rgb(53, 69, 92)) {
            trip = TraceNavigator.referenceTrip(this)
            navigating = false
            plan = null
            renderOverlays()
            refresh()
        }
        controls.addView(action, LinearLayout.LayoutParams(0, dp(52), 1.25f))
        controls.addView(dataButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(controls)
        setContentView(root)

        val key = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("com.google.android.geo.API_KEY")
        } catch (_: Exception) { null }
        if (key.isNullOrBlank() || key == "NO_KEY") {
            // An honest geometry-only diagnostic, not an invented Google basemap.
            preview = LanePreviewView(this, network)
            mapContainer.addView(preview, FrameLayout.LayoutParams(-1, -1))
        } else {
            googleMapView = MapView(this)
            mapContainer.addView(googleMapView, FrameLayout.LayoutParams(-1, -1))
            googleMapView?.onCreate(state)
            googleMapView?.getMapAsync(this)
        }
        refresh()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        renderOverlays()
        fitCampus()
        refresh()
    }

    private fun fitCampus() {
        val map = googleMap ?: return
        if (fitted) return
        val builder = LatLngBounds.Builder()
        network.lanes.forEach { lane -> lane.forEach { builder.include(latLng(it)) } }
        val currentTrip = trip
        if (currentTrip != null) builder.include(latLng(currentTrip.bike))
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), dp(32)))
            fitted = true
        } catch (_: Exception) {
            // Map dimensions may not be ready on the first callback.
            val car = currentTrip?.bike ?: GeoPoint(20.9600, 105.7480)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng(car), 16.8f))
        }
    }

    private fun renderOverlays() {
        val map = googleMap
        if (map != null) {
            map.clear()
            dynamicOverlays.clear()
            // User-authored lane lines, rendered as geodetic polylines (not Google routing).
            network.lanes.forEach { road ->
                if (road.size >= 2) {
                    map.addPolyline(PolylineOptions().addAll(road.map(::latLng))
                        .color(Color.rgb(0, 155, 185)).width(4f).zIndex(1f))
                }
            }
            val currentTrip = trip
            if (currentTrip != null) {
                map.addMarker(MarkerOptions().position(latLng(currentTrip.bike))
                    .title(if (currentTrip.demo) "Xe mẫu • GPX kiểm thử" else "Vị trí xe đã lưu")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                if (currentTrip.trace.size > 1) {
                    map.addPolyline(PolylineOptions().addAll(currentTrip.trace.map(::latLng))
                        .color(Color.rgb(66, 110, 250)).width(4f).zIndex(2f))
                }
            }
            position?.let {
                map.addMarker(MarkerOptions().position(latLng(it)).title("Vị trí hiện tại")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
            }
            plan?.let { p ->
                if (p.lanePart.size > 1) dynamicOverlays.add(map.addPolyline(
                    PolylineOptions().addAll(p.lanePart.map(::latLng))
                        .color(Color.rgb(23, 183, 107)).width(9f).zIndex(5f)))
                if (p.approachPart.size > 1) dynamicOverlays.add(map.addPolyline(
                    PolylineOptions().addAll(p.approachPart.map(::latLng))
                        .color(Color.rgb(255, 147, 24)).width(8f).zIndex(6f)))
            }
        }
        preview?.update(trip, position, plan)
    }

    private fun refresh() {
        val currentTrip = trip
        val start = position ?: if (currentTrip?.demo == true) currentTrip.trace.firstOrNull() else null
        plan = if (navigating && start != null && currentTrip != null)
            TraceNavigator.plan(network, start, currentTrip) else null
        info.text = buildString {
            append("MẠNG LANE NHÁP · ")
            append(network.lanes.size).append(" đoạn · ")
            append(network.vertexCount).append(" nút · ")
            append(network.componentCount).append(" nhóm kết nối")
            append("\nTỷ lệ từ GPX: hiệu chỉnh thử, CHƯA xác minh địa lý toàn trường.")
            if (googleMapView == null) append("\nChưa có Google Maps API key: đang xem sơ đồ lane không nền.")
            if (currentTrip == null) append("\nChưa lưu xe. Có thể bấm THỬ GPX 29 ĐIỂM.")
            else {
                append(if (currentTrip.demo) "\nCHẾ ĐỘ THỬ NGHIỆM (không phải xe hiện tại)." else "\nĐang dùng xe và trace đã lưu của BPA.")
                if (start == null) append("\nBật quyền vị trí để tìm đường từ vị trí hiện tại.")
                if (navigating) {
                    val route = plan
                    if (route == null) append("\nChưa ghép được lane và trace: không vẽ đường xuyên công trình.")
                    else append("\nDự kiến ").append(route.metres.roundToInt())
                        .append(" m · khớp lane tại điểm GPX #").append(route.traceEntryIndex + 1)
                        .append(" · cách lane ").append(route.laneMatchGap.roundToInt()).append(" m.")
                }
            }
        }
        renderOverlays()
    }

    override fun onStart() { super.onStart(); googleMapView?.onStart() }
    override fun onResume() {
        super.onResume()
        googleMapView?.onResume()
        startLocation()
    }
    private fun startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 47)
            return
        }
        try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            (manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.let { onLocationChanged(it) }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 3f, this)
        } catch (_: Exception) { }
    }
    override fun onRequestPermissionsResult(code: Int, names: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, names, results)
        if (code == 47 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startLocation()
        else refresh()
    }
    override fun onLocationChanged(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > 40f) return
        position = GeoPoint(location.latitude, location.longitude)
        refresh()
    }
    override fun onPause() {
        try { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (_: Exception) {}
        googleMapView?.onPause()
        super.onPause()
    }
    override fun onStop() { googleMapView?.onStop(); super.onStop() }
    override fun onDestroy() { googleMapView?.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); googleMapView?.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) { googleMapView?.onSaveInstanceState(out); super.onSaveInstanceState(out) }
}

/** Preview fallback: geodetically scaled lane geometry only. Never pretends to be Google Maps. */
private class LanePreviewView(context: Context, private val network: LaneNetwork) : View(context) {
    private var trip: TraceNavigator.BikeTrip? = null
    private var user: GeoPoint? = null
    private var route: TraceNavigator.Plan? = null
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    fun update(t: TraceNavigator.BikeTrip?, u: GeoPoint?, p: TraceNavigator.Plan?) {
        trip = t; user = u; route = p; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(13, 25, 39))
        val points = network.lanes.flatten()
        if (points.isEmpty()) return
        val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }; val maxLon = points.maxOf { it.lon }
        val reference = (minLat + maxLat) / 2
        val correctedWidth = (maxLon - minLon) * cos(Math.toRadians(reference))
        val spanY = (maxLat - minLat).coerceAtLeast(0.00001)
        val margin = 24f * resources.displayMetrics.density
        val scale = min((width - 2 * margin) / correctedWidth.toFloat().coerceAtLeast(0.00001f),
            (height - 2 * margin) / spanY.toFloat())
        val offsetX = (width - correctedWidth * scale) / 2
        val offsetY = (height - spanY * scale) / 2
        fun x(p: GeoPoint) = (offsetX + (p.lon - minLon) * cos(Math.toRadians(reference)) * scale).toFloat()
        fun y(p: GeoPoint) = (offsetY + (maxLat - p.lat) * scale).toFloat()
        fun drawLine(points: List<GeoPoint>, color: Int, stroke: Float) {
            brush.color = color; brush.style = Paint.Style.STROKE; brush.strokeWidth = stroke
            points.zipWithNext { a,b -> canvas.drawLine(x(a), y(a), x(b), y(b), brush) }
        }
        network.lanes.forEach { drawLine(it, Color.rgb(72, 138, 163), 3f) }
        trip?.trace?.let { drawLine(it, Color.rgb(58, 117, 255), 3f) }
        route?.lanePart?.let { drawLine(it, Color.rgb(12, 222, 148), 6f) }
        route?.approachPart?.let { drawLine(it, Color.rgb(254, 172, 66), 6f) }
        fun dot(p: GeoPoint?, color: Int, radius: Float) {
            if (p == null) return
            brush.color = color; brush.style = Paint.Style.FILL
            canvas.drawCircle(x(p), y(p), radius, brush)
        }
        dot(trip?.bike, Color.RED, 8f)
        dot(user, Color.CYAN, 7f)
    }
}
