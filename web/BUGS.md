# Browser port bug ledger

Updated 2026-11-12. This ledger distinguishes upstream game issues, reusable
TeaVM/backend issues, and browser integration gaps. A passing build does not
close a runtime bug. Entries marked fixed refer to the validated local revision;
deployment status is tracked separately in PORTING.md.

| ID | Owner | Reproduction / impact | Status and next action |
| --- | --- | --- | --- |
| WEB-001 | gdx-teavm storage | Write a named save, reload, select it: date showed 1970 because WebFileHandle.lastModified returned zero. | Fixed in gdx-teavm 97c2c3e. Timestamp write/reload contract and current full-game build pass; overwrite and turn-6 reload also passed locally. Date sorting has not been stress-tested. |
| WEB-002 | gdx-teavm lifecycle | pause/resume/dispose from visibilitychange/pagehide run directly from DOM, unlike frames/input. Suspending callbacks lack TeaVM Java context and can overlap a suspended frame. | Fixed in gdx-teavm 97c2c3e. Suspendable visibility/pagehide browser contract passes with render-thread identity and no post-dispose frames. Actual unload can terminate queued work; bfcache restore remains unvalidated. |
| WEB-003 | web multiplayer integration | Browser capability gate disables online games; Fetch transport alone does not implement browser WebSockets. | Deferred by request to codex/webrtc-peer. Default server lacks browser CORS; WebRTC prototype is isolated and unvalidated. Main build remains single-player. |
| WEB-004 | WebGL atlas loading | Atlas metadata can request MipMapLinearLinear for magnification, which WebGL does not accept. | WebGL overrides loaded atlas textures to Linear magnification while preserving minification. Shared atlases and desktop packer settings are unchanged. The override runs after TextureAtlas loading, so it does not prevent a warning from the initial metadata value. |
| WEB-005 | web packaging | Browser build requires developer-specific absolute dependency checkouts. | Fixed: checksum-pinned public YosefLm source archives bootstrap the forks. Public-input `:web:preparePages` completed on 2026-11-08 in 5m 41s. |
| WEB-006 | deployment | Earlier implementation was local only; no reproducible current Pages pipeline. | Fixed for the current deployment: Yosef CI and Pages run 42190407113 succeeded; live `SOURCE_COMMIT` reports 873ae6a2a. Published source-map URLs return 404. |
| WEB-007 | web reflection policy | Load saved game: Vector2 lacks reflective constructor/fields; bounds written as empty objects. Fallback version parser also lacks constructor metadata. | Fixed f05dc7d08. JVM/generated-JS full-save roundtrip and fresh Babylon save/reload pass; nonzero bounds confirmed in IndexedDB export. |
| WEB-008 | web reflection policy | Select save: preview says Could not load game even though actual load works. GameInfoPreview/CivilizationInfoPreview lack metadata. | Fixed ac18ff19b. Old generated JS fails, patched JVM/JS passes; final browser preview and load verified. |
| WEB-009 | gdx-teavm input | Click new game: callbacks can suspend outside Java thread context; queued input state can race frame reset. | Fixed in gdx-teavm 393993b8. Immutable input snapshots and state/callback dispatch share the render executor; browser regression and current game startup pass. |
| WEB-010 | gdx-teavm rendering | First launch black canvas during font initialization/runBlocking. RAF callback lacks native Java context. | Fixed in gdx-teavm 393993b8; persistent render executor, FIFO/suspension/identity regression and current startup pass. |
| WEB-011 | TeaVM DecimalFormat | Pattern #,##0.### gives null omitted suffix during crash-screen formatting. | Fixed cd3eddd9b; omitted affixes empty, explicit-null setter semantics preserved; JVM/JS regression passes. |
| WEB-012 | TeaVM regex | Language-picker labels use optional capture groups in replacement; unmatched group dereferenced. | Fixed fee13672c, initial and cached replacements; old-fails/new-passes regression. |
| WEB-013 | web reflection policy | New-game texture access and resource/deprecation enumeration lack reflective members. | Fixed 2fe4533a9 and b8cee2bec; focused field/annotation fixtures and browser new game pass. |
| WEB-014 | TeaVM optimizer | Full compilation fails on unreachable predecessor dominator and exception-entry loop motion. | Fixed f2de7ba8c/5757a02e8; compiler regressions and 217 core tests pass. |
| WEB-016 | WebGL options / mod fonts | Open Options > Advanced: mod-font discovery called `WebFileHandle.file()`, which the browser backend explicitly does not support, crashing the tab. `WebFont` also ignored selected mod families and always used the browser sans stack. | Fixed: WebGL discovery uses virtual `FileHandle` traversal; selected font bytes load through the browser Font Loading API, with invalid files falling back to the sans stack. `ModFontsTest`, `WebFontTest`, focused Chrome contract, generated-game integration, and full pinned `:web:preparePages` build passed. |
| WEB-017 | Web Fetch/Ktor response metadata | Browser API response: Fetch decodes compressed body bytes but exposes compressed wire `Content-Length`; Ktor 3.5.2 compares that value to decoded bytes and throws. CORS may hide `Content-Encoding` while exposing `Content-Length`. | Fixed in the Fetch engine: normalize ambiguous length/encoding metadata while preserving HEAD and no-body response headers. Browser contract and generated Ktor fixture passed for plain, gzip, zstd, CORS-decoded, HEAD, 204, and stream-failure cases. Full pinned `:web:preparePages` and `node --check` passed. |
| WEB-018 | web display capability provider | Open Options > Display: WebLauncher installed the default `PlatformDisplay`, whose empty screen-mode map caused `DisplayTab` to call `values.first()` on an empty collection. | Fixed: WebLauncher supplies the responsive viewport mode and normalizes stale saved ids; browser fullscreen remains a separate user-gesture path. Generated-game E2E and manual review of before/after-refresh screenshots confirmed `Windowed` and 1280×720/1024×768 viewport updates with no page errors. Advanced is not covered because its tab is clipped beyond the header close button. |
| WEB-019 | TeaVM Kotlin reflection | Open Options > Multiplayer: sound-provider enumeration calls `UncivSound.Companion::class.declaredMemberProperties`, which requires Kotlin's full reflection factory. TeaVM deliberately excludes that factory and returns a lightweight `ClassReference`; generated code then casts it to `KClassImpl`, causing a `ClassCastException`. | Fixed in core by enumerating a typed `UncivSound.builtInSounds` registry instead of using full Kotlin reflection. The JVM parity test, generated-game Multiplayer sound-menu validation, and full pinned build pass. |
| WEB-020 | gdx-teavm music lifecycle | Start background music while Howler is waiting for browser audio unlock: `Howler.playing(id)` is initially false, so Unciv's music controller can dispose the queued track before a user gesture starts it. | Fixed in gdx-teavm `d2df1013`: `HowlMusic` reports requested playback as pending until Howler confirms actual playback, retains only the recognized autoplay-blocked request for unlock/retry, and clears it on pause, stop, dispose, completion, load failure, or terminal play error. The production adapter contract runs through TeaVM in Chromium and checks real audio advancement after a trusted click, cancellation-before-unlock, and terminal failures. In the generated game, the default Dropbox music download returned HTTP 200 and played; after reload, the downloaded file persisted and playback resumed. No page errors. Full pinned `:web:preparePages` and `node --check` pass. |
| WEB-021 | TeaVM stream decoding | Some multiplayer save downloads threw `IllegalStateException` when `InputStreamReader` flushed the UTF-8 decoder before signaling end of input. | Fixed in TeaVM `01e53e07c`. The captured gzip save and a short stream that reports availability until the EOF read both pass in generated JavaScript. |

