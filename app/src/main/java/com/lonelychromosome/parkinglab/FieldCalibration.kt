package com.lonelychromosome.parkinglab

import android.content.Context
import android.graphics.PointF
import kotlin.math.*

/**
 * Calibrates an unchanged raster using two real surveyed gates.
 * Local geodetic ENU metres -> similarity transform to map pixels.
 * No independent X/Y scale, shear or guessed pixel-per-metre allowed.
 */
class FieldCalibration(private val context: Context) {
    private val store = context.getSharedPreferences("fmb_map_calibration_1", Context.MODE_PRIVATE)
    private val reference = CampusFence.CENTERS
    data class Geo(val lat: Double,val lon: Double)
    fun has():Boolean=store.contains("g1x") && store.contains("g2x")
    fun setGate(gate:Int,x:Float,y:Float) {
        require(gate==0||gate==1)
        store.edit().putFloat("g${gate+1}x",x).putFloat("g${gate+1}y",y).apply()
    }
    fun mark(gate:Int):PointF? {
        val key="g${gate+1}"
        if(!store.contains(key+"x"))return null
        return PointF(store.getFloat(key+"x",0f),store.getFloat(key+"y",0f))
    }
    private data class Fit(val p0:PointF,val scaleX:Double,val scaleY:Double)
    private fun fit():Fit? {
        val g1=mark(0) ?: return null
        val g2=mark(1) ?: return null
        val dE=(reference[1][1]-reference[0][1])*111320.0*cos(Math.toRadians(reference[0][0]))
        val dN=(reference[1][0]-reference[0][0])*111320.0
        val l=dE*dE+dN*dN
        if(l<400.0) return null
        val dx=g2.x-g1.x.toDouble()
        val dy=g2.y-g1.y.toDouble()
        if(hypot(dx,dy)<80.0)return null
        val a=(dx*dE+dy*dN)/l
        val b=(dy*dE-dx*dN)/l
        return Fit(g1,a,b)
    }
    fun metrePerPixel():Double? {
        val f=fit() ?: return null
        val pxPerM=hypot(f.scaleX,f.scaleY)
        return if(pxPerM in .1..100.0)1.0/pxPerM else null
    }
    fun point(lat:Double,lon:Double):PointF? {
        val f=fit() ?: return null
        val m=metrePerPixel() ?: return null
        val e=(lon-reference[0][1])*111320.0*cos(Math.toRadians(reference[0][0]))
        val n=(lat-reference[0][0])*111320.0
        return PointF((f.p0.x+f.scaleX*e-f.scaleY*n).toFloat(),
            (f.p0.y+f.scaleY*e+f.scaleX*n).toFloat())
    }
    fun gps(pixel:PointF):Geo? {
        val f=fit() ?: return null
        val det=f.scaleX*f.scaleX+f.scaleY*f.scaleY
        if(det<1e-8)return null
        val dx=pixel.x-f.p0.x
        val dy=pixel.y-f.p0.y
        val e=(f.scaleX*dx+f.scaleY*dy)/det
        val n=(-f.scaleY*dx+f.scaleX*dy)/det
        return Geo(reference[0][0]+n/111320.0,
            reference[0][1]+e/(111320.0*cos(Math.toRadians(reference[0][0]))))
    }
}
