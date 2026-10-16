package com.lonelychromosome.parkinglab

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import android.graphics.drawable.GradientDrawable
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlin.math.roundToInt

/** Isolated simulated test: Google supplies ONLY background pixels, BPA computes every route. */
class FindBikeActivity : Activity(), OnMapReadyCallback {
    private lateinit var network:LaneNetwork
    private lateinit var info:TextView
    private lateinit var caption:TextView
    private lateinit var mapFrame:FrameLayout
    private var mapView:MapView?=null
    private var map:GoogleMap?=null
    private var preview:InteractiveLaneView?=null
    private var trip:TraceNavigator.BikeTrip?=null
    private var start:GeoPoint?=null
    private var path:TraceNavigator.Plan?=null
    private var index=0
    private val bg=Color.rgb(11,21,35)
    private val accent=Color.rgb(77,239,191)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun ll(p:GeoPoint)=LatLng(p.lat,p.lon)
    private fun style(color:Int,radius:Int=15)=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(radius).toFloat()
    }
    private fun text(s:String,size:Float,color:Int=Color.WHITE)=TextView(this).apply{
        text=s;textSize=size;setTextColor(color)
    }
    private fun button(s:String,fill:Int,click:()->Unit)=Button(this).apply{
        text=s;textSize=12.5f;isAllCaps=false;setTextColor(Color.WHITE)
        background=style(fill,12);setOnClickListener{click()}
    }
    override fun onCreate(saved:Bundle?){
        super.onCreate(saved)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        window.decorView.systemUiVisibility=0
        network=LaneNetwork.load(this)
        trip=TraceNavigator.referenceTrip(this)
        start=network.simulatedStarts.firstOrNull()?.second
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        // Android 15 edge-to-edge: keep title out of the status bar on every device.
        root.setOnApplyWindowInsetsListener{v,insets->
            if(Build.VERSION.SDK_INT>=30){
                val b=insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                v.setPadding(0,b.top,0,b.bottom)
            }else{
                @Suppress("DEPRECATION")
                v.setPadding(0,insets.systemWindowInsetTop,0,insets.systemWindowInsetBottom)
            }
            insets
        }
        val top=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(12),dp(16),dp(10))
        }
        top.addView(text("FIND BIKE",19f,accent))
        top.addView(text("MÔ PHỎNG TUYẾN ĐẾN XE · GPS KHÔNG BẬT",11f,Color.rgb(162,187,205)))
        root.addView(top)
        mapFrame=FrameLayout(this)
        root.addView(mapFrame,LinearLayout.LayoutParams(-1,0,1f))
        val controlBar=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(13),dp(14),dp(13))
            background=style(Color.rgb(18,32,49),16)
        }
        info=text("",13f).apply{setLineSpacing(dp(3).toFloat(),1f)}
        controlBar.addView(info)
        val legend=text("━  LANE       ━  TRÊN LANE       - -  NGOÀI LANE / TRACE",10.5f,
            Color.rgb(162,187,205)).apply{setPadding(0,dp(9),0,dp(9))}
        controlBar.addView(legend)
        val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        buttons.addView(button("TÌM LẠI TUYẾN",Color.rgb(0,125,109)){refresh()},LinearLayout.LayoutParams(0,dp(49),1f))
        val gap=View(this)
        buttons.addView(gap,LinearLayout.LayoutParams(dp(9),1))
        buttons.addView(button("ĐỔI ĐIỂM ĐI",Color.rgb(44,69,91)){
            if(network.simulatedStarts.isNotEmpty()){
                index=(index+1)%network.simulatedStarts.size
                start=network.simulatedStarts[index].second
                refresh();fit()
            }
        },LinearLayout.LayoutParams(0,dp(49),1f))
        controlBar.addView(buttons)
        root.addView(controlBar)
        setContentView(root)
        val key=try{packageManager.getApplicationInfo(packageName,
            android.content.pm.PackageManager.GET_META_DATA).metaData
            ?.getString("com.google.android.geo.API_KEY")}catch(_:Exception){null}
        if(key.isNullOrBlank()||key=="NO_KEY"){
            preview=InteractiveLaneView(this,network).apply{
                onSelectOrigin={newPos->
                    start=newPos
                    refresh()
                }
            }
            mapFrame.addView(preview,FrameLayout.LayoutParams(-1,-1))
        }else{
            mapView=MapView(this)
            mapFrame.addView(mapView,FrameLayout.LayoutParams(-1,-1))
            mapView?.onCreate(saved)
            mapView?.getMapAsync(this)
        }
        val floating=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(4),dp(4),dp(4))
            background=style(Color.rgb(24,42,59),12)
        }
        listOf("+" to 1,"−" to -1,"◎" to 0).forEach{(label,dir)->
            floating.addView(button(label,Color.rgb(35,60,79)){
                when(dir){
                    1 -> {map?.animateCamera(CameraUpdateFactory.zoomIn());preview?.zoom(1.6)}
                    -1 -> {map?.animateCamera(CameraUpdateFactory.zoomOut());preview?.zoom(1.0/1.6)}
                    else -> fit()
                }
            },LinearLayout.LayoutParams(dp(49),dp(49)))
        }
        mapFrame.addView(floating,FrameLayout.LayoutParams(-2,-2,Gravity.END or Gravity.CENTER_VERTICAL)
            .apply{rightMargin=dp(12)})
        caption=text(if(mapView==null)"CHƯA CÓ GOOGLE MAPS KEY · SƠ ĐỒ GPS"
            else "GOOGLE MAPS · NỀN HIỂN THỊ",10f,Color.WHITE).apply{
            setPadding(dp(10),dp(7),dp(10),dp(7))
            background=style(Color.rgb(28,49,68),9)
        }
        mapFrame.addView(caption,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START)
            .apply{leftMargin=dp(12);topMargin=dp(12)})
        refresh()
    }
    override fun onMapReady(m:GoogleMap){
        map=m
        m.mapType=GoogleMap.MAP_TYPE_NORMAL
        m.uiSettings.isZoomGesturesEnabled=true
        m.uiSettings.isScrollGesturesEnabled=true
        m.uiSettings.isCompassEnabled=true
        m.uiSettings.isZoomControlsEnabled=false
        m.setOnMapClickListener{location->
            start=GeoPoint(location.latitude,location.longitude)
            refresh()
        }
        render()
        mapFrame.post{fit()}
    }
    private fun fit(){
        val m=map
        if(m!=null){
            val builder=LatLngBounds.Builder()
            network.lanes.flatten().forEach{builder.include(ll(it))}
            start?.let{builder.include(ll(it))}
            trip?.let{builder.include(ll(it.bike))}
            if(mapFrame.width>0&&mapFrame.height>0){
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(),dp(48)))
            }else{
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(ll(trip?.bike
                    ?:GeoPoint(20.959505,105.747637)),17f))
            }
        }
        preview?.fit()
    }
    private fun poly(points:List<GeoPoint>,color:Int,width:Float,dash:Boolean=false,z:Float=2f){
        if(points.size<2)return
        val o=PolylineOptions().addAll(points.map(::ll)).color(color).width(width*resources.displayMetrics.density).zIndex(z)
        if(dash)o.pattern(listOf(Dash(dp(12).toFloat()),Gap(dp(9).toFloat())))
        map?.addPolyline(o)
    }
    private fun approach(c:List<GeoPoint>){
        c.zipWithNext{a,b->
            // A trace segment is solid only if both ends are on an existing digitized lane.
            val onLane=(network.nearest(a)?.distance ?: 999.0)<6.0 &&
                (network.nearest(b)?.distance ?: 999.0)<6.0
            poly(listOf(a,b),Color.rgb(255,163,65),4.2f,!onLane,6f)
        }
    }
    private fun render(){
        val m=map
        if(m!=null){
            m.clear()
            network.lanes.forEach{poly(it,Color.rgb(39,151,181),2.6f,false,1f)}
            network.estimatedLinks.forEach{poly(listOf(it.first,it.second),
                Color.rgb(236,191,104),2.8f,true,1f)}
            trip?.trace?.let{poly(it,Color.rgb(132,141,195),2f,true,2f)}
            path?.let{route->
                if(route.walkToLane.zipWithNext().any{(a,b)->a.distance(b)>3.0})
                    poly(route.walkToLane,Color.rgb(244,192,101),3f,true,4f)
                poly(route.lanePart,Color.rgb(31,221,148),5f,false,5f)
                approach(route.approachPart)
            }
            start?.let{m.addMarker(MarkerOptions().position(ll(it)).title("Điểm xuất phát giả lập")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))}
            trip?.let{m.addMarker(MarkerOptions().position(ll(it.bike)).title("XE ĐỖ · TỌA ĐỘ ĐÃ XÁC NHẬN")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))}
        }
        preview?.show(trip,start,path)
    }
    private fun refresh(){
        path=if(start!=null&&trip!=null)TraceNavigator.plan(network,start!!,trip!!) else null
        val originName=network.simulatedStarts.find{it.second==start}?.first ?: "Đã chọn trên bản đồ"
        info.text=if(path!=null)
            "TỪ: "+originName+"      →      XE ĐÃ GHI"+
            "\nTUYẾN: "+path!!.metres.roundToInt()+" m  ·  Nối trace tại điểm #"+(path!!.traceEntryIndex+1)
        else "TỪ: "+originName+"\nCHƯA TÌM ĐƯỢC LỐI KHỚP LANE VỚI TRACE"
        render()
    }
    override fun onStart(){super.onStart();mapView?.onStart()}
    override fun onResume(){super.onResume();mapView?.onResume()}
    override fun onPause(){mapView?.onPause();super.onPause()}
    override fun onStop(){mapView?.onStop();super.onStop()}
    override fun onDestroy(){mapView?.onDestroy();super.onDestroy()}
    override fun onLowMemory(){super.onLowMemory();mapView?.onLowMemory()}
    override fun onSaveInstanceState(out:Bundle){mapView?.onSaveInstanceState(out);super.onSaveInstanceState(out)}
}
