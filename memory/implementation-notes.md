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
- 2026-06-29: v0.2.0 increments `versionCode` to 2. Database migrated from v1 to v2 with `ALTER TABLE ADD COLUMN` only, preserving old queue records. Config keys for SMTP remain unchanged; SIM labels and battery alert state are additive SharedPreferences keys.
- 2026-06-29: debug signing key moved to persistent local `signing\sms-retre-debug.keystore`. Future builds must keep using this local key, otherwise Android will reject overwrite installs or require uninstalling, which would lose app-private config/data. The key must be ignored by Git and not uploaded to GitHub.
- 2026-06-29: build script copies the signed APK to project root as `SMS_Retre.apk` after each successful build for easier installation.
- 2026-06-29: installed pre-0.2.0 APKs may fail overwrite install with "developer signature" or "package conflicts" because those builds used a temporary build-directory debug keystore that was later deleted. Without the old private signing key, Android cannot preserve that installed app data through an overwrite install. From 0.2.0 onward, keep `signing\sms-retre-debug.keystore` to preserve upgrade compatibility.
- 2026-06-30: v0.3.1 removes the attempted inbox fallback/manual recent-SMS scan path and keeps the app broadcast-only without `READ_SMS`. SMS receiver compatibility now accepts both `SMS_RECEIVED` and `SMS_DELIVER`, uses maximum receiver priority, and falls back to manual PDU parsing only when framework parsing returns no messages. Battery alert threshold is 10%.
- 2026-06-30: v0.3.3 rolls back the notification-listener fallback because it did not help on the user's device. Version code is still increased so it can overwrite a locally installed v0.3.2 build while preserving data. Battery alert remains 10%.
- 2026-06-30: v0.4.1 rolls back default-SMS-app role support after vivo kept the stock messaging app active and SMS-Retre stopped forwarding. The working fix for the user's device is to disable vivo Messages' verification-code privacy/safety protection that blocks third-party apps from reading or using verification code messages.
- 2026-06-30: v0.4.2 adds a periodic inbox scan job. It requires `READ_SMS`, runs with JobScheduler every 60 minutes, scans only SMS inbox rows not already represented in `sms_queue`, sends one aggregate email with all missed message contents, and records successfully summarized messages as sent so later scans do not repeat them. First scan looks back about 65 minutes instead of the full inbox history.
