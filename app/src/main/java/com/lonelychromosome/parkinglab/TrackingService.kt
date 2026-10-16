package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*

/** GPS FGS only during active trip; step detector watches departure from the stopped motorcycle. */
class TrackingService : Service(), LocationListener, SensorEventListener {
    companion object {
        const val START = "parkinglab.START_GPS"
        const val AUTO_START = "parkinglab.AUTO_START_GPS"
        const val STOP = "parkinglab.STOP_GPS"
        private const val CHANNEL = "parking_gps_v5"
        private const val NOTICE = 3003
    }
    private lateinit var manager: LocationManager
    private lateinit var sensors: SensorManager
    private var listeningGps = false
    private var listeningSteps = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (!TripStore.running(this@TrackingService)) {
                shutdown()
                return
            }
            getSystemService(NotificationManager::class.java).notify(NOTICE, notification())
            handler.postDelayed(this, 10_000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "GPS và xác minh đi bộ",
                NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if ((intent?.action == START || intent?.action == AUTO_START) && !TripStore.running(this)) TripStore.begin(this)
        if (!TripStore.running(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTICE, notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTICE, notification())
            if (!listeningGps) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    throw SecurityException("Fine location is required")
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    throw IllegalStateException("GPS is off")
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    2_000L, 0f, this, Looper.getMainLooper())
                listeningGps = true
            }
            if (!listeningSteps) {
                val granted = Build.VERSION.SDK_INT < 29 ||
                    checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                val step = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                if (step != null && granted)
                    listeningSteps = sensors.registerListener(this, step, SensorManager.SENSOR_DELAY_NORMAL)
                if (!listeningSteps) {
                    TripStore.alert(this, if (step == null)
                        "Không có cảm biến bước chân — lưu điểm đỗ thủ công"
                    else "Cần quyền Hoạt động thể chất để tự chốt sau 10 giây đi bộ")
                }
            }
        } catch (ex: Exception) {
            TripStore.cancel(this)
            shutdown()
            TripStore.alert(this, "Không khởi động GPS: " + ex.javaClass.simpleName)
            FieldStatus.error(this,"Không khởi động GPS: " + ex.javaClass.simpleName)
            return START_NOT_STICKY
        }
        if (intent?.action == AUTO_START) ClassSchedule.markTriggered(this)
        FieldStatus.tracking(this)
        handler.removeCallbacks(refresh)
        handler.post(refresh)
        return START_STICKY
    }
    override fun onLocationChanged(location: Location) {
        val before=TripStore.stage(this)
        TripStore.onFix(this, location)
        val after=TripStore.stage(this)
        if(before!=after && after==TripStore.STOP) FieldStatus.stopped(this,0,0)
        else if(before!=after && after==TripStore.TRACK) FieldStatus.tracking(this)
    }
    override fun onProviderDisabled(provider: String) {
        TripStore.alert(this, "GPS đã tắt: bật lại để ghi hành trình")
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
        if (TripStore.onStep(this, event.timestamp / 1_000_000L)) {
            CampusFence.record(this, "Đã nhận đi bộ 10s · lưu điểm đỗ nội suy ngược")
            FieldStatus.tracked(this,TripStore.accuracy(this))
            shutdown()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val info = if (TripStore.stage(this) == TripStore.STOP)
            "Đã dừng xe · chờ 10 giây bước chân (${TripStore.stepCount(this)} bước)"
        else "Đang ghi hành trình · ${TripStore.count(this)} điểm GPS"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Parking Lab · tracking")
            .setContentText(info).setContentIntent(pending)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true).setOngoing(true).build()
    }
    private fun shutdown() {
        handler.removeCallbacks(refresh)
        if (listeningGps) {
            try { manager.removeUpdates(this) } catch (_: Exception) {}
            listeningGps = false
        }
        if (listeningSteps) {
            sensors.unregisterListener(this)
            listeningSteps = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        if (listeningGps) {
            try { manager.removeUpdates(this) } catch (_: Exception) {}
        }
        if (listeningSteps) sensors.unregisterListener(this)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
