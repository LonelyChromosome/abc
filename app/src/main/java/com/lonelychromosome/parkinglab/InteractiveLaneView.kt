package com.lonelychromosome.parkinglab

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

/** Geometry-only fallback: NOT a Google map. Interactive even without a Maps key. */
class InteractiveLaneView(context: Context, private val network: LaneNetwork) : View(context) {
    var onSelectOrigin: ((GeoPoint)->Unit)? = null
    private var trip: TraceNavigator.BikeTrip? = null
    private var origin: GeoPoint? = null
    private var route: TraceNavigator.Plan? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private val cosLat = cos(Math.toRadians(20.96))
    private var centre = GeoPoint(20.96,105.748)
    private var ppm=1.0
    private var fitted=1.0
    private var lastX=0f
    private var lastY=0f
    private var moved=false
    private var scaling=false
    private val pinch=ScaleGestureDetector(context, object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(d:ScaleGestureDetector):Boolean{scaling=true;return true}
        override fun onScale(d:ScaleGestureDetector):Boolean{
            zoom(d.scaleFactor.toDouble());return true
        }
        override fun onScaleEnd(d:ScaleGestureDetector){scaling=false}
    })
    fun show(t:TraceNavigator.BikeTrip?, o:GeoPoint?, r:TraceNavigator.Plan?){
        trip=t;origin=o;route=r;invalidate()
    }
    fun zoom(factor:Double){ppm=(ppm*factor).coerceIn(fitted*.6,fitted*30);invalidate()}
    fun fit(){
        val a=network.lanes.flatten().toMutableList()
        origin?.let{a.add(it)};trip?.let{a.add(it.bike)}
        if(width<1||height<1||a.isEmpty())return
        val w=(a.maxOf{it.lon}-a.minOf{it.lon})*111320*cosLat
        val h=(a.maxOf{it.lat}-a.minOf{it.lat})*111320
        centre=GeoPoint((a.maxOf{it.lat}+a.minOf{it.lat})/2,
            (a.maxOf{it.lon}+a.minOf{it.lon})/2)
        val margin=36*resources.displayMetrics.density
        fitted=min((width-2*margin).coerceAtLeast(10f)/w.coerceAtLeast(1.0),
            (height-2*margin).coerceAtLeast(10f)/h.coerceAtLeast(1.0))
        ppm=fitted;invalidate()
    }
    override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){
        super.onSizeChanged(w,h,ow,oh);if(ow==0||oh==0)post{fit()}
    }
    private fun x(v:GeoPoint)=(width/2.0+(v.lon-centre.lon)*111320*cosLat*ppm).toFloat()
    private fun y(v:GeoPoint)=(height/2.0-(v.lat-centre.lat)*111320*ppm).toFloat()
    private fun inverse(x:Float,y:Float)=GeoPoint(
        centre.lat-(y-height/2.0)/(111320*ppm),
        centre.lon+(x-width/2.0)/(111320*cosLat*ppm))
    private fun line(c:Canvas,pts:List<GeoPoint>,color:Int,weight:Float,dash:Boolean=false){
        if(pts.size<2)return
        p.color=color;p.style=Paint.Style.STROKE
        p.strokeWidth=weight*resources.displayMetrics.density
        p.pathEffect=if(dash)DashPathEffect(floatArrayOf(12f,9f),0f) else null
        pts.zipWithNext{a,b->c.drawLine(x(a),y(a),x(b),y(b),p)}
        p.pathEffect=null
    }
    private fun dot(c:Canvas,v:GeoPoint?,color:Int){
        if(v==null)return
        p.style=Paint.Style.FILL;p.color=Color.WHITE
        c.drawCircle(x(v),y(v),9f*resources.displayMetrics.density,p)
        p.color=color
        c.drawCircle(x(v),y(v),6f*resources.displayMetrics.density,p)
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.drawColor(Color.rgb(16,29,44))
        val step=when{ppm>=8->10.0;ppm>=3->25.0;ppm>=1->50.0;else->100.0}
        val pixelStep=(step*ppm).toFloat().coerceAtLeast(1f)
        p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=Color.rgb(37,53,69)
        var gx=(width/2f)%pixelStep
        while(gx<width){c.drawLine(gx,0f,gx,height.toFloat(),p);gx+=pixelStep}
        var gy=(height/2f)%pixelStep
        while(gy<height){c.drawLine(0f,gy,width.toFloat(),gy,p);gy+=pixelStep}
        network.lanes.forEach{line(c,it,Color.rgb(82,166,191),3.6f)}
        network.estimatedLinks.forEach{line(c,listOf(it.first,it.second),Color.rgb(242,189,96),3f,true)}
        trip?.trace?.let{line(c,it,Color.rgb(94,115,163),2f,true)}
        route?.let{
            line(c,it.walkToLane,Color.rgb(255,191,93),3f,true)
            line(c,it.lanePart,Color.rgb(36,222,163),5f)
            it.approachPart.zipWithNext{a,b->
                val onLane=(network.nearest(a)?.distance ?: 999.0)<6.0 &&
                    (network.nearest(b)?.distance ?: 999.0)<6.0
                line(c,listOf(a,b),Color.rgb(255,150,62),4f,!onLane)
            }
        }
        network.simulatedStarts.forEach{dot(c,it.second,Color.rgb(90,130,156))}
        dot(c,trip?.bike,Color.rgb(250,74,94));dot(c,origin,Color.rgb(29,229,238))
        p.style=Paint.Style.FILL;p.color=Color.WHITE
        p.textSize=12f*resources.displayMetrics.scaledDensity
        val dx=18f*resources.displayMetrics.density
        val dy=height-18f*resources.displayMetrics.density
        c.drawText(step.toInt().toString()+" m",dx,dy,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=2f*resources.displayMetrics.density
        c.drawLine(dx,dy-7,dx+pixelStep,dy-7,p)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        pinch.onTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                lastX=e.x;lastY=e.y;moved=false
                parent?.requestDisallowInterceptTouchEvent(true);return true
            }
            MotionEvent.ACTION_POINTER_DOWN->{scaling=true;moved=true;return true}
            MotionEvent.ACTION_MOVE->{
                if(e.pointerCount==1&&!pinch.isInProgress&&!scaling){
                    val dx=e.x-lastX;val dy=e.y-lastY
                    if(abs(dx)+abs(dy)>2f){
                        centre=GeoPoint(centre.lat+dy/(111320*ppm),
                            centre.lon-dx/(111320*cosLat*ppm));moved=true;invalidate()
                    }
                    lastX=e.x;lastY=e.y
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP->{moved=true;return true}
            MotionEvent.ACTION_UP->{
                if(!moved&&!scaling)onSelectOrigin?.invoke(inverse(e.x,e.y))
                scaling=false;performClick();return true
            }
            MotionEvent.ACTION_CANCEL->{scaling=false;return true}
        }
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
