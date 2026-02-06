# Unciv Web Architecture Decisions (Phase-1)

Last updated: 2026-02-06

## 1) Locked Decisions (Phase-1)

These are intentionally fixed for the phase-1 web deliverable.

1. Runtime: TeaVM via `gdx-teavm` (`1.4.0`), WASM-first.
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

## 2) Explicitly Deferred (May Change Later)

These are deferred, not rejected permanently.

1. Three.js integration: only reconsider if LibGDX web rendering becomes a hard blocker.
2. libKTX/texture compression pipeline: evaluate after functional parity and perf baselines.
3. Re-enabling multiplayer: after web auth/network/runtime stability and UI flow validation.
4. Re-enabling online mod operations: after binary/network compatibility is validated on TeaVM web.
5. Re-enabling custom file picker IO: after stable browser file system policy and UX are finalized.
6. Expanded font strategy (system fonts/full selection): after deterministic baseline font path is proven stable.

## 3) Why These Locks Exist

1. Minimize unknowns during first web bring-up.
2. Keep architecture single-renderer and avoid split ownership.
3. Establish deterministic runtime behavior before optimization layers.
4. Separate parity/stability work from optimization/feature expansion.

## 4) Change-Control Rule for Deferred Decisions

A deferred decision can be changed only when all are true:

1. Phase-1 smoke matrix passes in containerized builds.
2. Capability-gated behavior is stable and regression-tested.
3. Change has a written impact note (bundle size/startup/perf/risk/testing).
4. Change has a rollback path.

## 5) Current Phase-1 Acceptance Focus

1. `:web:webBuildWasm` and `:web:webBuildJs` succeed in container tooling.
2. App boots from static files without fatal startup errors.
3. Main menu/start new game/end-turn/save-load/clipboard roundtrip works.
4. Disabled-by-design web features are hidden or non-actionable in UI flows.

