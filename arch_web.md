# Unciv Web Architecture Decisions (Phases 1–3)

Last updated: 2026-02-11

## 1) Locked Decisions (Phase-1)

These are intentionally fixed for the phase-1 web deliverable.

1. Runtime: TeaVM via `gdx-teavm` (snapshot track), WASM-first.
2. Scope: playable single-player core loop + local browser-backed saves.
3. Packaging target: static deploy bundle (`web/build/dist`) for CDN/static hosting.
4. Rendering stack: keep existing LibGDX rendering pipeline; do not add Three.js.
5. Texture pipeline: keep current PNG/atlas assets; do not add libKTX in phase-1.
6. Tooling: Docker/devcontainer-first workflow (no host Java requirement).
7. Web phase-1 capability gates:
   - online multiplayer disabled
   - custom-location file picker save/load disabled
   - online mod download/update disabled
   - system font enumeration disabled

## 2) Snapshot Compatibility Decisions (Alterable Later)

These are temporary implementation decisions and may be reverted once upstream snapshot/tag artifacts are aligned.

1. Web build currently uses `com.github.xpenatan.gdx-teavm:backend-teavm:-SNAPSHOT` and `com.github.xpenatan.gdx-teavm:gdx-freetype-teavm:-SNAPSHOT`.
2. Snapshot import namespace is `com.github.xpenatan.gdx.teavm.backends.*` (renamed from legacy `com.github.xpenatan.gdx.backends.teavm.*`).
3. Added compatibility shim `web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/assetloader/TeaBlob.java` to bridge backend runtime references to `TeaBlob` against snapshot asset-loader rename.
4. `TeaBuildConfiguration.reflectionListener` assignment is intentionally disabled in web build bootstrap due split-jar type mismatch in current snapshot set.
5. Web save payloads currently use a runtime snapshot-token transport (`WEBSNAP:<id>`) backed by an in-memory clone cache in `UncivFiles` for TeaVM correctness (avoids broken reflective JSON payloads on web runtime).
6. TeaVM reflection preservation is hardened by marker-based class discovery:
   - scan `com/unciv/` classpath entries
   - include classes assignable to `IsPartOfGameInfoSerialization`, `IRulesetObject`, or `Json.Serializable`
   - exclude `com.unciv.logic.multiplayer.*` and `com.unciv.ui.screens.devconsole.*` from auto-preserve to avoid web-unsupported pull-ins/static-init failures.

## 3) Phase-3 Decisions (Capability Enablement)

1. Web multiplayer uses HTTP file storage + WebSocket chat; no WebRTC.
2. Web HTTP layer is fetch-based (TeaVM JS interop) and shared across multiplayer + GitHub.
3. Mod downloads on web use GitHub API + ZIP extraction (JSZip via TeaVM interop).
4. Web file import/export uses File System Access API with fallback to input/download flows.
5. Capability staging is enforced via `PlatformCapabilities.currentStaging`:
   - alpha: file chooser enabled
   - beta: file chooser + mod downloads enabled
   - full: multiplayer + mods + file chooser enabled
6. CI multiplayer validation uses a local test server (`scripts/web/multiplayer-test-server.js`).

## 4) Explicitly Deferred (May Change Later)

These are deferred, not rejected permanently.

1. Three.js integration: only reconsider if LibGDX web rendering becomes a hard blocker.
2. libKTX/texture compression pipeline: evaluate after functional parity and perf baselines.
3. Broader multiplayer rollout to public servers: after production auth/network monitoring proves stable.
4. Mod marketplace expansion beyond GitHub ZIP path: after storage/perf budgets are proven.
5. System font enumeration and selection UI: after deterministic baseline remains stable across browsers.
6. Expanded font strategy (system fonts/full selection): after deterministic baseline font path is proven stable.

## 5) Why These Locks Exist

1. Minimize unknowns during first web bring-up.
2. Keep architecture single-renderer and avoid split ownership.
3. Establish deterministic runtime behavior before optimization layers.
4. Separate parity/stability work from optimization/feature expansion.

## 6) Change-Control Rule for Deferred Decisions

A deferred decision can be changed only when all are true:

1. Phase-1 smoke matrix passes in containerized builds.
2. Capability-gated behavior is stable and regression-tested.
3. Change has a written impact note (bundle size/startup/perf/risk/testing).
4. Change has a rollback path.

