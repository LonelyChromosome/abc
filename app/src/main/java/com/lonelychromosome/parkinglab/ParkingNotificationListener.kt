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
        if (!matches || !FindMyBikeStore.withinWindow(this)) return
        // Ignore notifications posted before this scheduled/manual waiting session.
        if (sbn.postTime < FindMyBikeStore.waitingStartedWall(this) - 1_000L) return
        if (!FindMyBikeStore.serviceReady(this)) {
            ScheduledGateController.setEvent(this,
                "Nhận xe vào nhưng Android chưa cho service nền hoạt động")
            ScheduledGateController.notifyAction(this,
                "Đã nhận thông báo xe vào",
                "Chạm để mở BPA và bắt đầu tracking GPS.")
            return
        }
        try {
            // Send to the ALREADY-RUNNING location foreground service.
            startService(Intent(this, BikeTrackingService::class.java)
                .setAction(BikeTrackingService.GATE))
        } catch (e: Exception) {
            ScheduledGateController.setEvent(this,
                "Không thể chuyển thông báo tới GPS: " + e.javaClass.simpleName)
            ScheduledGateController.notifyAction(this,
                "Find My Bike cần mở app", "Thông báo xe vào đã đến; chạm để bật GPS.")
        }
    }
}
