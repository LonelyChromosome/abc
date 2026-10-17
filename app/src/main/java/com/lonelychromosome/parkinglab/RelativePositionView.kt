package com.lonelychromosome.parkinglab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/**
 * White, geographical relative-position canvas.
 * Two GPS points only: user's live fix and the saved parked location.
 * North remains up; no map imagery, snapping, lanes, routes or simulated points.
 */
class RelativePositionView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var person: GeoPoint? = null
    private var bike: GeoPoint? = null
    private val ink = Color.rgb(21, 43, 59)
    private val blue = Color.rgb(19, 154, 235)
    private val amber = Color.rgb(244, 164, 40)

    fun setPositions(current: GeoPoint?, parked: GeoPoint?) {
        person = current
        bike = parked
        invalidate()
    }

    private fun dp(v: Float) = v * density

    private fun text(canvas: Canvas, value: String, x: Float, y: Float,
                     color: Int = ink, sizeSp: Float = 13f, bold: Boolean = false) {
        p.reset()
        p.isAntiAlias = true
        p.color = color
        p.textAlign = Paint.Align.CENTER
        p.textSize = sizeSp * resources.displayMetrics.scaledDensity
        p.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD
                     else android.graphics.Typeface.DEFAULT
        canvas.drawText(value, x, y, p)
    }

    private fun pin(canvas: Canvas, x: Float, y: Float, color: Int, title: String,
                    labelBelow: Boolean) {
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = Color.argb(24, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(x, y, dp(28f), p)
        p.color = Color.WHITE
        canvas.drawCircle(x, y, dp(13.5f), p)
        p.color = color
        canvas.drawCircle(x, y, dp(10f), p)
        text(canvas, title, x, y + (if (labelBelow) dp(44f) else -dp(37f)),
            ink, 13f, true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val w = width.toFloat()
        val h = height.toFloat()
        if(w <= 0f || h <= 0f) return
        val u = person
        val b = bike
        if (b == null) {
            text(canvas, "CHƯA CÓ VỊ TRÍ XE", w/2f, h/2f, ink, 15f, true)
            return
        }
        if (u == null) {
            pin(canvas, w/2f, h/2f, amber, "XE ĐỖ", false)
            text(canvas, "Đang chờ tọa độ GPS hiện tại", w/2f, h/2f + dp(88f),
                 Color.rgb(104,122,140), 12f)
            return
        }

        val result = FloatArray(3)
        Location.distanceBetween(u.lat, u.lon, b.lat, b.lon, result)
        val metres = result[0].coerceAtLeast(0f)
        val bearingRadians = Math.toRadians(result[1].toDouble())
        val east = sin(bearingRadians).toFloat() * metres
        val north = cos(bearingRadians).toFloat() * metres
        val availableW = max(dp(40f), w - dp(155f))
        val availableH = max(dp(40f), h - dp(175f))
        val ppm = min(4.5f * density, min(
            availableW / max(abs(east), 1f),
            availableH / max(abs(north), 1f)
        ))
        val midX = w * 0.5f
        val midY = h * 0.5f
        val bikeX = midX + east * ppm / 2f
        val bikeY = midY - north * ppm / 2f
        val youX = midX - east * ppm / 2f
        val youY = midY + north * ppm / 2f

        if(metres < 5f) {
            // Keep both true positions, not an artificially expanded distance.
            pin(canvas, bikeX, bikeY, amber, "XE ĐỖ", false)
            pin(canvas, youX, youY, blue, "BẠN", true)
        } else {
            pin(canvas, bikeX, bikeY, amber, "XE ĐỖ", false)
            pin(canvas, youX, youY, blue, "BẠN", true)
        }
        text(canvas, "BẮC ↑", w/2f, dp(34f), Color.rgb(137,150,162), 11f, true)

        // A scale indicator explains auto-fit; it is not a walking route.
        val values = floatArrayOf(1f,2f,5f,10f,20f,50f,100f,200f,500f,1000f)
        var scaleMetres = values[0]
        for (v in values) if(v*ppm <= dp(95f)) scaleMetres = v
        val scaleWidth = scaleMetres * ppm
        val sx = dp(25f)
        val sy = h-dp(38f)
        p.reset()
        p.isAntiAlias = true
        p.color = Color.rgb(140,153,167)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.6f)
        canvas.drawLine(sx,sy,sx+scaleWidth,sy,p)
        canvas.drawLine(sx,sy-dp(4f),sx,sy+dp(4f),p)
        canvas.drawLine(sx+scaleWidth,sy-dp(4f),sx+scaleWidth,sy+dp(4f),p)
        text(canvas,"${scaleMetres.toInt()} m",sx+scaleWidth/2f,sy-dp(9f),
            Color.rgb(117,131,144),11f)
    }
}
