# BPA Parking Lab — Notification Test v0.2

Standalone native Android test project. Does NOT change DemoF3.

## Step 1 — preserved
Tap **KÍCH HOẠT**, let the foreground timer service run. Swipe from Recents, lock and reopen. Elapsed time and heartbeat/restarts/task removal are retained from v0.1.

## Step 2 — new
Tap **CẤP QUYỀN ĐỌC THÔNG BÁO**, enable **Parking Lab - Notification Test** in Android's **Notification Access** settings, and return.
Tap **GỬI THÔNG BÁO FAKE**. This posts a genuine Android system notification, not an in-app simulation.
The listener registers FAKE; **standby foreground notification refreshes are ignored**.
Have a second app deliver a new notification (e.g. a test chat from another phone); the app stores type EXTERNAL, source package, timestamp and approximate delivery delay.
Close Parking Lab, swipe it from Recents and lock the phone, then send a second external notification and reopen the app to inspect counters.
Your test data is stored locally only. **External notification title and body are intentionally NOT saved.**
No GPS, tracking, walking recognition, calendar, alarms, backend or network permissions are added.

### Notes
- Notification Access is a separate Android Settings grant; POST_NOTIFICATIONS does not grant it.
- The notification listener depends on Android and OEM restrictions. Some notifications (e.g., protected content) may be filtered/redacted.
- GitHub Actions debug APK signing certificates are ephemeral. If Android rejects installing v0.2 over v0.1 due to conflicting signatures, uninstall v0.1 first.

GitHub Actions builds debug APK on main.
