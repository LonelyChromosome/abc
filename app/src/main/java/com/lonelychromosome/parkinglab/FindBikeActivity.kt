package com.lonelychromosome.parkinglab

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** REAL ONLY: saved TRACKED destination + new GPS fixes from foreground location service. */
class FindBikeActivity : Activity() {
    private val bg=Color.rgb(11,21,35)
    private val cyan=Color.rgb(66,204,246)
    private lateinit var map:FieldMapView
    private lateinit var title:TextView
    private lateinit var info:TextView
    private lateinit var start:Button
    private lateinit var stop:Button
    private lateinit var calibrate:TextView
    private val handler=Handler(Looper.getMainLooper())
    private val poll=object:Runnable {
        override fun run(){render();handler.postDelayed(this,2000L)}
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun style(color:Int)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(13).toFloat()
    }
    private fun txt(s:String,sz:Float,col:Int=Color.WHITE)=TextView(this).apply {
        text=s;textSize=sz;setTextColor(col)
    }
    private fun btn(s:String,click:()->Unit)=Button(this).apply {
        text=s;isAllCaps=false;textSize=13f;setTextColor(Color.WHITE)
        background=style(Color.rgb(32,57,75));setOnClickListener{click()}
    }
    override fun onCreate(saved:Bundle?){
        super.onCreate(saved)
        window.statusBarColor=Color.rgb(8,13,22)
        window.navigationBarColor=Color.rgb(8,13,22)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)
        }
        root.setOnApplyWindowInsetsListener{v,insets->
            if(Build.VERSION.SDK_INT>=30){
                val b=insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                v.setPadding(0,b.top,0,b.bottom)
            }
            insets
        }
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(17),dp(15),dp(17),dp(14))
        }
        title=txt("FIND MY BIKE · GPS THỰC",19f,cyan)
        header.addView(title)
        info=txt("Đang tải vị trí TRACKED...",13f,Color.rgb(185,205,218))
        header.addView(info)
        root.addView(header)
        val frame=FrameLayout(this)
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        map=FieldMapView(this)
        frame.addView(map,FrameLayout.LayoutParams(-1,-1))
        val zoom=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        zoom.addView(btn("+"){map.zoomIn()},LinearLayout.LayoutParams(dp(45),dp(46)))
        zoom.addView(btn("−"){map.zoomOut()},LinearLayout.LayoutParams(dp(45),dp(46)))
        zoom.addView(btn("◎"){map.reset()},LinearLayout.LayoutParams(dp(45),dp(46)))
        frame.addView(zoom,FrameLayout.LayoutParams(-2,-2,Gravity.END or Gravity.CENTER_VERTICAL).apply {rightMargin=dp(8)})
        val bottom=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(15),dp(12),dp(15),dp(15))
            background=style(Color.rgb(15,29,44))
        }
        start=btn("DẪN ĐƯỜNG · GPS HIỆN TẠI"){navigate()}
        stop=btn("END · KẾT THÚC DẪN ĐƯỜNG"){finishNav()}
        bottom.addView(start,LinearLayout.LayoutParams(-1,dp(52)))
        bottom.addView(stop,LinearLayout.LayoutParams(-1,dp(49)))
        val calibrateRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        calibrateRow.addView(btn("CHẤM CỔNG 1"){selectGate(0)},LinearLayout.LayoutParams(0,dp(46),1f))
        calibrateRow.addView(btn("CHẤM CỔNG 2"){selectGate(1)},LinearLayout.LayoutParams(0,dp(46),1f))
        bottom.addView(calibrateRow)
        calibrate=txt("Hiệu chuẩn bằng 2 mốc thực, không ước đoán pixel/m.",11f,
            Color.rgb(181,194,208)).apply{setPadding(0,dp(7),0,dp(7))}
        bottom.addView(calibrate)
        bottom.addView(btn("MỞ GOOGLE MAPS DỰ PHÒNG (GPS THẬT)"){openGoogleMaps()},
            LinearLayout.LayoutParams(-1,dp(47)))
        root.addView(bottom)
        setContentView(root)
        map.onGate={gate->
            Toast.makeText(this,"Đã chấm mốc cổng ${gate+1} trên ảnh. Cần xác minh tỷ lệ thực địa.",Toast.LENGTH_LONG).show()
            render()
        }
        render()
    }
    private fun selectGate(i:Int) {
        map.selectedGate=i
        Toast.makeText(this,"Chạm đúng tâm cổng ${i+1} trên ảnh map để định chuẩn.",Toast.LENGTH_LONG).show()
    }
    private fun navigate(){
        if(!TripStore.hasSaved(this)) {
            AlertDialog.Builder(this).setTitle("Chưa có TRACKED")
                .setMessage("Chưa lưu được điểm đỗ thật. Hãy thực hiện một lần tracking trước.")
                .setPositiveButton("Đóng",null).show()
            return
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),51)
            return
        }
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),52)
            return
        }
        try {
            startForegroundService(Intent(this,LiveNavigationService::class.java)
                .setAction(LiveNavigationService.START))
            info.text="ĐANG LẤY GPS THẬT · đợi fix mới..."
        }catch(e:Exception){
            Toast.makeText(this,"Không bật GPS dẫn đường: ${e.javaClass.simpleName}",Toast.LENGTH_LONG).show()
        }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if(requestCode in 51..52) {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                navigate()
            else Toast.makeText(this,"Cần quyền Vị trí chính xác",Toast.LENGTH_LONG).show()
        }
    }
    private fun finishNav(){
        if(NavState.active(this)){
            startService(Intent(this,LiveNavigationService::class.java)
                .setAction(LiveNavigationService.END))
        }
        NavState.ended(this);FieldStatus.ended(this);render()
    }
    private fun openGoogleMaps(){
        val lat=TripStore.parkLat(this)?:return
        val lon=TripStore.parkLon(this)?:return
        val destination=String.format(Locale.US,"%.8f,%.8f",lat,lon)
        val uri=Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destination&travelmode=walking")
        try{startActivity(Intent(Intent.ACTION_VIEW,uri))}catch(e:Exception){
            Toast.makeText(this,"Không mở được ứng dụng bản đồ",Toast.LENGTH_LONG).show()
        }
    }
    private fun render(){
        if(!::info.isInitialized)return
        val lat=TripStore.parkLat(this)
        val lon=TripStore.parkLon(this)
        val current=NavState.location(this)
        val bike=if(lat!=null&&lon!=null) GeoPoint(lat,lon) else null
        map.setGps(current,bike,NavState.active(this))
        start.isEnabled=bike!=null
        stop.visibility=if(NavState.active(this)) View.VISIBLE else View.GONE
        calibrate.text=if(map.isCalibrated())
            "Hiệu chuẩn 2 cổng: ${map.scaleLabel()} · cần kiểm chứng sai số ngoài thực địa"
        else "Chưa định chuẩn 2 cổng · không vẽ tuyến theo tỷ lệ giả"
        if(bike==null){
            info.text="CHƯA CÓ ĐIỂM TRACKED THỰC · cần ghi xe + đi bộ 10s trước"
            return
        }
        val parked=String.format(Locale.US,"%.6f, %.6f",lat,lon)
        if(current==null){
            info.text="TRACKED: $parked\n"+if(NavState.active(this))
                "ĐANG DẪN ĐƯỜNG · chờ vị trí GPS thực (không dùng điểm giả)"
            else "Bấm DẪN ĐƯỜNG để bắt đầu lấy GPS hiện tại"
        }else {
            val gap=current.distance(bike)
            val route=map.remainingPathMetres()
            info.text="TRACKED: $parked\nGPS BẠN ±${NavState.accuracy(this).toInt()} m · " +
                "Cách điểm xe ~${gap.toInt()} m (thẳng)" +
                if(route!=null) " · Tuyến map ~${route.toInt()} m" else " · Tuyến map chưa đủ dữ liệu"
        }
    }
    override fun onResume(){super.onResume();handler.removeCallbacks(poll);handler.post(poll)}
    override fun onPause(){handler.removeCallbacks(poll);super.onPause()}
}
