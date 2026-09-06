'use strict';
const input = document.querySelector('#input');
const output = document.querySelector('#output');
const result = document.querySelector('#result');
const status = document.querySelector('#status');
const notice = document.querySelector('#notice');
const shareButton = document.querySelector('#share');
let current = null;
function render() {
  if (!input.value.trim()) return;
  current = LinkClear.clean(input.value);
  output.value = current.text;
  result.hidden = false;
  document.querySelector('#result-title').textContent = current.kind === 'clean' ? 'Your clean link' : 'Original content';
  status.textContent = current.kind === 'clean' ? 'Query parameters and fragment removed.' : current.kind === 'unresolved'
    ? 'This short link needs the Android app to resolve it. The original content below is unchanged.'
    : 'Unrecognized link. Original content is unchanged.';
  document.querySelector('#copy').textContent = current.kind === 'clean' ? 'Copy link' : 'Copy original';
  shareButton.textContent = current.kind === 'clean' ? 'Share link' : 'Share original';
  notice.textContent = '';
}
document.querySelector('#clean-form').addEventListener('submit', event => { event.preventDefault(); render(); });
input.addEventListener('input', () => { current = null; result.hidden = true; output.value = ''; notice.textContent = ''; });
document.querySelector('#clear').addEventListener('click', () => { input.value = ''; input.dispatchEvent(new Event('input')); input.focus(); });
document.querySelector('#copy').addEventListener('click', async () => {
  if (!current) return;
  try { await navigator.clipboard.writeText(current.text); notice.textContent = 'Copied.'; }
  catch { output.focus(); output.select(); notice.textContent = 'Select and copy the highlighted text using your browser.'; }
});
shareButton.addEventListener('click', async () => {
  if (!current) return;
  if (!navigator.share) { notice.textContent = 'Sharing is unavailable in this browser. Use Copy instead.'; return; }
  try { await navigator.share({ text: current.text }); notice.textContent = 'Share sheet closed.'; }
  catch (error) { if (error.name !== 'AbortError') notice.textContent = 'Could not open sharing. Use Copy instead.'; }
});
// Installed share targets receive a service-worker redirect fragment, never a query parameter.
const incoming = new URLSearchParams(location.hash.slice(1)).get('share');
if (incoming !== null) {
  history.replaceState(null, '', location.pathname);
  input.value = incoming;
  render();
}
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
