# BK PAY Update Channel

This public repository hosts the BK PAY online update metadata.

Current updater build: **v1.5** (versionCode 6)

- Update feed: `update.js`
- `forceUpdate` is currently **false**
- The APK itself is not committed here yet.
- Keep the signing key private. Never upload the private signing source/keystore to this public repository.

Future release flow:
1. Build a new APK with the same package name and signing certificate.
2. Host that APK at a stable HTTPS URL.
3. Update `versionCode`, `versionName`, `apkUrl`, and `forceUpdate` in `update.js`.
