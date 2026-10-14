# BPA Parking Lab — Standby test v0.1

Standalone Android APK for testing a foreground standby service. **No DemoF3 changes.**

## Scope
- Tap **KÍCH HOẠT** to start the service and stopwatch.
- Swipe the app out of Recents, lock the screen, and reopen the app.
- Stopwatch uses monotonic elapsed time, so elapsed time alone is *not evidence* the service ran continuously.
- The service stores heartbeat counts every ~10 seconds. The screen shows heartbeat age, restarts, and Recents removal time.
- Tap **DỪNG / ĐẶT LẠI** to stop.
- No GPS, alarms, notifications from another app, QLĐT, sensors, or navigation.

## Test
1. Install debug APK from GitHub Actions artifact `BPA-Parking-Lab-Standby-v0.1`.
2. Grant notifications (optional for the service but recommended to see its persistent notification).
3. Tap KÍCH HOẠT; wait ~20 seconds and record timer / heartbeat.
4. Swipe away from Recents, lock screen for several minutes, unlock and reopen.
5. Confirm timer advanced, service notification survived, and heartbeat count advanced after task removal. Deep sleep may delay heartbeats; examine gaps rather than assuming failure.
6. Force stop from Android Settings deliberately to verify the documented limitation: no app can keep running after a force stop.

## Build
Github Actions workflow builds debug APK on push to main using JDK 17, Android SDK 35, Gradle 8.9, Kotlin 2.0.21 and Android Gradle Plugin 8.7.3.

Only testing; no networking or telemetry.
