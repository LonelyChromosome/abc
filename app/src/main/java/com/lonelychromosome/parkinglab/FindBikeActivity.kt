package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup
import android.os.Build
import java.util.Locale

/**
 * Find bike WITHOUT a map or turn-by-turn navigation.
 * Just two GPS positions on a clean white canvas and their straight-line distance.
 */
class FindBikeActivity : Activity() {
    private val ink=Color.rgb(24,43,59)
    private val secondary=Color.rgb(111,127,143)
    private lateinit var field:RelativePositionView
    private lateinit var distanceText:TextView
    private lateinit var gpsStatus:TextView
    private lateinit var bikeStatus:TextView
    private val handler=Handler(Looper.getMainLooper())
    private var lastStarted=false
    private val poll=object:Runnable {
        override fun run(){
            render()
            handler.postDelayed(this,2_000L)
        }
    }

    private fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()
    private fun label(value:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply{
        text=value
        textSize=size
        setTextColor(color)
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun button(value:String, callback:()->Unit)=Button(this).apply{
        text=value
        isAllCaps=false
        textSize=14f
        setTextColor(ink)
        background=GradientDrawable().apply{
            setColor(Color.rgb(242,247,250))
            cornerRadius=dp(14).toFloat()
        }
        setOnClickListener{callback()}
    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.WHITE
        window.navigationBarColor=Color.WHITE
        if(Build.VERSION.SDK_INT>=26){
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(18),0,dp(18),0)
        }
        root.setOnApplyWindowInsetsListener{view,insets->
            if(Build.VERSION.SDK_INT>=30){
                val safe=insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                view.setPadding(dp(18),safe.top,dp(18),safe.bottom)
            }
            insets
        }
        val title=label("TÌM XE",15f,secondary,true)
        root.addView(title,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18)})
        distanceText=label("— m",47f,ink,true).apply{gravity=Gravity.CENTER_HORIZONTAL}
        root.addView(distanceText,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18)})
        root.addView(label("Khoảng cách đường chim bay",14f,secondary).apply{
            gravity=Gravity.CENTER_HORIZONTAL
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(2)})
        field=RelativePositionView(this)
        root.addView(field,LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=dp(10)})
        val legend=label("●  BẠN          ●  XE ĐỖ",14f,ink,true).apply{
            gravity=Gravity.CENTER_HORIZONTAL
        }
        root.addView(legend,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
        gpsStatus=label("Đang chờ GPS...",13f,secondary).apply{
            gravity=Gravity.CENTER_HORIZONTAL
        }
        root.addView(gpsStatus,LinearLayout.LayoutParams(-1,-2))
        bikeStatus=label("",12f,secondary).apply{gravity=Gravity.CENTER_HORIZONTAL}
        root.addView(bikeStatus,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)})
        root.addView(button("LẤY LẠI VỊ TRÍ GPS"){startLocationWatch()},
            LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(18)})
        root.addView(button("QUAY LẠI"){finish()},
            LinearLayout.LayoutParams(-1,dp(47)).apply{
                topMargin=dp(7);bottomMargin=dp(12)
            })
        setContentView(root)
        render()
    }

    private fun startLocationWatch(){
        if(!TripStore.hasSaved(this) || TripStore.running(this)){
            render()
            return
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),51)
            return
        }
        try{
            startForegroundService(Intent(this,LiveNavigationService::class.java)
                .setAction(LiveNavigationService.START))
            lastStarted=true
            gpsStatus.text="Đang lấy tọa độ GPS thật..."
        }catch(e:Exception){
            gpsStatus.text="Không thể lấy GPS: ${e.javaClass.simpleName}"
        }
    }

    override fun onRequestPermissionsResult(requestCode:Int,
        permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==51){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                startLocationWatch()
            else gpsStatus.text="Cần cấp quyền Vị trí chính xác"
        }
    }

    private fun stopLocationWatch(){
        if(!lastStarted)return
        lastStarted=false
        try{
            startService(Intent(this,LiveNavigationService::class.java)
                .setAction(LiveNavigationService.END))
        }catch(_:Exception){}
        NavState.ended(this)
    }

    private fun render(){
        if(!::field.isInitialized)return
        val parkedLat=TripStore.parkLat(this)
        val parkedLon=TripStore.parkLon(this)
        // Do not display a previous bike as the new trip's current destination.
        val parked=if(!TripStore.running(this) && parkedLat!=null && parkedLon!=null)
            GeoPoint(parkedLat,parkedLon) else null
        val activeFix=if(TripStore.running(this)){
            val lat=TripStore.latitude(this)
            val lon=TripStore.longitude(this)
            if(lat!=null && lon!=null && System.currentTimeMillis()-TripStore.lastFix(this)<15_000L)
                GeoPoint(lat,lon) else null
        }else NavState.location(this)
        field.setPositions(activeFix,parked)
        if(parked==null){
            distanceText.text="— m"
            gpsStatus.text=if(TripStore.running(this))
                "Đang ghi hành trình. Chưa xác nhận đỗ xe."
                else "Chưa có tọa độ xe đỗ đã lưu"
            bikeStatus.text="Không hiển thị điểm ảo"
            return
        }
        bikeStatus.text=String.format(Locale.US,"Xe đỗ: %.6f, %.6f",parked.lat,parked.lon)
        if(activeFix==null){
            distanceText.text="— m"
            gpsStatus.text="Đang chờ vị trí hiện tại từ GPS"
        }else{
            val metres=activeFix.distance(parked)
            distanceText.text=if(metres<1000) "${metres.toInt()} m"
                else String.format(Locale.US,"%.2f km",metres/1000f)
            val accuracy=if(TripStore.running(this))TripStore.accuracy(this)
                else NavState.accuracy(this)
            gpsStatus.text=if(accuracy>0f)
                "GPS hiện tại ±${accuracy.toInt()} m · khoảng cách ước tính"
                else "Khoảng cách ước tính từ tọa độ GPS"
        }
    }
    override fun onResume(){
        super.onResume()
        handler.removeCallbacks(poll)
        handler.post(poll)
        startLocationWatch()
    }
    override fun onPause(){
        handler.removeCallbacks(poll)
        stopLocationWatch()
        super.onPause()
    }
}
