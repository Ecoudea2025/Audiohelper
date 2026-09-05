# CD Market — Listening Course 🎧

Interactive listening practice for the **CD Market** English course. Two levels in a single page — no server, no build step, works offline.

👉 **Just open `index.html` in any browser** (or serve the folder statically).

## Levels

| Level | Theme | Content |
|-------|-------|---------|
| **Level I — Listening Lab** 🎧 | Calm light/dark design | 12 units (Introductions → Jobs) · **84 audios** with transcripts + vocabulary |
| **Medium — Hostile Protocol** ⚠ | Neon dark "game" HUD | 12 sectors (Careers → Products) · **68 encounters** with transcripts + vocabulary |

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

## Deploy as a website (optional)

This repo is GitHub Pages-ready: Settings → Pages → Deploy from branch → `main` → `/ (root)`. The site will be live at `https://<user>.github.io/Audiohelper/`.

## Tech

Single-file app: vanilla HTML + CSS + JS with GSAP, ScrollTrigger, Lenis, SplitType and anime.js via CDN (internet needed only for those libraries and fonts — audios always play offline).
