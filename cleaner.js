(function (root) {
  'use strict';
  const rules = [
    { hosts: ['instagram.com', 'www.instagram.com', 'm.instagram.com'], host: 'www.instagram.com', path: /^\/(p|reel|reels|tv)\/[A-Za-z0-9_-]+\/?$/, short: /^\/share\/(p|reel|r)\/[A-Za-z0-9_-]+\/?$/ },
    { hosts: ['threads.com', 'www.threads.com', 'threads.net', 'www.threads.net'], host: 'www.threads.com', path: /^\/@[A-Za-z0-9._]+\/post\/[A-Za-z0-9_-]+\/?$/, short: /^\/(share|t)\/[A-Za-z0-9_-]+\/?$/ },
    { hosts: ['x.com', 'www.x.com', 'mobile.x.com', 'twitter.com', 'www.twitter.com', 'mobile.twitter.com', 'm.twitter.com'], host: 'x.com', path: /^\/(?:[A-Za-z0-9_]{1,15}\/status|i\/web\/status|i\/status)\/[0-9]+(?:\/(photo|video)\/[1-4])?\/?$/ }
  ];
  function clean(text) {
    const original = { kind: 'original', text };
    if (typeof text !== 'string' || text.length > 32768) return original;
    const urls = text.match(/https?:\/\/[^\s<>"\u201c\u201d]+/gi);
    if (!urls || urls.length !== 1) return original;
    const raw = urls[0].replace(/[.,!;:)\]}'\u2019]+$/, '');
    // Inspect raw authority/path before URL normalization can hide userinfo, ports or dot segments.
    const match = raw.match(/^https?:\/\/([^/?#]+)([^?#]*)/i);
    if (!match || /[@:\\]/.test(match[1]) || /[\\\u0000-\u0020]/.test(raw)) return original;
    const rule = rules.find(r => r.hosts.includes(match[1].toLowerCase()));
    if (!rule) return original;
    const path = match[2];
    if (rule.short && rule.short.test(path)) return { kind: 'unresolved', text };
    if (!rule.path.test(path)) return original;
    const normalized = path.replace(/^\/reels\//, '/reel/');
    return { kind: 'clean', text: 'https://' + rule.host + normalized + (normalized.endsWith('/') ? '' : '/') };
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = { clean };
  else root.LinkClear = { clean };
})(typeof globalThis !== 'undefined' ? globalThis : this);
