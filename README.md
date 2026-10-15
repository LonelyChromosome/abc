# BPA Parking Lab v0.4 — Google Play Services geofence

## Auto flow
- On first run, tap **BẬT TỰ ĐỘNG 100M**, grant precise location and in Settings choose **Allow all the time**. This is explicit opt-in.
- Geofence center: 20.96110, 105.74746, a publicly mapped Phenikaa University reference near the Google Maps place ID `ChIJ40w5_-9SNDERZORbMtQ5Gjk`. Radius is 100 metres **from this point, not from the university perimeter**. The point is approximate and not independently verified identical to Google's own pin coordinates.
- Google Play Services monitors transitions without Parking Lab polling GPS in the waiting stage.
- On ENTER, Android attempts to start the user-authorized location foreground service for the existing GPS route recorder. Geofencing events may be significantly delayed while backgrounded: 100m is not an instantaneous promise.
- Once tracking, app suggests a stop, user confirms parking to save the route and GPS coordinate. Google Maps opens pedestrian navigation to the saved coordinate. A manual Start option remains.
- Suppress new auto starts until receiving an EXIT event after a trip, to avoid repeated starts on stop/save.
- Re-register geofence on reboot or app update, while opt-in remains enabled.

## Privacy and caveats
- No Phenikaa app access, login, push, API, backend, GPS while waiting, or internet permission in Parking Lab. No server-side tracking.
- Google Play Services may use sensors/location internally for its geofencing.
- On Android 10+, background geofencing requires background location; on Android 14+, launching a location FGS from the background requires background location plus applicable exception. User grants these manually.
- The automatic start may be delayed, suppressed by OS/OEM, or not trigger when the mapped center differs from the gate.
- Last route saved privately as CSV, GPX export available. Google Maps is for navigation to the point; it does not follow the original trip polyline.
- Debug APK signing might differ across CI runs.

Source: https://www.google.com/maps/place/?q=place_id:ChIJ40w5_-9SNDERZORbMtQ5Gjk

Repo: LonelyChromosome/abc. DemoF3 is not modified.
