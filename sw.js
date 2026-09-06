'use strict';
const CACHE = 'linkclear-web-v1';
const ASSETS = ['/', '/index.html', '/web.css', '/cleaner.js', '/web.js', '/icon.svg', '/icon-192.png', '/icon-512.png', '/manifest.webmanifest'];
self.addEventListener('install', event => { event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)).then(() => self.skipWaiting())); });
self.addEventListener('activate', event => { event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith('linkclear-web-') && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  if (event.request.method === 'POST' && url.pathname === '/share-target') {
    event.respondWith((async () => {
      const data = await event.request.formData();
      const text = String(data.get('text') || '');
      const link = String(data.get('url') || '');
      const title = String(data.get('title') || '');
      const content = [title, text, link && !text.includes(link) ? link : ''].filter(Boolean).join('\n');
      return Response.redirect(new URL('/#share=' + encodeURIComponent(content), self.location.origin).href, 303);
    })());
    return;
  }
  if (event.request.method === 'GET' && !url.search && ASSETS.includes(url.pathname)) {
    event.respondWith(fetch(event.request).catch(() => caches.match(event.request)));
  }
});
