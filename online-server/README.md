# Yubaraj Share temporary online relay

Deploy this Node.js service to a public host that provides HTTPS. The Android app's Online transfer screen needs the public base URL.

Endpoints:
- `GET /health`
- `POST /api/upload` multipart field `file` → returns code/name/size/expiry
- `GET /api/file/:code` → downloads file and consumes the code

Deploy with Node.js 20+, build command `npm install`, start command `npm start`.
Environment variables: `PORT` (default 8080), `MAX_FILE_MB` (default 512), `FILE_TTL_MINUTES` (default 60), `FILE_STORE` (optional storage directory).

The service uses temporary files and a six-digit code. A restart clears in-memory code records. Use persistent disk if the host filesystem is ephemeral. Deploy behind HTTPS. Only share codes with the intended recipient. Mobile-number/OTP login is not included and requires an SMS provider.
