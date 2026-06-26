# SMS-Retre implementation notes

## 2026-06-26 first design

- The forwarding channel is email only. QQ channel bot and SMS-to-SMS forwarding were rejected for first version.
- `smtp.qq.com:465 SSL` means the app opens an SSL/TLS socket to QQ Mail's SMTP server on TCP port 465 and authenticates with the QQ Mail SMTP authorization code.
- The app should be excluded from Clash Meta for Android Access Control so it can use direct network while other apps keep global proxy.
- Retry policy is intentionally short: immediate send plus once per minute for around five minutes. Long-running background retries are not part of v1.
- vivo/OriginOS may restrict background work. v1 uses a short foreground service for the retry window and falls back to Android `JobScheduler` if foreground service start is rejected.
- Failed records remain queryable in the app and can be manually reset to pending for another five-minute retry window.
- Android SDK is installed at `D:\env\android-sdk`. Because Gradle is not installed, v1 has a manual debug build script at `scripts/build-debug.ps1` that uses Android SDK build-tools directly.
- If saving the SMTP authorization code fails with `caller-provided IV not permitted`, Android Keystore rejected caller-supplied AES-GCM IV. Encryption must call `cipher.init(ENCRYPT_MODE, key)` and persist `cipher.getIV()` with the ciphertext.
