package com.lonelychromosome.parkinglab

import android.content.Context
import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Reference georeferencing measured against Google Maps z18.
 *
 * Approved map size 1448 x 1086. Scale 0.385223m/pixel.
 * Transformation is a SIMILARITY (constant scale + rotation + translation).
 * Pixel origin derives from a mapped building reference, not a true surveyed GNSS point.
 * The ~3% reference-scale uncertainty must not be hidden from the field test.
 */
class FieldCalibration(private val context: Context) {
    companion object {
        const val METRES_PER_PIXEL=0.385223
        const val REFERENCE_LAT=20.96063
        const val REFERENCE_LON=105.74547
        const val ORIGIN_PIXEL_X=235.47
        const val ORIGIN_PIXEL_Y=642.29
        private const val AE=2.595804
        private const val AN=-0.021822
        private const val BE=-0.021822
        private const val BN=-2.595804
        private const val METRES_PER_LAT=111320.0
        private const val METRES_PER_LON=111320.0*0.93386
    }
    data class Geo(val lat:Double,val lon:Double)
    /** The map has a built-in, *reference-grade* georeference, no manual gate clicks. */
    fun has()=true
    fun metrePerPixel():Double=METRES_PER_PIXEL
    fun point(lat:Double,lon:Double):PointF {
        val east=(lon-REFERENCE_LON)*METRES_PER_LAT*cos(Math.toRadians(20.9611))
        val north=(lat-REFERENCE_LAT)*METRES_PER_LAT
        return PointF((ORIGIN_PIXEL_X+AE*east+AN*north).toFloat(),
            (ORIGIN_PIXEL_Y+BE*east+BN*north).toFloat())
    }
    fun gps(p:PointF):Geo {
        val dx=p.x-ORIGIN_PIXEL_X
        val dy=p.y-ORIGIN_PIXEL_Y
        val det=AE*BN-AN*BE
        val east=(BN*dx-AN*dy)/det
        val north=(-BE*dx+AE*dy)/det
        return Geo(REFERENCE_LAT+north/METRES_PER_LAT,
            REFERENCE_LON+east/(METRES_PER_LAT*cos(Math.toRadians(20.9611))))
    }
    fun validPixel(p:PointF):Boolean = p.x>=0f && p.x<=1448f && p.y>=0f && p.y<=1086f
}
