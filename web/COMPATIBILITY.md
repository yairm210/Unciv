# TeaVM and browser compatibility

This page separates validated current results from earlier compiler and runtime
checkpoints. The pinned public build, Pages deployment, and local gameplay/save
checks have each been verified.

## Current validated results

### Full build and gameplay

The latest public pinned `:web:preparePages` build completed in 6m 6s using
TeaVM `9ce0ede26b7235a10b9143d6383d5866a400352c`, gdx-teavm
`393993b8ea852f542d8025d4c7d34700a035421c`, and Unciv runtime commit
`873ae6a2a`. The generated `app.js` SHA-256 is
`7F8A912487CEFBE237B57A09F6D2D1231ADE9E8790E44A5AB4FEB5E2A2B4A4EB`.
Yosef CI and Pages run `42190407113` succeeded; the live site's `SOURCE_COMMIT`
reports `873ae6a2a`. Deployed source-map URLs return 404, as intended.

Local headless Chrome with SwiftShader verified the English picker, main menu,
Tiny Vanilla game creation, map display, unit movement, city founding, research
and production selection, turn completion, and save/load. Extended play reached
turn 6 with research and production completing. Overwriting the named save,
reloading the page, and loading it again preserved turn 6. This is a local
gameplay result; the Pages run establishes deployment, not the same extended
playthrough.

### Save and reload validation

Unciv `f05dc7d08` adds targeted metadata for serialized `Vector2` fields and the
`GameInfoSerializationVersion` fallback parser. JVM and generated-JavaScript
contracts passed against the captured 44 KB save and a nonzero-coordinate variant.

An earlier captured-bundle check created a fresh Tiny Vanilla game as Babylon,
founded a city, selected Animal Husbandry and Monument, completed turn 1, then
reloaded and loaded the named save through the UI. The later extended check
overwrote and reloaded a turn-6 save successfully.

Read-only IndexedDB export confirms game ID
`f700883a-ac70-41cf-b1b3-a45cf8e11e74`, 43,356 bytes, and explored-region bounds
`topLeft=(-5,3)`, `bottomRight=(-9,-5)`. Screenshot evidence is in the ignored
local diagnostics directory `web/build/validation/headless-browser`:
`gameplay-11-final-turn-one.png` and `gameplay-17-final-loaded.png`.
The browser reported no page errors, failed requests or HTTP error responses.

The save-selection preview initially showed an incorrect error label while actual
loading succeeded. Unciv `ac18ff19b` registers `GameInfoPreview` and
`CivilizationInfoPreview`. The generated-JS regression fails without those rules
and passes with them, including ID migration, human-player filtering and date
formatting. The final full-app build passes (4m 44s); browser preview now displays Babylon,
Prince, turn 1, and Civ V - Vanilla correctly, and the game loads again. Evidence:
`gameplay-02-preview-fixed.png`, `gameplay-03-verified-final-bundle.png`.

The timestamp persistence fix is included in the pinned backend and its browser
contract passed. Save preview date sorting has not been separately stress-tested.

## Reproducing the web build

Use JDK 21 and `./gradlew -Pweb -PwebCompilerMemory=8192 :web:preparePages`.
The main build downloads checksum-pinned public fork archives; see
[BUILDING.md](BUILDING.md) and `dependencies.lock.properties`. Explicit source
checkout overrides remain available for development, but are not required.
The public package excludes debug maps and source trees to avoid exposing build
machine paths. Normal builds without `-Pweb` do not bootstrap these dependencies.

## Current compatibility changes

- TeaVM 0.16 fork fixes cover concurrency used by coroutines, executor and
  scheduled-executor behavior, `LockSupport`, blocking queues/read-write locks,
  locale/collation, SHA-1, secure random, file and classloader APIs, and native
  JavaScript throwable stack capture. The source fork has focused JVM and
  generated-JavaScript contracts for these changes.
- TeaVM optimizer fixes cover dominator computation with unreachable
  predecessors and avoid loop-invariant code motion across external exception
  entries. TeaVM core tests and Checkstyle passed for those fixes.
- gdx-teavm fixes cover the libGDX `Pixmap(ByteBuffer)` constructor and web input
  dispatch. DOM defaults/focus handling stays synchronous; event state and
  callbacks run in FIFO order on the persistent render thread. The browser
  regression checks suspended frames, default prevention, input state and thread
  identity.
