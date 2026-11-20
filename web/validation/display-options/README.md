# Browser display options regression

This Playwright runner opens the built web game in an isolated Chrome profile, opens Options, visits Display and the other visible option pages, resizes the viewport, refreshes, and reopens Display. It verifies the canvas tracks both viewport sizes and fails on uncaught page errors. Screenshots are written under `web/build/validation/display-options/` by default.

Run it with the Pages bundle served locally:

```powershell
$env:UNCIV_PREVIEW_URL = 'http://127.0.0.1:18081/'
node web/validation/display-options/run.cjs
```

Set `PLAYWRIGHT_MODULE` or install Playwright in Node's module path. On Windows the runner uses the standard Chrome install when present. `UNCIV_DISPLAY_ARTIFACTS` and `UNCIV_DISPLAY_PROFILE` can override the output and browser profile paths.

The Options header is horizontally scrollable. This fixture covers the Display, Gameplay, Automation, and Language pages visible at its 1280 by 720 viewport. The runner does not OCR the Display labels. Manually inspect `04-display.png` and `07-display-after-refresh.png` and confirm the canvas shows `Windowed` in both screenshots; no page errors or canvas-size checks alone are sufficient to assert the Options page content. Advanced is not covered by this runner because its tab is beyond the fixed close button in the narrow header.
