// Offline support. The production build replaces BUILD and PRECACHE (hashWasmJsBundle in
// composeApp/build.gradle.kts), so every deploy ships a new worker that caches its whole shell on
// install: the app opens offline after its first visit, and an old shell is never half replaced.
// The event feed is never cached here; the app keeps its own copy in IndexedDB.
const BUILD = "dev";
const PRECACHE = /*PRECACHE*/[]/*END*/;
const SHELL = "timetabler-shell-" + BUILD;
const POSTERS = "timetabler-posters-v1";

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(SHELL).then((cache) => cache.addAll(PRECACHE)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== SHELL && k !== POSTERS).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function cacheable(res) {
  return res && res.status === 200 && res.type !== "opaque";
}

function cacheFirst(cacheName, req, key) {
  return caches.open(cacheName).then((cache) =>
    cache.match(key || req).then(
      (cached) =>
        cached ||
        fetch(req).then((res) => {
          if (cacheable(res)) cache.put(key || req, res.clone()).catch(() => {});
          return res;
        })
    )
  );
}

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/v1/events") || url.pathname === "/health") return;
  if (url.pathname.startsWith("/v1/posters/")) {
    event.respondWith(cacheFirst(POSTERS, req));
    return;
  }
  if (PRECACHE.length === 0) return; // development server: no shell to serve
  // Every page of the app (the list, /event/<id>) is the same single-page shell, offline too.
  event.respondWith(cacheFirst(SHELL, req, req.mode === "navigate" ? "/" : undefined));
});
