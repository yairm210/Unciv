# Unciv Web Architecture Decisions (Phase-1)

Last updated: 2026-02-08

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

## 3) Explicitly Deferred (May Change Later)

These are deferred, not rejected permanently.

1. Three.js integration: only reconsider if LibGDX web rendering becomes a hard blocker.
2. libKTX/texture compression pipeline: evaluate after functional parity and perf baselines.
3. Re-enabling multiplayer: after web auth/network/runtime stability and UI flow validation.
4. Re-enabling online mod operations: after binary/network compatibility is validated on TeaVM web.
5. Re-enabling custom file picker IO: after stable browser file system policy and UX are finalized.
6. Expanded font strategy (system fonts/full selection): after deterministic baseline font path is proven stable.

## 4) Why These Locks Exist

1. Minimize unknowns during first web bring-up.
2. Keep architecture single-renderer and avoid split ownership.
3. Establish deterministic runtime behavior before optimization layers.
4. Separate parity/stability work from optimization/feature expansion.

## 5) Change-Control Rule for Deferred Decisions

A deferred decision can be changed only when all are true:

1. Phase-1 smoke matrix passes in containerized builds.
2. Capability-gated behavior is stable and regression-tested.
3. Change has a written impact note (bundle size/startup/perf/risk/testing).
4. Change has a rollback path.

## 6) Current Phase-1 Acceptance Focus

1. `:web:webBuildWasm` and `:web:webBuildJs` succeed in container tooling.
2. App boots from static files without fatal startup errors.
3. Main menu/start new game/end-turn/save-load/clipboard roundtrip works.
4. Disabled-by-design web features are hidden or non-actionable in UI flows.

## 7) Runtime Validation Status (Current)

1. JS target (`:web:webBuildJs`) is currently the validated gameplay path in this environment:
   - `tmp/run-web-validation.js` passes full matrix (boot/start-game/end-turn/save-load/clipboard/audio/font/external-link and disabled-by-design gates), with explicit `Start new game` notes for Settler founding and Warrior melee combat.
   - headed browser repro confirms quickstart opens world, settler action list includes `FoundCity`, and web movement success markers for warrior/settler.
2. Web correctness fix applied at source:
   - `Ruleset.loadNamedArray()` web fallback hydration now restores full `BaseUnit` fields from raw JSON (not only name/unitType), which removes partial-unit initialization on TeaVM and unblocks settler actions + movement logic.
3. WASM target (`:web:webBuildWasm`) compiles successfully, but runtime in current Playwright Chromium still fails before boot with repeated `dereferencing a null pointer`.
4. Attempting to switch TeaVM target from `WEBASSEMBLY_GC` to `WEBASSEMBLY` is not viable with this backend set:
   - compile-time native import annotation errors in `backend-web` classes.
5. Current operational decision:
   - keep WASM-first build support in place (`WEBASSEMBLY_GC`) and track runtime blocker separately.
   - use JS build for E2E regression validation until WASM runtime issue is resolved.
