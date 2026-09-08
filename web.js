"use strict";
// Retire old share fragments without retaining their content in browser history.
if(location.hash) history.replaceState(null,"",location.pathname);
if("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").then(r=>r.update()).catch(()=>{});
