package com.lonelychromosome.parkinglab

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*
import java.util.PriorityQueue

/** Approved static map. It is never resized non-uniformly. Navigation runs only after gate calibration. */
class FieldMapView(ctx:Context) : View(ctx) {
    private val calibration=FieldCalibration(ctx)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val bitmap:Bitmap?=try {ctx.assets.open("approved_map.webp").use{BitmapFactory.decodeStream(it)}}catch(_:Exception){null}
    private val mapWidth=1448f
    private val mapHeight=1086f
    var onGate:((Int)->Unit)?=null
    var selectedGate=-1
    private var person:GeoPoint?=null
    private var parked:GeoPoint?=null
    private var path:List<PointF> = emptyList()
    private var angle=0f
    private var zoom=1f
    private var panX=0f
    private var panY=0f
    private var downX=0f
    private var downY=0f
    private var dragged=false
    private val pinch=ScaleGestureDetector(ctx,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(d:ScaleGestureDetector):Boolean{
            zoom=(zoom*d.scaleFactor).coerceIn(1f,12f)
            invalidate();return true
        }
    })
    private fun factor():Float=min(width/mapWidth,height/mapHeight)*zoom
    private fun left():Float=(width-mapWidth*factor())/2f+panX
    private fun top():Float=(height-mapHeight*factor())/2f+panY
    private fun src(x:Float,y:Float)=PointF((x-left())/factor(),(y-top())/factor())
    fun setGps(now:GeoPoint?,bike:GeoPoint?,follow:Boolean=false) {
        person=now;parked=bike
        path=if(now!=null&&bike!=null&&calibration.has()) {
            val a=calibration.point(now.lat,now.lon)
            val b=calibration.point(bike.lat,bike.lon)
            if(a!=null&&b!=null) pathfind(a,b) else emptyList()
        } else emptyList()
        if(follow && now!=null) {
            val p=calibration.point(now.lat,now.lon)
            if(p!=null&&p.x in 0f..mapWidth&&p.y in 0f..mapHeight) {
                panX=(width/2f)-(width-mapWidth*factor())/2f-p.x*factor()
                panY=(height*.51f)-(height-mapHeight*factor())/2f-p.y*factor()
            }
        }
        invalidate()
    }
    fun remainingPathMetres():Double? {
        val scale=calibration.metrePerPixel()?:return null
        if(path.size<2)return null
        return path.zipWithNext().sumOf{(a,b)->hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble())}*scale
    }
    fun isCalibrated()=calibration.metrePerPixel()!=null
    fun scaleLabel():String=calibration.metrePerPixel()?.let{"%.2f m/pixel".format(it)}?:"Chưa định chuẩn 2 cổng"
    fun zoomIn(){zoom=(zoom*1.5f).coerceAtMost(12f);invalidate()}
    fun zoomOut(){zoom=(zoom/1.5f).coerceAtLeast(1f);invalidate()}
    fun reset(){zoom=1f;panX=0f;panY=0f;invalidate()}
    private fun dot(c:Canvas,p:PointF?,color:Int,r:Float=9f){
        if(p==null)return
        if(p.x !in 0f..mapWidth||p.y !in 0f..mapHeight)return
        paint.style=Paint.Style.FILL
        paint.color=Color.WHITE;c.drawCircle(p.x,p.y,r+3,paint)
        paint.color=color;c.drawCircle(p.x,p.y,r,paint)
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c)
        c.drawColor(Color.rgb(239,241,244))
        c.save()
        c.translate(left(),top());c.scale(factor(),factor())
        val b=bitmap
        if(b!=null){
            paint.style=Paint.Style.FILL;paint.color=Color.WHITE
            c.drawBitmap(b,null,RectF(0f,0f,mapWidth,mapHeight),paint)
        }else {
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(206,219,230)
            c.drawRect(0f,0f,mapWidth,mapHeight,paint)
        }
        if(path.size>=2) {
            val p=Path();p.moveTo(path[0].x,path[0].y)
            path.drop(1).forEach{p.lineTo(it.x,it.y)}
            paint.style=Paint.Style.STROKE
            paint.color=Color.rgb(4,175,219)
            paint.strokeWidth=2.6f/factor()
            c.drawPath(p,paint)
            paint.pathEffect=DashPathEffect(floatArrayOf(1.1f/factor(),12f/factor()),0f)
            paint.strokeWidth=3.5f/factor()
            paint.color=Color.rgb(0,112,190)
            c.drawPath(p,paint);paint.pathEffect=null
        }
        val radius=8f/factor()
        dot(c,person?.let{calibration.point(it.lat,it.lon)},Color.rgb(12,178,235),radius)
        dot(c,parked?.let{calibration.point(it.lat,it.lon)},Color.rgb(250,174,47),radius)
        for(g in 0..1)dot(c,calibration.mark(g),
            if(g==0)Color.rgb(55,175,105) else Color.rgb(180,85,175),radius*.85f)
        c.restore()
        if(!isCalibrated()){
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(19,37,56);paint.textSize=14*resources.displayMetrics.scaledDensity
            c.drawText("MAP CHUA DINH CHUAN TOA DO GPS",12f,32f,paint)
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        pinch.onTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;dragged=false;return true}
            MotionEvent.ACTION_POINTER_DOWN->{dragged=true;return true}
            MotionEvent.ACTION_MOVE->{
                if(e.pointerCount==1&&!pinch.isInProgress){
                    val dx=e.x-downX;val dy=e.y-downY
                    if(abs(dx)+abs(dy)>1f){
                        panX+=dx;panY+=dy
                        downX=e.x;downY=e.y
                        dragged=true;invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP->{
                if(!dragged && selectedGate in 0..1){
                    val p=src(e.x,e.y)
                    if(p.x in 0f..mapWidth&&p.y in 0f..mapHeight){
                        calibration.setGate(selectedGate,p.x,p.y)
                        val gate=selectedGate;selectedGate=-1
                        onGate?.invoke(gate)
                        setGps(person,parked,false)
                    }
                }
                performClick();return true
            }
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    // Pixel-level A*; gray blocks + user's charcoal walls are impassable.
    // White/yellow are passable; blue lanes have a small cost preference.
    private val nX=181
    private val nY=136
    private val blocked=BooleanArray(nX*nY)
    private val preferred=BooleanArray(nX*nY)
    init {
        bitmap?.let { b ->
            for(y in 0 until nY)for(x in 0 until nX){
                val cx=((x+.5f)*b.width/nX).toInt().coerceIn(0,b.width-1)
                val cy=((y+.5f)*b.height/nY).toInt().coerceIn(0,b.height-1)
                var grayVotes=0
                var roadVotes=0
                var wallVotes=0
                for(oy in -3..3 step 3)for(ox in -3..3 step 3){
                    val px=(cx+ox).coerceIn(0,b.width-1)
                    val py=(cy+oy).coerceIn(0,b.height-1)
                    val v=b.getPixel(px,py)
                    val r=Color.red(v);val g=Color.green(v);val bl=Color.blue(v)
                    // Walls are neutral dark. Dark-blue road edges must NOT be treated as walls.
                    if(r<110&&g<110&&bl<125&&abs(r-g)<18&&abs(g-bl)<21)wallVotes++
                    if(r in 168..240 && g in 169..242 && bl in 173..245 &&
                        abs(r-g)<13 && abs(g-bl)<17 && bl-r<19)grayVotes++
                    if(bl-r>=22&&bl-g>=8&&r in 60..207&&g in 85..226)roadVotes++
                }
                // Vote against antialiasing artifacts at the borders of yellow buildings.
                blocked[y*nX+x]=wallVotes>0 || (grayVotes>=5 && roadVotes<3)
                preferred[y*nX+x]=roadVotes>=2
            }
            // Expand forbidden interiors so diagonal grid paths cannot cut the corners.
            val original=blocked.copyOf()
            for(y in 0 until nY)for(x in 0 until nX) {
                if(original[y*nX+x])for(dy in -1..1)for(dx in -1..1){
                    val xx=x+dx;val yy=y+dy
                    if(xx in 0 until nX && yy in 0 until nY)blocked[yy*nX+xx]=true
                }
            }
        }
    }
    private fun pathfind(a:PointF,b:PointF):List<PointF> {
        if(bitmap==null)return emptyList()
        fun index(p:PointF):Int {
            val x=(p.x/mapWidth*nX).toInt().coerceIn(0,nX-1)
            val y=(p.y/mapHeight*nY).toInt().coerceIn(0,nY-1)
            return y*nX+x
        }
        fun nearest(s:Int):Int {
            if(!blocked[s])return s
            var best=-1;var cost=Float.POSITIVE_INFINITY
            val sx=s%nX;val sy=s/nX
            for(y in max(0,sy-8)..min(nY-1,sy+8))
              for(x in max(0,sx-8)..min(nX-1,sx+8)) {
                val u=y*nX+x
                if(blocked[u])continue
                val d=hypot((x-sx).toFloat(),(y-sy).toFloat())
                if(d<cost){cost=d;best=u}
              }
            return best
        }
        val start=nearest(index(a));val end=nearest(index(b))
        if(start<0||end<0)return emptyList()
        val dist=FloatArray(blocked.size){Float.POSITIVE_INFINITY}
        val from=IntArray(blocked.size){-1}
        val visited=BooleanArray(blocked.size)
        val q=PriorityQueue(compareBy<Pair<Int,Float>>{it.second})
        dist[start]=0f;q.add(start to 0f)
        val dx=intArrayOf(-1,0,1,-1,1,-1,0,1)
        val dy=intArrayOf(-1,-1,-1,0,0,1,1,1)
        while(q.isNotEmpty()){
            val u=q.remove().first
            if(visited[u])continue
            visited[u]=true
            if(u==end)break
            val x=u%nX;val y=u/nX
            for(k in 0..7){
                val xx=x+dx[k];val yy=y+dy[k]
                if(xx !in 0 until nX||yy !in 0 until nY)continue
                val v=yy*nX+xx
                if(blocked[v])continue
                if(dx[k]!=0&&dy[k]!=0){
                    if(blocked[y*nX+xx]||blocked[yy*nX+x])continue
                }
                val w=(if(dx[k]==0||dy[k]==0)1f else 1.4142f) *
                    (if(preferred[v]&&preferred[u]) .76f else 1f)
                val next=dist[u]+w
                if(next<dist[v]) {
                    dist[v]=next;from[v]=u
                    val h=hypot((xx-end%nX).toFloat(),(yy-end/nX).toFloat())*.76f
                    q.add(v to next+h)
                }
            }
        }
        if(!dist[end].isFinite())return emptyList()
        val arr=ArrayList<PointF>()
        var u=end
        while(u>=0){
            arr.add(PointF((u%nX+.5f)*mapWidth/nX,(u/nX+.5f)*mapHeight/nY))
            if(u==start)break
            u=from[u]
        }
        arr.reverse()
        return arr
    }
}
