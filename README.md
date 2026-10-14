# BPA Parking Lab — Find My Bike v0.3

Android-native proof of concept in **LonelyChromosome/abc** (branch `feature/find-my-bike-v0.3`). Does not modify DemoF3.

## Behavior

1. From the app screen, grant location, optional activity-recognition, notification posting and Notification Access (a **separate** Android settings switch). Tap **Bật Find My Bike** near the university entrance.
2. The app starts a user-initiated **location-type foreground service**, but **does not request GPS yet**. The existing notification listener waits up to **10 seconds** for a system notification from `phx.smartschool.phenikaauniversity` matching an **entry** phrase.
3. Matching within 10 seconds triggers GPS tracking. Otherwise the foreground service ends and an actionable local notification asks the user to open the app and manually start tracking. A late push will not overwrite a manual session.
4. During tracking the app keeps an in-memory rolling buffer of **10 seconds** of location fixes. A low-speed stop (not just any single GPS coordinate) followed by three step-detector events after at least three seconds attempts to save the stop position. **This heuristic needs actual field tests and is not guaranteed accurate.** If the phone lacks a step detector, the user declines Activity Recognition, or auto-detection is uncertain, tap **Lưu vị trí dựng xe ngay**.
5. Saved coordinates persist on the device. **Dẫn đường** opens the official Google Maps application with walking directions, with a browser fallback.
6. The in-app Google Maps SDK map requires a **Maps SDK for Android API key** and Google Cloud project billing. In GitHub Actions, add secret `MAPS_API_KEY` and rerun the workflow. Without a key, the app **explicitly displays a placeholder description**, not a pretend map; tracking and opening Maps still work.

### Important validation points

- A notification is **not a shared FCM feed**. The app reads Android-posted notifications only, using user-granted Notification Access. Match rules in `GateNotificationMatcher.kt` must be verified against a **real Phenikaa Uni entry notification**. Text/content may be redacted or phrased differently.
- The fake notification tests the listener, whereas **TEST: Giả lập tín hiệu vào cổng** simulates a successful gate notification while Find My Bike is waiting.
- Begin the foreground service while the Activity is visible. Android 14+ imposes while-in-use location/FGS restrictions on background starts.
- The saved GPS accuracy is an estimate from Android location, not 1–2m guaranteed parking precision.
- A 10-second window is a user-selected fallback based on reported delivery times, not a guarantee that all pushes arrive by then.
- No map matching, NFC, server API, background GPS polling during standby, or persistent tracking after saving.
- Notification content is processed transiently for event matching, never stored; the audit log retains package and timing only.
- v0.1 standby test and v0.2 notification listener diagnostics remain accessible at the bottom.
- GitHub Actions uses a fresh **debug signing key** per runner. Existing debug versions may need uninstalling before installation. Production needs a persistent, securely stored signing key.

## Build

```sh
gradle :app:assembleDebug --no-daemon
```

Set `MAPS_API_KEY` in the environment only if Maps SDK is configured.