## 7) Current Phase-1 Acceptance Focus

1. `:web:webBuildWasm` and `:web:webBuildJs` succeed in container tooling.
2. App boots from static files without fatal startup errors.
3. Main menu/start new game/end-turn/save-load/clipboard roundtrip works.
4. Disabled-by-design web features are hidden or non-actionable in UI flows.

## 8) Runtime Validation Status (Current)

1. JS target (`:web:webBuildJs`) is the validated gameplay path in this environment:
   - `scripts/web/run-web-validation.js` passes full matrix (boot/start-game/end-turn/save-load/clipboard/audio/font/external-link and disabled-by-design gates), with explicit `Start new game` notes for Settler founding and Warrior melee combat.
   - headed browser repro confirms quickstart opens world, settler action list includes `FoundCity`, and web movement success markers for warrior/settler.
2. Web correctness fix applied at source:
   - `Ruleset.loadNamedArray()` web fallback hydration now restores full `BaseUnit` fields from raw JSON (not only name/unitType), which removes partial-unit initialization on TeaVM and unblocks settler actions + movement logic.
3. Web correctness hardening for JSON loads:
   - `BuildWebCommon` now auto-preserves serialization marker types (not broad package preserve), reducing future field-stripping regressions while keeping TeaVM compile/runtime stable.
4. WASM target (`:web:webBuildWasm`) compiles successfully, but runtime in current Playwright Chromium still fails before boot with repeated `dereferencing a null pointer`.
5. Attempting to switch TeaVM target from `WEBASSEMBLY_GC` to `WEBASSEMBLY` is not viable with this backend set:
   - compile-time native import annotation errors in `backend-web` classes.
6. Current operational decision:
   - keep WASM-first build support in place (`WEBASSEMBLY_GC`) and track runtime blocker separately.
   - use JS build for E2E regression validation until WASM runtime issue is resolved.
7. Phase-3 validation lanes now pass locally:
   - phase3-alpha: file import/export path
   - phase3-beta: mod download/update/remove
   - phase3-full: multiplayer file storage + chat

## 9) Browser JS Test Harness Status (Current)

1. All test sources are now compiled into the web JS build path:
   - `web/build.gradle.kts` includes `../tests/src` and generated browser suite code.
   - `CountableTests` is included (web compile classpath now includes `kotlin-reflect`).
   - Minimal JUnit/Hamcrest compatibility stubs exist under `web/src/main/java/org/junit/*`, `web/src/main/java/junit/*`, and `web/src/main/java/org/hamcrest/*` for TeaVM/browser compileability.
2. Browser execution mode is enabled via `index.html?jstests=1`:
   - launcher routes to `WebJsTestsGame`.
   - runner executes generated suite in Chromium and publishes JSON (`window.__uncivJsTestsResultJson`).
3. Current measured browser JS suite result (Chromium):
   - classCount: 27
   - totalRun: 225
   - totalFailures: 0
   - totalIgnored: 5
4. Baseline and regression gate:
   - committed baseline file: `web/baseline/regression-baseline.json` (source commit: `77ff2c79ea33dbdeb10ade98093a3bc6f3132564`).
   - gate script: `scripts/web/check-regression.js`, comparing candidate artifacts vs committed baseline.
5. Decision for release gating:
   - require browser E2E validation (`scripts/web/run-web-validation.js`), browser JS suite (`scripts/web/run-js-browser-tests.js`), and regression gate (`scripts/web/check-regression.js`) to pass.
   - deploy is blocked on any regression in fail/blocked counts, console/page critical errors, or JS suite failures.
6. Capability rollout staging for phase 3 is tracked in `docs/web-capability-staging.md` and exposed in `PlatformCapabilities.currentStaging`.

## 10) Browser Compatibility Matrix (Current)

1. Deploy-gating browser lane:
   - Chromium headless, full gameplay validation + full browser JS suite + regression diff gate.
2. Compatibility-only lane:
   - Firefox headless smoke validation is executed as informational (`continue-on-error`) and artifacts are uploaded.
3. Current Firefox blocker:
   - runtime page error before validation completion:
     - `TypeError: can't access property "pixelStorei", $this.$gl is null`
   - this is tracked as a phase-3 compatibility task and does not currently block GitHub Pages deploy.
