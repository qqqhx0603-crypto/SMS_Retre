# SMS-Retre memory index

## Project goal

Build a self-use Android app that runs on the standby phone and forwards newly received SMS messages to the user's main phone through email.

## First-version requirements

- Project root: `D:\chatgpt\Tools\SMS-Retre`.
- Receive new SMS only; do not read historical SMS.
- Do not send SMS from the standby SIM.
- Use QQ Mail SMTP SSL by default: `smtp.qq.com:465`.
- Store SMS forwarding jobs locally first, then send asynchronously.
- Retry failed mail sends for about five minutes: immediate attempt plus one attempt per minute, up to six attempts total.
- After retry window expires, stop automatic retry and keep a failed record visible in the app.
- Support manual retry of failed records from the app.
- Make the package name stable for Clash Meta for Android per-app bypass: `com.smsretre.app`.

## Detail files

- `implementation-notes.md`: implementation decisions, platform notes, and error lessons.
