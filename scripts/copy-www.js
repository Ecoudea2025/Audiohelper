// Copies the static web app into www/ for `npx cap sync`.
// The web source of truth is the repo root (index.html + audio/ + vendor/ ...).
// www/ itself is a build artifact (gitignored) — regenerate with `npm run app:copy`.
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const WWW = path.join(ROOT, 'www');

const FILES = [
  'index.html',
  'sw.js',
  'manifest.webmanifest',
  'logo-home.png',
  'logo-intro.png',
  'logo-medium.png',
  'icon-192.png',
  'icon-512.png',
  'apple-touch-icon.png'
];
const DIRS = ['audio', 'vendor'];

fs.rmSync(WWW, { recursive: true, force: true });
fs.mkdirSync(WWW, { recursive: true });

for (const f of FILES) {
  const src = path.join(ROOT, f);
  if (!fs.existsSync(src)) {
    console.warn('skip (missing):', f);
    continue;
  }
  fs.copyFileSync(src, path.join(WWW, f));
}
for (const d of DIRS) {
  fs.cpSync(path.join(ROOT, d), path.join(WWW, d), { recursive: true });
}

const mp3s = [];
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.mp3')) mp3s.push(p);
  }
})(path.join(WWW, 'audio'));

let bytes = 0;
for (const m of mp3s) bytes += fs.statSync(m).size;
console.log(`www/ ready: ${mp3s.length} mp3s, ${(bytes / 1048576).toFixed(1)} MB audio`);
