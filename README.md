# Riders Pay — Auto Driver Android Beta

A fresh native Android project following the **“RIDERS PAY — Digital Partner for Auto Drivers”** presentation provided by the project owner. This is a new, standalone implementation, **not an update to an older APK**.

## Implemented (v0.3 floating meter beta source)

- Full yellow Tamil Nadu auto-inspired visual redesign using the supplied Riders Pay logo for the launcher and splash.
- Draggable yellow floating HUD over navigation apps with live speed, distance and fare. Long-press opens an inward, edge-aware translucent radial menu: Start, Arrived, Picked, Waiting, Dropped and Fare QR.
- Editable manual fare card: base fare, per-kilometre fare, minimum fare (saved in paise). The starting values are illustrative, NOT asserted to be legally approved local fares.
- Driver duty ON/OFF, GPS trip initiation, background foreground-service tracking, live trip distance / duration / estimated fare, GPS readiness indication.
- End trip → save ledger entry → amount-filled UPI QR to the driver’s configured UPI ID; optional manual “UPI received” or “cash received” ledger status.
- Local trip history and pending/recorded-paid totals. No account, cloud dependency or advertising SDK.

## Important boundaries

- **The app is not a certified fare meter.** GPS can be inaccurate in tunnels, poor signal, or dense urban areas. Check applicable regulations before displaying or collecting fare.
- Android's “display over other apps” permission is required for the floating meter. A persistent notification is shown while it is enabled. The translucent arc is glass-styled; true system-wide backdrop blur is not consistent across Android devices.
- A prefilled UPI QR **does not verify incoming money** and some UPI apps allow the sender to edit the amount. Confirm receipt in your own bank/UPI app. The app NEVER claims automatic settlement or escrow.
- The presentation’s **proposed 3% commission, commission cap/incentives, NEED RIDE booking, fair-queue dispatch, third-party platform integrations, and hardware smart meter/POS** remain future concepts; none are presented as operational here.
- All data is saved locally in Android SharedPreferences. Clearing app storage, uninstalling, or switching phones will remove local history. No recovery or export in this version. Location is collected only during a started trip; last coordinates are deleted when the trip ends.
- Tested fare/GPS arithmetic only in the supplied offline environment. An emulator and real-device GPS/payment smoke test remain necessary before public release.

## Build a debug APK

### Option 1 — GitHub Actions (no local Android setup)

1. Upload this folder to a **private GitHub repository** with the main branch and enable Actions.
2. Open **Actions → Android beta APK → Run workflow**.
3. After a successful run, open the workflow’s **Artifacts** section and download the generated debug APK artifact. Unzip and install `app-debug.apk` on a test Android device.

The Action uses JDK 17, Gradle 8.9, Android SDK 35, and outputs a **debug-signed APK**. A debug build is for private testing, not Play Store release. Do not publish a debug-signed app as production.

### Option 2 — Android Studio

Install Android Studio with Android SDK Platform 35, Build Tools 35.0.0, JDK 17 and Gradle 8.9. Open this folder as an existing project, sync dependencies, and run `:app:assembleDebug`. If Android Studio requires a Gradle wrapper, generate one with `gradle wrapper --gradle-version 8.9` or select your installed local Gradle 8.9 distribution. Result: `app/build/outputs/apk/debug/app-debug.apk`.

This source ZIP intentionally does **not** include downloaded Gradle distributions, Android SDK files, or private signing keys.

## Quick development checks

```bash
bash run-core-tests.sh
```

The script compiles and tests the platform-independent fare and GPS math with `javac` and `java`, without an Android SDK.

## Package details

- Application ID: `com.riderspay.autodriver`
- Java/native Android (no Flutter, no WebView); min SDK 26 (Android 8), target SDK 35.
- QR library: ZXing Core 3.5.3 (fetched during Gradle build).
- Permissions: fine/coarse location on ride start, foreground location tracking, display-over-other-apps for the floating meter, and optional notifications (Android 13+).

## Before wider beta distribution

Build the APK, run a clean-install smoke test on Android 8–16 as available, test GPS tracking with app in foreground/background, check Settings and UPI QR with a test amount, validate local fare rules, and define production signing and version-update policy. Do not embed payment gateway private keys in the app.
