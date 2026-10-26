# Yubaraj Share — FINAL Combined Project Package

This ZIP combines the latest project files for:
- Android app and supplied Yubaraj Share icon
- Local Wi-Fi / hotspot file sending and receiving code
- Online transfer by six-digit code (requires deploying online-server)
- Node.js temporary relay backend
- GitHub Actions workflow for building a debug APK

## Important
This is a combined source project package, not a prebuilt APK. The project has not been verified on real Android devices in this environment. Build it with GitHub Actions and test with non-sensitive files first.

## Build the APK
1. Extract this ZIP.
2. Upload the contents inside the extracted `Yubaraj-Share-File-Transfer` project folder to the root of a GitHub repository (so `.github`, `app`, `online-server`, `build.gradle`, and `settings.gradle` are at repository root).
3. Open GitHub Actions.
4. Select `Build Yubaraj Share APK` and press `Run workflow`.
5. Download the `Yubaraj-Share-debug-apk` artifact from the completed workflow and extract `app-debug.apk`.

## Offline transfer
1. Install the APK on both phones.
2. Connect both phones to the same Wi-Fi or connect the sender to the receiver phone's hotspot.
3. On the receiving phone, tap `Start Receiver`; note its IP address.
4. On the sending phone, enter that IP and tap `Select files and send`.
5. Received files are saved to `Downloads/Yubaraj Share`.
Use only trusted local networks because the current LAN transfer uses plain HTTP.

## Online transfer
The online feature requires deploying the `online-server` folder to a public Node.js host with HTTPS:
- Node.js 20+
- Build/install command: `npm install`
- Start command: `npm start`
- Health check: `https://YOUR-HOST/health`
Then enter that HTTPS URL in the app. Sender uploads a single file and receives a six-digit code; recipient enters the same URL and code to download it. The starter backend uses in-memory code records, so a restart invalidates active codes. Do not use for sensitive files without additional security hardening.

## Not included yet
Mobile-number login/OTP, QR pairing, end-to-end encryption, resumable transfers, and production-grade abuse prevention are not implemented.
