package com.lonelychromosome.parkinglab

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Notification test only; does not turn on GPS or track movement. */
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
        val isOwn = sbn.packageName == packageName
        // Ignore timer foreground service updates; they occur every 10 seconds.
        if (isOwn && sbn.id != 2001) return
        NotificationStore.record(this, sbn.packageName, isOwn, sbn.postTime)
    }
}
