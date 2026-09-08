const assert = require('node:assert/strict');
const fs = require('node:fs');
const homepage = fs.readFileSync('index.html', 'utf8');
const downloadPage = fs.readFileSync('download/index.html', 'utf8');
assert.match(homepage, /href="\/download\/"/);
assert.match(downloadPage, /releases\/latest\/download\/LinkClear\.apk/);
assert.doesNotMatch(homepage, /release-assets\.githubusercontent\.com/);
console.log('Permanent APK download route is configured.');
async function checkShareTarget() {
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
  assert.equal(target.hash, '');
  console.log('Retired web share target safely discards shared content.');
}
checkShareTarget().catch(error => { console.error(error); process.exitCode = 1; });
