# BPA Parking Lab 0.5 — custom schedule, two Google geofences, GPS + walking

Only repo `LonelyChromosome/abc`. Not integrated with BPA, QLDT, DemoF3, Phenikaa Uni or any external backend.

Flow: enter a weekly custom lesson schedule as lines like `T2 07:30`, `T4 13:00` or `CN 08:00`. Enable scheduled waiting (requires Android precise location, **Allow all the time** background location, Physical activity for auto confirmation). About 30 minutes before class start, arm two Google Play Services geofences: 100m around (20.962668790883644, 105.74871859259113), and 100m around (20.959607109088672, 105.74789745697416). Remain armed until 60 minutes after class start to accommodate arrivals. Event ENTER triggers GPS location foreground service. If GPS route is at least 35m, vehicle remains slow and inside 12m for about 6s, mark parked-candidate. After 10 real seconds of consecutive step-detector events and >=8 steps, reverse-scan the stationary GPS cluster *before walking* and save bike position and track automatically. Stop GPS and allow Google Maps walking navigation to saved position. Buttons remain to manually start or save where GPS or sensors are unreliable. Saved last route is GPX-exportable.

Limitations:
- These are two 100m radii around coordinates supplied by the user, **not** entire campus perimeter. Geofencing is best effort, can arrive late under OS restrictions, and 100m is not a guaranteed instant threshold. Google Play Services owns location sampling while waiting; Parking Lab itself does not poll GPS continuously.
- Calendar scheduling uses AlarmManager; Android may delay inexact alarms unless exact-alarm privileges are granted. Class times are custom/local, not linked to actual BPA.
- Step detector may not exist on all phones and ACTIVITY_RECOGNITION is required on Android 10+. No proof of exact position to within 1–2m. Reverse interpolation is a robust median of low-speed GPS points, not a physical guarantee.
- If foreground service is blocked by Android/OEM battery saver, auto tracking may fail. Inspect logs and use manual fallback.
- Android app stays in the same package; debug APK signing can vary in CI.

Build workflow: `.github/workflows/android.yml`.
