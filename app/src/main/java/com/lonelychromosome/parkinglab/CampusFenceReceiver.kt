package com.lonelychromosome.parkinglab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Transition delivered by Google Play services; no always-on timer or GPS polling. */
class CampusFenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!CampusFence.enabled(context)) return
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            CampusFence.record(context, "Lỗi geofence " + (event?.errorCode ?: -1))
            return
        }
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                CampusFence.suppress(context, false)
                CampusFence.record(context, "Rời vùng 100m, sẵn sàng lượt mới")
            }
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                CampusFence.record(context, "Google báo vào vùng trường")
                if (TripStore.running(context) || CampusFence.suppressed(context)) return
                if (!CampusFence.canRun(context)) {
                    CampusFence.record(context, "Vào vùng nhưng thiếu quyền Vị trí mọi lúc")
                    return
                }
                try {
                    context.startForegroundService(Intent(context, TrackingService::class.java)
                        .setAction(TrackingService.START))
                    CampusFence.suppress(context, true)
                } catch (e: Exception) {
                    CampusFence.record(context, "Chưa bật được tracking: " + e.javaClass.simpleName)
                }
            }
        }
    }
}

/** Re-register a persistent user-opted-in fence after reboot/app upgrade. */
class CampusFenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (CampusFence.enabled(context)) CampusFence.register(context)
        }
    }
}
