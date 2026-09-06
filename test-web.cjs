const assert = require('node:assert/strict');
const { clean } = require('./cleaner.js');
let count = 0;
function check(input, expected, kind = 'clean') { assert.deepEqual(clean(input), { kind, text: expected }); count++; }
check('https://twitter.com/example/status/123?s=20', 'https://x.com/example/status/123/');
check('https://x.com/example/status/123?s=19&t=SYNTHETIC', 'https://x.com/example/status/123/');
check('https://www.instagram.com/p/TEST/?stkn=SYNTHETIC#fragment', 'https://www.instagram.com/p/TEST/');
check('https://threads.net/@example/post/TEST?xmt=SYNTHETIC', 'https://www.threads.com/@example/post/TEST/');
check('Caption (https://instagram.com/reels/TEST/).', 'https://www.instagram.com/reel/TEST/');
for (const text of ['https://example.org/?keep=YES', 'https://x.com.evil.test/example/status/123?s=20', 'https://instagram.com/p/../p/TEST', 'https://instagram.com:443/p/TEST', 'https://user@instagram.com/p/TEST', 'Caption\nhttps://example.org/?x=1\n', 'https://x.com/example/status/123 https://example.org/', 'plain text']) check(text, text, 'original');
for (const text of ['https://threads.com/share/SYNTHETIC', 'https://instagram.com/share/p/SYNTHETIC']) check(text, text, 'unresolved');
console.log(`${count} browser parser checks passed (synthetic fixtures).`);

async function checkShareTarget() {
  const fs = require('node:fs');
  const vm = require('node:vm');
  const events = {};
  vm.runInNewContext(fs.readFileSync('sw.js', 'utf8'), {
    self: { location: { origin: 'https://example.test' }, addEventListener: (name, handler) => { events[name] = handler; } },
    URL, Response
  });
  const form = new FormData();
  form.set('text', 'Caption https://x.com/example/status/123?s=20');
  form.set('url', 'https://x.com/example/status/123?s=20');
  let response;
  events.fetch({ request: { method: 'POST', url: 'https://example.test/share-target', formData: async () => form }, respondWith: value => { response = value; } });
  const redirect = await response;
  const target = new URL(redirect.headers.get('location'));
  assert.equal(redirect.status, 303);
  assert.equal(target.search, '');
  assert.equal(new URLSearchParams(target.hash.slice(1)).get('share'), form.get('text'));
  console.log('Web share-target POST handling passed; content stays in the fragment.');
}
checkShareTarget().catch(error => { console.error(error); process.exitCode = 1; });
