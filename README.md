# SMS-Retre

SMS-Retre is a self-use Android app for forwarding newly received SMS messages from a standby phone to an email inbox.

Current first-version assumptions:

- Android standby phone with SMS receiving permission granted.
- Email forwarding through QQ Mail SMTP over SSL (`smtp.qq.com:465`).
- Clash Meta for Android keeps global proxy enabled, while this app is excluded in Clash Access Control so SMTP uses direct network.
- Failed sends are retried for about five minutes, then kept as failed records for later review or manual retry.

No mail credentials are stored in source code. Enter the QQ Mail address, SMTP authorization code, and destination mailbox in the app UI after installation.

## Local debug build

Install Android SDK to `D:\env\android-sdk`, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
```

The debug APK is written to `build\outputs\apk\debug\app-debug.apk`.
