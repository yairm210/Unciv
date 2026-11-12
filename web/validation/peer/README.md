# WebRTC transport regression

Run `node web/validation/peer/run.cjs` with Playwright and Chromium installed.
Set `PLAYWRIGHT_MODULE` and `CHROME_PATH` to use explicit local installations.

The fixture uses two isolated Chromium contexts and a real WebRTC data channel
with local ICE candidates. It covers signaling replacement, offer and answer
exchange through the setup dialog, chunked UTF-8 save transfer, preview removal,
oversize and invalid URL rejection, cancellation, disconnect, and IndexedDB
persistence across browser reload and reconnect.
