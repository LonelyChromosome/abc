package com.lonelychromosome.parkinglab

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Receives system-posted notices; never reads another app's private FCM messages. */
class ParkingNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationStore.setConnected(this, true)
    }

    override fun onListenerDisconnected() {
        NotificationStore.setConnected(this, false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val own = sbn.packageName == packageName
        // Ignore our own foreground service updates (v0.1 timer and v0.3 tracking).
        if (own && sbn.id != 2001) return

        NotificationStore.record(this, sbn.packageName, own, sbn.postTime)

        if (sbn.packageName != GateNotificationMatcher.PHENIKAA_PACKAGE) return
        val matches = GateNotificationMatcher.isGateEntry(
            sbn.packageName, sbn.notification.extras)
        NotificationStore.notePhenikaa(this, matches)
        if (matches && FindMyBikeStore.mode(this) == FindMyBikeStore.WAITING) {
            // The user already started BikeTrackingService in the foreground.
            // Do not attempt to launch a new location FGS from a background listener.
            try {
                startService(Intent(this, BikeTrackingService::class.java)
                    .setAction(BikeTrackingService.GATE))
            } catch (_: Exception) {
                // The running service can be missing if Android killed the app.
                // No fabricated tracking success.
            }
        }
    }
}
