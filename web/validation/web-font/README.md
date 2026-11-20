# Browser font contract

This fixture extracts the three production `@JSBody` implementations from
`WebFontRasterizer.kt` and runs them in headless Chrome. It verifies that a
binary `ByteArray` source registers, that browser canvas metrics and glyph
pixels change for a distinctive font, that removing and registering another
face switches the rendered family, and that malformed bytes fail without
registering a face.

The test font is supplied by the runner and is not copied into this repository.
Use a local TTF or OTF whose metrics differ from Arial for `WMWMWM`; on Windows,
`C:\Windows\Fonts\ALGER.TTF` works. It also checks that WebGL mod-font
discovery uses `FileHandle` directory APIs and never calls `.file()`.

From the repository root:

```powershell
$env:WEB_FONT_TEST_TTF = 'C:\Windows\Fonts\ALGER.TTF'
node web/validation/web-font/run.cjs
```

After building the generated web app and serving `web/build/pages/game` on
`127.0.0.1:18081`, exercise the real TeaVM `WebFont` and LocalDB `FileHandle`
path with an isolated Chrome profile:

```powershell
$env:WEB_FONT_TEST_TTF = 'C:\Windows\Fonts\ALGER.TTF'
node web/validation/web-font/run-game.cjs
```

This seeds a temporary IndexedDB database with the selected `FontFamilyData`,
its font bytes and the expected nested mod folders, then checks that the
generated app registers and measures that custom face during startup. It does
not write to the user's normal browser profile.

The runner uses the existing headless-browser Playwright installation and
Chrome path when available. Set `PLAYWRIGHT_MODULE` and `CHROME_PATH` to use
other installations.