- Unciv's web reflection policy is narrow and driven by observed JSON/runtime
  requirements. It covers save-marker types, ruleset models, selected settings
  and skin types, scene2d defaults, file texture pixmap access, and enum
  annotation lookup. The latest targeted additions for Vector2 and the fallback
  version parser passed the fixture noted above.
- The browser Fetch engine handles HTTP requests without exposing CIO sockets to
  TeaVM. Focused tests cover UTF-8 streaming, status/headers, error bodies,
  network rejection, request and response cancellation, and request timeout.

These source changes are pinned public forks, not upstream releases. Keep
compiler/backend/classlib artifacts paired to the same fork revisions when
reproducing results. The normal opt-in path verifies pinned public archives;
local source overrides are documented in `BUILDING.md`.

## Additional validation evidence

- All 217 TeaVM core tests and applicable Checkstyle checks passed.
- SaveLocationTest passed 3 tests, including eight local/external save combinations.
- JVM loopback tests verified default CIO selection and raw WebSocket ping/pong.
- Generated JavaScript contracts exercised real settings, saves, rulesets and skins.
- Current relocated source artifacts, SHA-256:
  - core: `684AF8FF9A94EE88A7335EB02EA20F6C4A2B00563B806BF4CC30659467627BF0`
  - classlib: `CD4021DC1BA708EA47D0185D32B802795BBB90FBE18921E274252995446314AF`
  - JSO APIs: `7E0E919BD0F1D608A97858C05C2A32F4FD38756583153E3D1C2596559FFC9CEE`

## Historical compiler and runtime checkpoints

The entries below document resolved investigations and older baselines. They are
not current blockers unless called out in the current status section.

### Initial TeaVM 0.16 diagnostics

The published 0.16 baseline reported 48 unique missing symbols (32 classes and
16 methods). The initial TeaVM/gdx-teavm source substitution reduced this to 42
(29 classes, 13 methods) and exposed a scheduled-executor gap. Those counts are
compiler diagnostics from before the compatibility fixes summarized above. Logs:

- `web/build/diagnostics/teavm-js-0.16-published-baseline.log`
- `web/build/diagnostics/teavm-js-0.16-fork-combined.log`

Earlier TeaVM 0.15 work reported 49 missing symbols, later 45 after an overlay.
That overlay and its task-level JAR replacement experiments are obsolete; current
web builds use the TeaVM 0.16 source composite.

### Resolved browser failures

- **Suspending from an animation callback:** the first bundle stayed black when
  font initialization reached coroutine `runBlocking` from a JavaScript callback.
  gdx-teavm `9ac36c5` moved animation work to a persistent render executor. The
  focused browser test proved the prior direct-callback path failed and the
  executor path preserves serial frames and Java thread identity.
- **DecimalFormat omitted suffix:** startup error rendering found a null negative
  suffix. TeaVM `cd3eddd9b` represents an omitted suffix as empty while retaining
  explicit-null setter behavior; JVM and generated-JS affix contracts passed.
- **Unmatched regex capture:** language-picker keyboard labels used replacement
  `$1$4 $2$3$5` with optional groups. TeaVM `fee13672c` maps unmatched captures to
  empty text both in initial replacement parsing and cached replacement parts.
  Alternating matches, no-match input and `appendReplacement` passed JVM and JS
  contracts; the prior implementation failed.
- **Serialized scene/game data:** start-game JSON loading exposed missing
  `FileTextureData.pixmap` metadata. gdx-teavm/Unciv metadata was restricted to
  actual fields and constructors instead of preserving whole packages. The
  latest new-game run passed; save reload passes after the targeted metadata additions above.
- **Throwable stacks:** coroutine stack recovery initially received an empty JS
  stack. TeaVM `76beab7ed` captures native JS frames and preserves writable-stack
  semantics; JVM, generated-JS, print-only contracts and Checkstyle passed.
- **Compiler optimizer:** the first full analysis exhausted 4 GB and needed an
  8 GB TeaVM worker. It then exposed a dominator error from an unreachable
  predecessor, followed by an unreachable preheader bug around exception edges.
  TeaVM fixes `f2de7ba8c` and `5757a02e8` pass their focused regressions and the
  full game compile. Historical logs include
  `teavm-js-0.16-json-stack-8g.log`,
  `teavm-js-0.16-optimizer-stacktrace.log`, and
  `teavm-js-0.16-dominator-fix.log`.

### Earlier browser caveats

The initial headless launch logged invalid `glTexParameterf` magnification-filter
enums from atlas metadata. The same filter was supplied by the existing atlas
configuration; it was not the cause of the black canvas or later startup errors.
That warning is separate from the validated gameplay path.
