# BPA Parking Lab — Find My Bike v0.3.1

Android-native proof of concept in **LonelyChromosome/abc** (branch `feature/find-my-bike-v0.3`). Does not modify DemoF3.

## Behavior

1. From the app screen, grant location, optional activity-recognition, notification posting and Notification Access (a **separate** Android settings switch). Tap **Bật Find My Bike** near the university entrance.
2. The app starts a user-initiated **location-type foreground service**, but **does not request GPS yet**. The existing notification listener waits up to **10 seconds** for a system notification from `phx.smartschool.phenikaauniversity` matching an **entry** phrase.
3. Matching within 10 seconds triggers GPS tracking. Otherwise the foreground service ends and an actionable local notification asks the user to open the app and manually start tracking. A late push will not overwrite a manual session.
4. During tracking the app writes **the entire GPS route** from the first accepted fix until the user parks (one time-stamped CSV point per accepted GPS fix, under app-private `files/bike_routes/`). It never truncates the route to 10 seconds. The **only 10-second timer is the notification wait**. A low-speed stop (not just any single GPS coordinate) followed by three step-detector events after at least three seconds attempts to save the stop position. **This heuristic needs actual field tests and is not guaranteed accurate.** If the phone lacks a step detector, the user declines Activity Recognition, or auto-detection is uncertain, tap **Lưu vị trí dựng xe ngay**.
5. Saved coordinates and the whole GPS route persist on the device. If a Maps SDK key is configured, the in-app Google Map displays the entire saved route as a polyline. **Dẫn đường** opens the official Google Maps application with walking directions, with a browser fallback.
6. The in-app Google Maps SDK map requires a **Maps SDK for Android API key** and Google Cloud project billing. In GitHub Actions, add secret `MAPS_API_KEY` and rerun the workflow. Without a key, the app **explicitly displays a placeholder description**, not a pretend map; tracking and opening Maps still work.

### Important validation points

- A notification is **not a shared FCM feed**. The app reads Android-posted notifications only, using user-granted Notification Access. Match rules in `GateNotificationMatcher.kt` must be verified against a **real Phenikaa Uni entry notification**. Text/content may be redacted or phrased differently.
- **FAKE NOTIFICATION PANEL** exposes entry, exit, unrelated, duplicate, no-push, and 12-second-late scenarios. Each synthetic event posts a **real Android notification from Parking Lab** (clearly marked FAKE), and separately feeds its content through the shared parser and tracking-service event action. It does **not** spoof the Phenikaa Uni package or prove that real third-party notification access works. The preserved v0.2 fake button tests the actual Android listener.
- Begin the foreground service while the Activity is visible. Android 14+ imposes while-in-use location/FGS restrictions on background starts.
- The saved GPS accuracy is an estimate from Android location, not 1–2m guaranteed parking precision.
- The 10-second window is **only for notification timeout**, based on reported delivery times. Whole-trip tracking continues until parking.
- No map matching, NFC, server API, background GPS polling during standby, or persistent tracking after saving.
- Notification content is processed transiently for event matching, never stored; the audit log retains package and timing only. Whole-trip GPS points are stored locally as sensitive data and are never uploaded.
- v0.1 standby test and v0.2 notification listener diagnostics remain accessible at the bottom.
- GitHub Actions uses a fresh **debug signing key** per runner. Existing debug versions may need uninstalling before installation. Production needs a persistent, securely stored signing key.

## Build

```sh
gradle :app:assembleDebug --no-daemon
```

Set `MAPS_API_KEY` in the environment only if Maps SDK is configured.
