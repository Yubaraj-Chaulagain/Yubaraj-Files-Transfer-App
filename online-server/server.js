const express = require('express');
const multer = require('multer');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 8080;
const STORE = process.env.FILE_STORE || path.join(__dirname, 'temporary-files');
const MAX_MB = Number(process.env.MAX_FILE_MB || 512);
const TTL_MS = Number(process.env.FILE_TTL_MINUTES || 60) * 60 * 1000;
fs.mkdirSync(STORE, { recursive: true });

app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
// Allow the Chromebook/browser client and hosted web page to call the relay API.
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});
app.use(express.json({ limit: '20kb' }));
app.use(rateLimit({ windowMs: 60 * 1000, limit: 60, standardHeaders: true, legacyHeaders: false }));

const upload = multer({
  dest: STORE,
  limits: { fileSize: MAX_MB * 1024 * 1024, files: 1 }
});
const records = new Map();

function cleanExpired() {
  const now = Date.now();
  for (const [code, rec] of records) {
    if (rec.expiresAt <= now) {
      try { fs.unlinkSync(rec.path); } catch (_) {}
      records.delete(code);
    }
  }
}
setInterval(cleanExpired, 60 * 1000).unref();

app.get('/health', (_req, res) => res.json({ ok: true, service: 'Yubaraj Share temporary relay', maxFileMB: MAX_MB, ttlMinutes: TTL_MS / 60000 }));

app.post('/api/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file uploaded' });
  cleanExpired();
  let code;
  do { code = crypto.randomInt(100000, 1000000).toString(); } while (records.has(code));
  const originalName = path.basename(req.file.originalname || 'shared-file').replace(/[\r\n]/g, '_').slice(0, 180);
  const expiresAt = Date.now() + TTL_MS;
  records.set(code, { path: req.file.path, name: originalName, size: req.file.size, expiresAt });
  res.json({ code, name: originalName, size: req.file.size, expiresAt, ttlMinutes: TTL_MS / 60000 });
});

app.get('/api/file/:code', (req, res) => {
  cleanExpired();
  const code = String(req.params.code || '');
  const rec = records.get(code);
  if (!/^\d{6}$/.test(code) || !rec) return res.status(404).json({ error: 'Code not found or expired' });
  res.setHeader('Content-Type', 'application/octet-stream');
  res.setHeader('Content-Length', rec.size);
  res.setHeader('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(rec.name)}`);
  const stream = fs.createReadStream(rec.path);
  stream.on('error', () => { if (!res.headersSent) res.status(500).end('Read failed'); });
  stream.pipe(res);
  // One-time code: remove after a successful download stream finishes.
  stream.on('close', () => {
    if (records.get(code) === rec) {
      try { fs.unlinkSync(rec.path); } catch (_) {}
      records.delete(code);
    }
  });
});

app.use((err, _req, res, _next) => {
  if (err instanceof multer.MulterError && err.code === 'LIMIT_FILE_SIZE') return res.status(413).json({ error: `File exceeds ${MAX_MB} MB limit` });
  console.error(err);
  res.status(400).json({ error: 'Request failed' });
});

app.listen(PORT, '0.0.0.0', () => console.log(`Yubaraj Share relay listening on ${PORT}; max ${MAX_MB} MB; expiry ${TTL_MS/60000} min`));