## Additional live observation

WEB-015 (unconfirmed, owner not yet isolated): on the deployed runtime 873ae6a2a,
a fresh Russia Quickstart at turn 0 displayed happiness 12 before saving and 0
after page reload and loading the named save. The map and units loaded, with no
page errors. Compare the same no-city save on desktop, inspect transient-stat
recalculation, and test after founding a city before classifying this as a port
regression or upstream game issue. This observation is not a demonstrated fix.
## Required validation, not yet established support

- Multiplayer: server compatibility, CORS, HTTPS/WSS, authentication, two-player turn exchange, reconnection and persistence.
- Longer games: combat, later AI turns, and late-game serialization. Local testing has reached turn 6, completed research/production, and verified save overwrite/reload at turn 6.
- Browser lifecycle/input: visibility, resize, focus, touch, suspended callbacks, and bounded disposal.
- Arbitrary mod behavior beyond font discovery/rendering, file import/export/sharing, audio, WebAssembly, mobile browsers and performance remain separate work until tested. WEB-016 validates mod-font discovery and loading in the browser fixture.
- Continue recording exact Unciv and dependency commits in CI artifacts; the current Pages deployment was verified against runtime commit 873ae6a2a.

## Reporting discipline

Add an entry when a concrete failure is found. Include owner, minimal reproduction,
observed behavior, fix commit, regression evidence, and remaining limitations.
Do not label a TeaVM/backend integration issue as an upstream Unciv game-logic bug.
No gameplay-rule defect has yet been demonstrated; WEB-004 is handled at the WebGL atlas-loading boundary.

## Source-path publication audit

The web packaging audit found workstation paths in generated source maps;
`:web:preparePages` omits maps/source trees and rejects workstation paths in the
public artifact. The current Pages deployment's map URLs return 404. Dependency
fork source archives are checksum-pinned; historical Git commits have not been
rewritten.
