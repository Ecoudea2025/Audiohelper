# CD Market — Listening Course 🎧

Interactive listening practice for the **CD Market** English course. Two levels in a single page — no server, no build step, works offline.

👉 **Just open `index.html` in any browser** (or serve the folder statically).

## Levels

| Level | Theme | Content |
|-------|-------|---------|
| **Level I — Listening Lab** 🎧 | Calm light/dark design | 12 units (Introductions → Jobs) · **84 audios** with transcripts + vocabulary |
| **Medium — Hostile Protocol** ⚠ | Neon dark "game" HUD | 12 sectors (Careers → Products) · **71 encounters** with transcripts + vocabulary |

## Method: listen first, read later

1. Press **play** and focus on the audio — the transcript stays hidden on purpose.
2. When ready, hit **Reveal transcript** (or **Decrypt** in Medium) to check yourself.
3. Study the **vocabulary cards** — click one to highlight it in the transcript.
4. Finishing an audio marks it ✅ and progress saves automatically (localStorage).

## Project structure

```
Audiohelper/
├── index.html            ← the whole app (single file, CSS+JS inline)
├── audio/
│   ├── intro-cd1/        ← Level I units 1–6 (MP3)
│   ├── intro-cd2/        ← Level I units 7–12 (MP3)
│   ├── medium-cd1/       ← Medium units 1–6 (MP3)
│   └── medium-cd2/       ← Medium units 7–12 (MP3)
├── .gitignore
└── README.md
```

> Paths are **relative** (`audio/…`), so the page works from any folder, any static host, or GitHub Pages. Keep `index.html` next to the `audio/` folder.

## Offline (PWA)

Open the Pages URL **once while online**: the service worker (`sw.js`) precaches the app shell (HTML, `vendor/` libs, logos, icons) and shows **OFFLINE LISTO** in the badge at the bottom-left.

- Each unit header has a **⬇ Offline** button that downloads just that unit's MP3s (~5 MB) into the cache. Anything you play is also cached automatically.
- After that, the page, transcripts, vocabulary and saved audios work **with no connection** (seek included — the worker answers Range requests with `206`).
- Animations/libs are bundled in `vendor/` (gsap, lenis, anime), so they work offline too. Google Fonts degrade gracefully to system fonts.
- iOS notes: use **Share → Add to Home Screen** for the most persistent install, and ask the browser for persistent storage when prompted. The system may evict cached audios if storage runs out or after long disuse — re-tap ⬇ Offline while online to restore.
- New deploys show a *“Nueva versión lista → Recargar”* toast (no forced reloads, to avoid iOS reload loops).
