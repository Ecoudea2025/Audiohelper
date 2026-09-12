/* CD Market — offline service worker (iOS-safe).
 * Strategy:
 *  - SHELL cache: precached app shell only (<1MB). Never evicted.
 *  - AUDIO cache: MP3s cached lazily on first fetch/play. Evicted oldest-first
 *    on quota errors. Supports HTTP Range (206) so <audio> seek works offline.
 *  - Updates: no auto skipWaiting (avoids iOS reload loops). Page prompts user.
 */
const SHELL = 'cdm-shell-v1';
const AUDIO = 'cdm-audio-v1';

const SHELL_FILES = [
  './',
  './index.html',
  './manifest.webmanifest',
  './logo-home.png',
  './logo-intro.png',
  './logo-medium.png',
  './icon-192.png',
  './icon-512.png',
  './vendor/gsap.min.js',
  './vendor/lenis.min.js',
  './vendor/anime.min.js'
];

self.addEventListener('install', (event) => {
  // iOS: cache.addAll rejects everything if ONE file fails -> cache one by one.
  event.waitUntil(
    (async () => {
      const cache = await caches.open(SHELL);
      await Promise.all(
        SHELL_FILES.map(async (url) => {
          try {
            const res = await fetch(new Request(url, { cache: 'reload' }));
            if (res && res.ok) await cache.put(url, res);
          } catch (e) {
            // tolerate single-file failures (e.g. icon missing on old deploy)
          }
        })
      );
    })()
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys.map((k) => {
          if (k !== SHELL && k !== AUDIO) return caches.delete(k);
          return undefined;
        })
      );
      await self.clients.claim();
    })()
  );
});

self.addEventListener('message', (event) => {
  if (event.data === 'SKIP_WAITING') self.skipWaiting();
});

function isAudioRequest(url) {
  return url.pathname.indexOf('/audio/') !== -1 || /\.mp3(\?|$)/i.test(url.pathname);
}

function isShellFile(url) {
  const p = url.pathname;
  return (
    p.endsWith('/vendor/gsap.min.js') ||
    p.endsWith('/vendor/lenis.min.js') ||
    p.endsWith('/vendor/anime.min.js') ||
    p.endsWith('/logo-home.png') ||
    p.endsWith('/logo-intro.png') ||
    p.endsWith('/logo-medium.png') ||
    p.endsWith('/icon-192.png') ||
    p.endsWith('/icon-512.png') ||
    p.endsWith('/manifest.webmanifest')
  );
}

// Refresh FIFO recency: re-insert entry so eviction order stays "oldest first".
async function touchAudio(url) {
  try {
    const cache = await caches.open(AUDIO);
    const hit = await cache.match(url);
    if (hit) {
      await cache.delete(url);
      await cache.put(url, hit);
    }
  } catch (e) {}
}

async function evictOldestAudio(count) {
  try {
    const cache = await caches.open(AUDIO);
    const keys = await cache.keys();
    for (let i = 0; i < Math.min(count, keys.length); i++) {
      await cache.delete(keys[i]);
    }
  } catch (e) {}
}

async function putAudio(url, response) {
  try {
    const cache = await caches.open(AUDIO);
    await cache.put(url, response);
  } catch (e) {
    // QuotaExceededError on iOS: drop oldest ~8 entries and retry once.
    try {
      await evictOldestAudio(8);
      const cache = await caches.open(AUDIO);
      await cache.put(url, response);
    } catch (e2) {}
  }
}

// Serve a Range request from a cached full response.
async function serveRange(cached, rangeHeader) {
  const buf = await cached.arrayBuffer();
  const total = buf.byteLength;
  const m = /bytes=(\d*)-(\d*)/.exec(rangeHeader || '');
  let start = m && m[1] !== '' ? parseInt(m[1], 10) : 0;
  let end = m && m[2] !== '' ? parseInt(m[2], 10) : total - 1;
  if (isNaN(start) || start < 0) start = 0;
  if (isNaN(end) || end >= total) end = total - 1;
  if (start > end) start = 0;
  const slice = buf.slice(start, end + 1);
  const headers = new Headers();
  const ctype = cached.headers.get('Content-Type') || 'audio/mpeg';
  headers.set('Content-Type', ctype);
  headers.set('Content-Range', 'bytes ' + start + '-' + end + '/' + total);
  headers.set('Content-Length', String(end - start + 1));
  headers.set('Accept-Ranges', 'bytes');
  return new Response(slice, { status: 206, statusText: 'Partial Content', headers });
}

async function handleAudio(request) {
  const url = new URL(request.url);
  const range = request.headers.get('Range');
  const cache = await caches.open(AUDIO);
  const cached = await cache.match(url.pathname + url.search);

  if (cached) {
    touchAudio(url.pathname + url.search); // async recency refresh
    if (range) return serveRange(cached, range);
    return cached;
  }

  // Not cached: go to network. Passthrough keeps Range/seek working online.
  try {
    const live = await fetch(request);
    // Only cache complete 200 responses (never partial 206).
    if (live && live.ok && live.status === 200) {
      putAudio(url.pathname + url.search, live.clone());
    }
    return live;
  } catch (e) {
    // Offline and not cached: explicit 404 so the player shows "not saved yet".
    return new Response('Audio not cached yet — save the unit while online.', {
      status: 404,
      headers: { 'Content-Type': 'text/plain' }
    });
  }
}

async function handleShellAsset(request) {
  const cached = await caches.match(request, { cacheName: SHELL });
  if (cached) {
    // Stale-while-revalidate in background (no iOS background sync needed).
    fetch(request)
      .then((live) => {
        if (live && live.ok) caches.open(SHELL).then((c) => c.put(request, live));
      })
      .catch(() => {});
    return cached;
  }
  try {
    const live = await fetch(request);
    if (live && live.ok) {
      const cache = await caches.open(SHELL);
      cache.put(request, live.clone());
    }
    return live;
  } catch (e) {
    return caches.match('./index.html');
  }
}

async function handleNavigation(request) {
  try {
    const live = await fetch(request);
    if (live && live.ok) {
      const cache = await caches.open(SHELL);
      // Keep the cached shell fresh (best-effort).
      cache.put('./index.html', live.clone()).catch(() => {});
    }
    return live;
  } catch (e) {
    const cached =
      (await caches.match('./index.html')) || (await caches.match('./'));
    if (cached) return cached;
    return new Response('Offline — open the app once while online first.', {
      status: 503,
      headers: { 'Content-Type': 'text/plain' }
    });
  }
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  // Only handle same-origin. Fonts/CDN pass through (never cached -> no quota burn).
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    event.respondWith(handleNavigation(request));
    return;
  }
  if (isAudioRequest(url)) {
    event.respondWith(handleAudio(request));
    return;
  }
  if (isShellFile(url)) {
    event.respondWith(handleShellAsset(request));
    return;
  }
  // Same-origin non-shell (shouldn't happen): network-first, no caching.
  event.respondWith(fetch(request).catch(() => caches.match('./index.html')));
});
