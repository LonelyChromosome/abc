# BPA Parking Lab — GPS v0.3

Android Kotlin standalone demo. Main flow: wait for manual gate confirmation -> record GPS -> detect likely stopped -> manually save parking coordinate and track -> navigate to saved coordinate with Google Maps.

- No Phenikaa login, backend, push listener, accelerometer or step-counter.
- No location usage while waiting or after saving. Location foreground service remains visible while active (also with screen locked).
- Stop is only a suggestion: ~25 seconds stationary within 14m after about 35m of recorded travel. The user must manually save to avoid false parking stops at lights.
- The GPS history is saved locally as CSV in private storage; latest trip may be exported as GPX.
- Google Maps walking navigation goes to the saved point; Google Maps does NOT necessarily follow the recorded GPS line.
- GPS accuracy indoors/covered parking may be insufficient. Android/OEM can kill services or deny precision; no 1–2m guarantee.
- Debug APK signing can differ between CI runners. If install over previous APK fails, export GPX then uninstall older Parking Lab and reinstall.

This repo is isolated from DemoF3.
