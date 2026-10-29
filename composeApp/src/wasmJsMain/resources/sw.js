// Offline support: stale-while-revalidate for the app shell, cache-first for posters.
// The event feed is never cached here; the app keeps its own copy in IndexedDB.
const SHELL = "timetabler-shell-v1";
const POSTERS = "timetabler-posters-v1";

self.addEventListener("install", () => self.skipWaiting());

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

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/v1/events") || url.pathname === "/health") return;

  if (url.pathname.startsWith("/v1/posters/")) {
    event.respondWith(
      caches.open(POSTERS).then((cache) =>
        cache.match(req).then(
          (cached) =>
            cached ||
            fetch(req).then((res) => {
              if (cacheable(res)) cache.put(req, res.clone()).catch(() => {});
              return res;
            })
        )
      )
    );
    return;
  }

  event.respondWith(
    caches.open(SHELL).then((cache) =>
      cache.match(req).then((cached) => {
        const fresh = fetch(req)
          .then((res) => {
            if (cacheable(res)) cache.put(req, res.clone()).catch(() => {});
            return res;
          })
          .catch(() => cached);
        return cached || fresh;
      })
    )
  );
});
