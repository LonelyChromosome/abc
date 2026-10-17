package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock

/** GPS ONLY: feed current position while the simple two-pin view is visible.
 * No directions, routing or movement simulation. Never overwrites the parking record.
 */
class LiveNavigationService: Service(),LocationListener {
    companion object {
        const val START="fmb.live.START"
        const val END="fmb.live.END"
        private const val CHANNEL="fmb_nav_fgs"
        private const val ID=7226
    }
    private lateinit var location:LocationManager
    private var startedGps=false
    override fun onCreate(){
        super.onCreate()
        location=getSystemService(LOCATION_SERVICE) as LocationManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Find My Bike · vị trí hiện tại",NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(text:String):Notification {
        val open=PendingIntent.getActivity(this,16,
            Intent(this,FindBikeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Find My Bike · vị trí hiện tại")
            .setContentText(text).setContentIntent(open)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true).setOngoing(true).build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==END){
            NavState.ended(this)
            if(TripStore.hasSaved(this))FieldStatus.tracked(this,TripStore.accuracy(this))
            shutdown()
            return START_NOT_STICKY
        }
        if(intent?.action!=START||!TripStore.hasSaved(this)){
            shutdown();return START_NOT_STICKY
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            FieldStatus.error(this,"Không có quyền vị trí chính xác");shutdown();return START_NOT_STICKY
        }
        try{
            NavState.started(this)
            if(Build.VERSION.SDK_INT>=34)
                startForeground(ID,notification("Đang chờ GPS thật..."),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID,notification("Đang chờ GPS thật..."))
            location.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,0f,this)
            startedGps=true
            FieldStatus.publish(this,"XEM VỊ TRÍ","Đang lấy GPS thật để đo khoảng cách tới xe",true)
        }catch(ex:Exception){
            NavState.ended(this)
            FieldStatus.error(this,"Không thể khởi động GPS: ${ex.javaClass.simpleName}")
            shutdown()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onLocationChanged(fix:Location){
        if(!fix.hasAccuracy()||!fix.accuracy.isFinite()||fix.accuracy>40f)return
        if(SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos>12_000_000_000L)return
        NavState.fix(this,fix)
        val a=TripStore.parkLat(this)?:return
        val b=TripStore.parkLon(this)?:return
        val remaining=FloatArray(1)
        Location.distanceBetween(fix.latitude,fix.longitude,a,b,remaining)
        val detail="Cách xe ~${remaining[0].toInt()} m (đường chim bay) · GPS ±${fix.accuracy.toInt()} m"
        getSystemService(NotificationManager::class.java).notify(ID,notification(detail))
        FieldStatus.publish(this,"KHOẢNG CÁCH",detail,true)
    }
    override fun onProviderDisabled(provider:String){
        FieldStatus.error(this,"GPS đang tắt. Bật Vị trí để đo khoảng cách.")
    }
    private fun shutdown(){
        if(startedGps){try{location.removeUpdates(this)}catch(_:Exception){};startedGps=false}
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy(){
        if(startedGps)try{location.removeUpdates(this)}catch(_:Exception){}
        super.onDestroy()
    }
    override fun onBind(intent:Intent?):IBinder?=null
}
