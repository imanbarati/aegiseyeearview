# Aegis Eye

Offline-first **tactical map** Android app (Kotlin + Jetpack Compose + osmdroid).

## What works (on device)

- OpenStreetMap tiles (osmdroid) with disk cache
- Local GeoJSON threat / exclusion / evacuation layers under `app/src/main/assets/tactical/`
- Pathfinding helpers and compass / orientation sensors
- Location permission + foreground proximity service wiring
- Room-backed local storage for breadcrumbs / emergency notes

## What is *not* claimed

- Not a live military / OSINT feed product
- Not dependent on Gemini for core map functionality (AI Studio scaffolding was removed from release claims)
- Threat polygons are **bundled sample data** unless you replace the GeoJSON assets

## Application ID

`com.aegiseye.app` (version **1.1.0**)

Kotlin source packages remain under `com.example.*` for now; only the installed app id changed.

## Build APK (GitHub Actions)

1. **Actions → Build Android APK → Run workflow**
2. Wait for success
3. Download from **Releases** or the run artifact `aegis-eye-debug-apk`

Debug builds are signed with the standard Android debug keystore (installable for sideload testing).

Optional release signing: set repo secrets `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` and provide a keystore in CI.

## Local build

Requires Android SDK, JDK 17+, and Gradle wrapper (generate if missing):

```bash
gradle wrapper   # if ./gradlew is missing
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/`

## License / data

Map tiles: respect OpenStreetMap tile usage policy for any public deployment.
Replace sample tactical GeoJSON with your own data for real operations.
