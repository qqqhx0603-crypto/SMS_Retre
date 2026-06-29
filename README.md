# SMS-Retre

SMS-Retre is a self-use Android app for forwarding newly received SMS messages from a standby phone to an email inbox.

Current first-version assumptions:

- Android standby phone with SMS receiving permission granted.
- Email forwarding through QQ Mail SMTP over SSL (`smtp.qq.com:465`).
- Clash Meta for Android keeps global proxy enabled, while this app is excluded in Clash Access Control so SMTP uses direct network.
- Failed sends are retried for about five minutes, then kept as failed records for later review or manual retry.
- SMS mail includes the receiving SIM label configured in the app.
- A low-battery email alert is queued once when the standby phone is detected at 5% battery or lower. The alert resets after battery rises above 5%.

No mail credentials are stored in source code. Enter the QQ Mail address, SMTP authorization code, and destination mailbox in the app UI after installation.

## Installation and updates

For normal use, install the APK published in this repository's GitHub Releases. Do not build from source unless you intend to maintain your own signing key and update chain.

Android only allows an installed app to be updated by another APK with the same package name and the same signing key. This project uses the package name `com.smsretre.app`.

- If you installed an APK released by the project maintainer, install later APKs from the same maintainer to update in place and keep app data.
- If you build the APK yourself, Android treats your build as signed by your own local key. Your builds can update your own builds, but they cannot update APKs signed by another key.
- If Android reports a developer signature or package conflict, the installed app was signed with a different key. Back up or record the app settings, uninstall the old app, then install the new APK.

The repository intentionally does not include the maintainer's signing key. Keep signing keys private; uploading them would allow anyone with repository access to produce updates accepted by already installed devices.

## Local debug build

Install Android SDK to `D:\env\android-sdk`, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
```

The debug APK is written to `build\outputs\apk\debug\app-debug.apk` and copied to the project root as `SMS_Retre.apk` for easy transfer.

The debug signing key is fixed at `signing\sms-retre-debug.keystore` so later builds can be installed over earlier builds and preserve app data. This local key is ignored by Git and must not be uploaded to public repositories. Do not delete it after installing an APK built from this project.
