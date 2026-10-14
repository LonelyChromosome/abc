package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** User-started location FGS; NOTIFICATION ACCESS never starts this service in the background. */
class BikeTrackingService : Service(), SensorEventListener {
    companion object {
        const val START = "bike.START"
        const val START_SCHEDULED = "bike.START_SCHEDULED"
        const val GATE = "bike.GATE"
        const val MANUAL = "bike.MANUAL"
        const val SAVE = "bike.SAVE"
        const val CANCEL = "bike.CANCEL"
        private const val TRACK_CHANNEL = "find_my_bike_tracking"
        private const val ALERT_CHANNEL = "find_my_bike_action"
        private const val FGS_ID = 3001
        private const val MANUAL_ID = 3002
        private const val SAVED_ID = 3003
        private const val WINDOW_MS = 10_000L
    }

    private data class Fix(val latitude: Double, val longitude: Double,
        val accuracy: Float, val atElapsed: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private var receivingLocations = false
    private var registeredSteps = false
    private var lastLocation: Location? = null
    private var stopCandidate: Fix? = null
    private var stationarySince = 0L
    private var walkingSteps = 0
    private var hasForeground = false
    private var newestFixAt = 0L
    private var latestPhase = "Đang tìm vị trí"
    private var writeError = false
    private var source = "Đang chờ xác nhận xe vào"

    private val statusTicker: Runnable = object : Runnable {
        override fun run() {
            if (!hasForeground) return
            updateStatusNotification()
            handler.postDelayed(this, 7_000L)
        }
    }

    private val timeout: Runnable = Runnable {
        if (FindMyBikeStore.mode(this) != FindMyBikeStore.WAITING) return@Runnable
        if (FindMyBikeStore.remainingWaitMs(this) > 0L) {
            handler.postDelayed(timeout, FindMyBikeStore.remainingWaitMs(this))
            return@Runnable
        }
        FindMyBikeStore.needManual(this)
        ScheduledGateController.cancelExpire(this)
        BikeProgressNotification.event(this, "HẾT GIỜ · CHƯA NHẬN XE VÀO",
            "Không có thông báo quẹt thẻ hợp lệ",
            "Không bật GPS. Mở BPA để bật theo dõi thủ công.")
        getSystemService(NotificationManager::class.java).notify(MANUAL_ID,
            alert("Chưa nhận thông báo vào cổng",
                "Bấm để đánh dấu thủ công và bắt đầu theo dõi xe.", true))
        endWithoutSaving(false)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) inspectLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            TRACK_CHANNEL, "Find My Bike đang hoạt động", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(
            ALERT_CHANNEL, "Find My Bike cần thao tác", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            START -> {
                if (FindMyBikeStore.mode(this) == FindMyBikeStore.TRACKING) {
                    showForeground("Đang theo dõi vị trí xe")
                    ensureLocation()
                    return START_STICKY
                }
                FindMyBikeStore.startWaiting(this)
                showForeground("Chờ Phenikaa Uni · 10 giây")
                scheduleWaitingTimeout()
                return START_STICKY
            }
            START_SCHEDULED -> {
                if (!FindMyBikeStore.withinWindow(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    showForeground("Lịch đã tới · đang chờ thông báo quẹt thẻ")
                    scheduleWaitingTimeout()
                    return START_STICKY
                } catch (e: Exception) {
                    FindMyBikeStore.needManual(this)
                    ScheduledGateController.setEvent(this,
                        "Android từ chối location service: " + e.javaClass.simpleName)
                    BikeProgressNotification.event(this, "LỖI · ANDROID CHẶN SERVICE",
                        "Đến giờ nhưng không bật được bộ chờ",
                        "Lỗi " + e.javaClass.simpleName + " · mở app để xử lý")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            GATE -> {
                // FCM notifications can arrive late; never replace a manual session.
                if (FindMyBikeStore.withinWindow(this) && hasForeground) {
                    handler.removeCallbacks(timeout)
                    ScheduledGateController.cancelExpire(this)
                    source = "Đã nhận thông báo xe vào"
                    showForeground("Đã nhận tín hiệu vào cổng · đang theo dõi")
                    beginTracking()
                    updateStatusNotification()
                }
            }
            MANUAL -> {
                if (FindMyBikeStore.mode(this) == FindMyBikeStore.NEEDS_MANUAL ||
                    FindMyBikeStore.mode(this) == FindMyBikeStore.WAITING) {
                    handler.removeCallbacks(timeout)
                    ScheduledGateController.cancelExpire(this)
                    source = "Theo dõi kích hoạt thủ công"
                    showForeground("Theo dõi vị trí · kích hoạt thủ công")
                    beginTracking()
                    updateStatusNotification()
                    getSystemService(NotificationManager::class.java).cancel(MANUAL_ID)
                }
            }
            SAVE -> {
                if (FindMyBikeStore.mode(this) == FindMyBikeStore.TRACKING) {
                    val fix = FindMyBikeStore.lastFix(this)?.let {
                        Fix(it.first, it.second, it.third, SystemClock.elapsedRealtime())
                    }
                    if (fix != null) saveFix(fix, "Thủ công")
                    else showForeground("Chưa có tín hiệu GPS · hãy chờ rồi lưu lại")
                }
            }
            CANCEL -> {
                RouteStore.abandon(this)
                ScheduledGateController.cancelExpire(this)
                FindMyBikeStore.reset(this)
                endWithoutSaving(true)
                BikeProgressNotification.event(this, "ĐÃ HỦY PHIÊN FIND MY BIKE",
                    "Đã dừng bộ chờ và tracking",
                    "GPS TẮT · không có điểm đỗ mới được xác nhận.")
                return START_NOT_STICKY
            }
            else -> {
                // Android may recreate an active foreground service after process death.
                if (FindMyBikeStore.mode(this) == FindMyBikeStore.TRACKING) {
                    showForeground("Đang khôi phục theo dõi")
                    ensureLocation()
                } else if (FindMyBikeStore.mode(this) == FindMyBikeStore.WAITING) {
                    if (FindMyBikeStore.remainingWaitMs(this) <= 0L) {
                        FindMyBikeStore.needManual(this)
                        stopSelf()
                    } else {
                        showForeground("Chờ Phenikaa Uni")
                        scheduleWaitingTimeout()
                    }
                } else {
                    stopSelf()
                }
            }
        }
        return if (FindMyBikeStore.mode(this) == FindMyBikeStore.TRACKING ||
            FindMyBikeStore.mode(this) == FindMyBikeStore.WAITING) START_STICKY
            else START_NOT_STICKY
    }

    private fun showForeground(description: String) {
        // Dismiss the pending-alarm card only after startForeground succeeds.
        val waiting = FindMyBikeStore.mode(this) == FindMyBikeStore.WAITING
        val notification = BikeProgressNotification.foreground(this,
            if (waiting) "2/4 · CHỜ QUẸT THẺ" else "3/4 · ĐANG XÁC ĐỊNH XE",
            description,
            if (waiting) "GPS TẮT · đang chờ thông báo xe vào"
            else "Đang kết nối GPS và phân tích chuyển động")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FGS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else startForeground(FGS_ID, notification)
        hasForeground = true
        FindMyBikeStore.setServiceReady(this, true)
        BikeProgressNotification.clearScheduled(this)
        handler.removeCallbacks(statusTicker)
        handler.postDelayed(statusTicker, 7_000L)
    }

    private fun updateStatusNotification() {
        if (!hasForeground) return
        if (FindMyBikeStore.mode(this) == FindMyBikeStore.WAITING) {
            val left = (FindMyBikeStore.remainingWaitMs(this) + 999L) / 1000L
            BikeProgressNotification.updateForeground(this, "2/4 · CHỜ QUẸT THẺ",
                "Còn " + left + " giây · GPS TẮT",
                "Listener đang chờ Phenikaa Uni. Hết hạn sẽ báo lại, không tự bật GPS.")
            return
        }
        if (FindMyBikeStore.mode(this) != FindMyBikeStore.TRACKING) return
        val elapsed = if (newestFixAt > 0L)
            (SystemClock.elapsedRealtime() - newestFixAt).coerceAtLeast(0L) / 1000L else -1L
        val condition = when {
            writeError -> "LỖI GHI HÀNH TRÌNH"
            !receivingLocations -> "GPS CHƯA KẾT NỐI"
            elapsed == -1L -> "GPS ĐANG TÌM VỊ TRÍ"
            elapsed > 15L -> "GPS ĐÃ " + elapsed + " GIÂY CHƯA CÓ ĐIỂM MỚI"
            else -> "GPS CẬP NHẬT " + elapsed + " GIÂY TRƯỚC"
        }
        val accuracy = if (lastLocation?.hasAccuracy() == true)
            " · ±" + lastLocation!!.accuracy.toInt() + "m" else ""
        val points = RouteStore.activeCount(this)
        BikeProgressNotification.updateForeground(this, "3/4 · ĐANG XÁC ĐỊNH XE",
            condition + accuracy + " · " + points + " điểm",
            source + " · " + latestPhase + ". Đang lưu toàn bộ hành trình.")
    }

    private fun scheduleWaitingTimeout() {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, FindMyBikeStore.remainingWaitMs(this).coerceAtLeast(0L))
    }

    private fun openApp(request: Int, manual: Boolean): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (manual) putExtra(MainActivity.EXTRA_MANUAL_PROMPT, true)
        }
        return PendingIntent.getActivity(this, request, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun alert(title: String, message: String, manual: Boolean): Notification =
        Notification.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title).setContentText(message)
            .setAutoCancel(true).setContentIntent(openApp(if (manual) 51 else 52, manual))
            .setCategory(Notification.CATEGORY_REMINDER).build()

    private fun beginTracking() {
        ScheduledGateController.cancelExpire(this)
        RouteStore.begin(this)
        FindMyBikeStore.track(this)
        newestFixAt = 0L
        writeError = false
        latestPhase = "Đang tìm GPS"
        lastLocation = null
        stopCandidate = null
        stationarySince = 0L
        walkingSteps = 0
        ensureLocation()
        val steps = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (steps != null && (Build.VERSION.SDK_INT < 29 ||
                    checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)) {
            registeredSteps = sensorManager.registerListener(this, steps, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun ensureLocation() {
        if (receivingLocations || !hasLocationPermission()) {
            if (!hasLocationPermission()) showForeground("Thiếu quyền vị trí · mở app để cấp")
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(700L)
            .setMaxUpdateDelayMillis(1_500L)
            .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            receivingLocations = true
            updateStatusNotification()
        } catch (_: SecurityException) {
            showForeground("Không thể lấy GPS · kiểm tra quyền vị trí")
        }
    }

    private fun inspectLocation(location: Location) {
        if (FindMyBikeStore.mode(this) != FindMyBikeStore.TRACKING ||
            !location.hasAccuracy() || location.accuracy > 100f) return

        val now = SystemClock.elapsedRealtime()
        val fix = Fix(location.latitude, location.longitude, location.accuracy, now)
        // Keep the COMPLETE route from first GPS fix until parking, including after restarts.
        val recorded = RouteStore.append(this, location.time, fix.latitude, fix.longitude, fix.accuracy)
        FindMyBikeStore.lastFix(this, fix.latitude, fix.longitude, fix.accuracy)
        writeError = !recorded
        newestFixAt = now

        val previous = lastLocation
        val derivedSpeed = if (previous != null && location.elapsedRealtimeNanos > previous.elapsedRealtimeNanos) {
            val secs = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1e9f
            if (secs >= 0.5f) location.distanceTo(previous) / secs else -1f
        } else -1f
        val speed = if (location.hasSpeed()) location.speed else derivedSpeed

        // Only arm after a plausible stop; GPS drift cannot confirm parking by itself.
        if (speed >= 0 && speed <= 1.0f) {
            if (stopCandidate == null) {
                stopCandidate = fix
                stationarySince = now
                walkingSteps = 0
                latestPhase = "Xe đã dừng · đang xác minh"
            } else if (now - stationarySince <= 12_000L &&
                fix.accuracy < (stopCandidate?.accuracy ?: Float.MAX_VALUE)) {
                stopCandidate = fix
            }
        } else if (speed > 2.8f) {
            // Fast movement after a stop indicates riding again; walking stays below this threshold.
            stopCandidate = null
            stationarySince = 0L
            walkingSteps = 0
            latestPhase = "Phát hiện xe lại tiếp tục di chuyển"
        }
        lastLocation = location
        if (RouteStore.activeCount(this) == 1) updateStatusNotification()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR) return
        if (FindMyBikeStore.mode(this) != FindMyBikeStore.TRACKING) return
        val candidate = stopCandidate ?: return
        val duration = SystemClock.elapsedRealtime() - stationarySince
        if (duration < 3_000L || duration > 20_000L) return
        walkingSteps++
        latestPhase = "Phát hiện " + walkingSteps + " bước sau khi dừng xe"
        updateStatusNotification()
        if (walkingSteps >= 3) saveFix(candidate, "Dừng xe + đi bộ")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun saveFix(fix: Fix, method: String) {
        RouteStore.finish(this)
        FindMyBikeStore.save(this, fix.latitude, fix.longitude, fix.accuracy, method)
        endWithoutSaving(false)
        BikeProgressNotification.event(this, "4/4 · ĐÃ XÁC ĐỊNH VỊ TRÍ XE",
            "Đã lưu xe · " + RouteStore.savedCount(this) + " điểm GPS",
            "Sai số ước lượng ±" + fix.accuracy.toInt() + "m · " +
                method + " · GPS ĐÃ TẮT. Mở app để xem lại.")
    }

    private fun endWithoutSaving(removeAlert: Boolean) {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(statusTicker)
        ScheduledGateController.cancelExpire(this)
        FindMyBikeStore.setServiceReady(this, false)
        if (receivingLocations) {
            fused.removeLocationUpdates(locationCallback)
            receivingLocations = false
        }
        if (registeredSteps) {
            sensorManager.unregisterListener(this)
            registeredSteps = false
        }
        if (removeAlert) getSystemService(NotificationManager::class.java).cancel(MANUAL_ID)
        if (hasForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            hasForeground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(statusTicker)
        FindMyBikeStore.setServiceReady(this, false)
        if (receivingLocations) fused.removeLocationUpdates(locationCallback)
        if (registeredSteps) sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
