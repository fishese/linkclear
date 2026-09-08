"use strict";
// Migration for previously installed web cleaners: remove cached UI and safely discard old shares.
self.addEventListener("install",e=>e.waitUntil(self.skipWaiting()));
self.addEventListener("activate",e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith("linkclear-web-")).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener("fetch",e=>{
  const url=new URL(e.request.url);
  if(url.origin===self.location.origin && url.pathname==="/share-target" && e.request.method==="POST")
    e.respondWith(Promise.resolve(Response.redirect(self.location.origin+"/",303)));
});
