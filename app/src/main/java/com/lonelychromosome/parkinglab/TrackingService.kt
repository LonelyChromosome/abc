package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*

/** GPS location FGS runs only during a user-confirmed trip. */
class TrackingService : Service(), LocationListener {
    companion object {
        const val START = "parkinglab.START_GPS"
        const val STOP = "parkinglab.STOP_GPS"
        private const val CHANNEL = "parking_gps_v3"
        private const val NOTIFICATION = 3003
    }
    private lateinit var manager: LocationManager
    private var listening = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (!TripStore.running(this@TrackingService)) return
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION, buildNotice())
            handler.postDelayed(this, 10_000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LOCATION_SERVICE) as LocationManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ghi hành trình GPS",
                NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (intent?.action == START && !TripStore.running(this)) TripStore.begin(this)
        if (!TripStore.running(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION, buildNotice(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION, buildNotice())
            if (!listening) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) throw SecurityException("No fine location")
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    throw IllegalStateException("GPS is switched off")
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    2_000L, 0f, this, Looper.getMainLooper())
                listening = true
            }
        } catch (e: Exception) {
            TripStore.cancel(this)
            TripStore.alert(this, "Không bật được tracking: ${e.javaClass.simpleName}")
            shutdown()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(refresh)
        handler.post(refresh)
        return START_STICKY
    }
    override fun onLocationChanged(location: Location) {
        TripStore.onFix(this, location)
    }
    override fun onProviderDisabled(provider: String) {
        TripStore.alert(this, "GPS bị tắt — hãy bật lại để tiếp tục ghi hành trình")
    }
    private fun buildNotice(): Notification {
        val pending = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val info = if (TripStore.stage(this) == TripStore.STOP)
            "Xe có vẻ đã dừng · mở app để lưu điểm đỗ"
        else "Đang ghi đường đi · ${TripStore.count(this)} điểm GPS"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Parking Lab · GPS đang chạy")
            .setContentText(info)
            .setContentIntent(pending)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true).setOngoing(true).build()
    }
    private fun shutdown() {
        handler.removeCallbacks(refresh)
        if (listening) {
            try { manager.removeUpdates(this) } catch (_: Exception) { }
            listening = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        if (listening) {
            try { manager.removeUpdates(this) } catch (_: Exception) { }
            listening = false
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
