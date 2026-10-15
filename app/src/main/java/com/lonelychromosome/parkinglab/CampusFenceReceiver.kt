package com.lonelychromosome.parkinglab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class CampusFenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ClassSchedule.inWindow(context)) return
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            CampusFence.record(context, "Lỗi Google Geofence: " + (event?.errorCode ?: -1))
            return
        }
        val ids = event.triggeringGeofences?.joinToString(",") { it.requestId }.orEmpty()
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                CampusFence.suppress(context, false)
                CampusFence.record(context, "Rời vùng: $ids")
            }
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                CampusFence.record(context, "Vào vùng: $ids")
                if (CampusFence.suppressed(context) || TripStore.running(context) ||
                    ClassSchedule.alreadyTriggered(context)) return
                if (!CampusFence.ready(context)) return
                try {
                    context.startForegroundService(
                        Intent(context, TrackingService::class.java).setAction(TrackingService.AUTO_START))
                    CampusFence.suppress(context, true)
                } catch (e: Exception) {
                    CampusFence.record(context, "Không khởi động được GPS: " + e.javaClass.simpleName)
                }
            }
        }
    }
}
