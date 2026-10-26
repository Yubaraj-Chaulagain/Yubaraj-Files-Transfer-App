# Yubaraj Share — Android project

## Included
- Android app with the supplied Yubaraj Share icon.
- Local Wi-Fi/hotspot file transfer.
- Online code-transfer UI and native upload/download integration.
- `online-server/`: Node.js temporary relay backend.
- GitHub Actions debug APK build workflow.

## Offline transfer
1. Install APK on both phones.
2. Connect both phones to the same Wi-Fi or connect sender to receiver phone's hotspot.
3. On receiving phone, tap **Start Receiver** and note its IP.
4. On sending phone, enter that IP and tap **Select files and send**.
5. Received files are saved in `Downloads/Yubaraj Share`.

## Online transfer setup
Online transfer requires a public Node.js host with HTTPS; hosting is not included in this ZIP.
1. Deploy the `online-server/` folder to a Node.js hosting provider.
2. Use build command `npm install`, start command `npm start`, Node.js 20+.
3. Optionally set `MAX_FILE_MB` (default 512), `FILE_TTL_MINUTES` (default 60), and `FILE_STORE`.
4. Check `https://YOUR-HOST/health`.
5. In the app's Online transfer section, enter `https://YOUR-HOST`.
6. Sender taps **Upload file & get code** and selects one file.
7. Share the six-digit code with recipient. Recipient enters the URL and code, then taps **Receive by code**.

The relay stores files temporarily and tries to delete them after one download or expiry. In-memory code records are lost if the server restarts. Use HTTPS and trusted hosting. Do not use this starter for sensitive files until security hardening is completed.

## Build APK on GitHub
1. Upload project contents to the root of a GitHub repository.
2. Open Actions → **Build Yubaraj Share APK** → Run workflow.
3. Download `Yubaraj-Share-debug-apk` and extract `app-debug.apk`.

## Limitations
- Code-based online transfer requires a separately deployed backend.
- Online transfer currently handles one file per code.
- QR pairing, mobile-number lookup, OTP login, transfer resume, end-to-end encryption and production-grade abuse prevention are not included.
- Local LAN transfer uses plain HTTP; only use on a trusted Wi-Fi/hotspot.
- The project has not been tested on physical devices in this environment.
