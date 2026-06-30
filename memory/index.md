# SMS-Retre memory index

## Project goal

Build a self-use Android app that runs on the standby phone and forwards newly received SMS messages to the user's main phone through email.

## First-version requirements

- Project root: `D:\chatgpt\Tools\SMS_Retre`.
- Receive new SMS only; do not read historical SMS.
- Do not send SMS from the standby SIM.
- Use QQ Mail SMTP SSL by default: `smtp.qq.com:465`.
- Store SMS forwarding jobs locally first, then send asynchronously.
- Retry failed mail sends for about five minutes: immediate attempt plus one attempt per minute, up to six attempts total.
- After retry window expires, stop automatic retry and keep a failed record visible in the app.
- Support manual retry of failed records from the app.
- Make the package name stable for Clash Meta for Android per-app bypass: `com.smsretre.app`.
- New APKs must bump `versionCode`/`versionName` so they can be installed over old APKs while preserving existing SharedPreferences and SQLite data.
- Every successful build should also place a copy of the installable APK at project root: `D:\chatgpt\Tools\SMS_Retre\SMS_Retre.apk`.
- SMS emails should identify which configured SIM/phone number received the message.
- Battery alert should send one email when the phone is detected at 5% battery or lower, then reset after charging above 5%.

## Detail files

- `implementation-notes.md`: implementation decisions, platform notes, and error lessons.
