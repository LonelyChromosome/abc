package com.lonelychromosome.parkinglab

import android.content.Context
import android.location.Location

/** Real GPS fixes only. Never seed with a simulated start or reference trace. */
object NavState {
    private fun p(c:Context)=c.getSharedPreferences("fmb_navigation_live_1", Context.MODE_PRIVATE)
    fun active(c:Context)=p(c).getBoolean("active",false)
    fun started(c:Context) {p(c).edit().putBoolean("active",true).remove("endedAt").remove("lat").remove("lon").remove("fixTime").apply()}
    fun ended(c:Context){p(c).edit().putBoolean("active",false).apply()}
    fun fix(c:Context,location:Location){
        if(!location.hasAccuracy()||location.accuracy>40f)return
        p(c).edit().putString("lat",location.latitude.toString()).putString("lon",location.longitude.toString())
            .putFloat("accuracy",location.accuracy).putLong("fixTime",System.currentTimeMillis()).apply()
    }
    fun location(c:Context):GeoPoint? {
        if(System.currentTimeMillis()-p(c).getLong("fixTime",0L)>15_000L)return null
        val lat=p(c).getString("lat",null)?.toDoubleOrNull()?:return null
        val lon=p(c).getString("lon",null)?.toDoubleOrNull()?:return null
        return GeoPoint(lat,lon)
    }
    fun accuracy(c:Context)=p(c).getFloat("accuracy",-1f)
    fun secondsSinceFix(c:Context):Long {
        val t=p(c).getLong("fixTime",0L)
        return if(t==0L)Long.MAX_VALUE else ((System.currentTimeMillis()-t)/1000L).coerceAtLeast(0L)
    }
}
