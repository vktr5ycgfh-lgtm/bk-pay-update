# RIDERS PAY + NEED RIDE

Production-oriented Android/Firebase monorepo:

- `driver-app`: RIDERS PAY driver app, Google Maps navigation, draggable HUD overlay, full-screen dispatch alert, live coordinate upload.
- `consumer-app`: NEED RIDE booking, nearby autos, route/fare preview, ride progress, live tracking and UPI QR settlement.
- `functions`: transactional stand FIFO dispatcher, FCM offers, 20-second task-queue cascade, accept/decline and completion re-queue.

## Required configuration

1. Create one Firebase project and two Android apps: `com.riderspay.driver` and `com.riderspay.consumer`. Put each downloaded `google-services.json` in its app module.
2. Enable Firebase Authentication, Firestore, Cloud Messaging, Cloud Functions, Cloud Tasks, Google Maps SDK for Android, Places API and Routes API.
3. Add `MAPS_API_KEY=...`, `UPI_ID=driver@bank`, and `DEFAULT_STAND_ID=your_registered_stand` to the root `local.properties` (never commit it).
4. Set Firebase Auth custom claim `role` to `driver` or `customer`. Cloud Functions are the only writers of dispatch/ride-state fields.
5. From `functions`: `npm install && npm run build && firebase deploy --only functions,firestore`. Commit the generated lockfile before CI/production deployment.
6. Build Android apps with JDK 17 and Gradle 8.10+: `gradle :driver-app:assembleDebug :consumer-app:assembleDebug` (or generate/use a Gradle wrapper).

## Production gates

This repository deliberately contains no private signing key, Maps key, Firebase service credential, payment-provider secret or real UPI ID. Before release: add App Check, production signing through CI secrets, crash reporting, privacy/retention policy, Places/Routes billing limits, real-device Android 8–16 tests, background-location disclosure where applicable, Play Console full-screen-intent declaration, and local fare-meter/transport approvals. UPI QR is direct settlement and does not prove receipt; confirm payment independently.
