# `<repo>` Web E2E Phase-4 Plan (Full Parity + Production Readiness)

## Summary
Phase 4 completes full feature parity and production-grade operations for web. This phase assumes Phase 3 gates are green and expands to default-on capability sets with strict stability and performance budgets.

## Preconditions
1. Phase 3 closeout checklist complete.
2. `Web Build + Pages (TeaVM)` green on `master` with deploy.
3. Regression baseline locked and referenced in `arch_web.md`.

## Scope (In)
1. Default-on multiplayer capability on web (feature-flagged but enabled by default).
2. Default-on mod download/update pipeline with robust storage and quota handling.
3. Default-on browser file picker import/export with fallback UI.
4. Audio parity for autoplay gating, latency, and fallback behavior across browsers.
5. Production performance budgets enforced (startup, world-entry, memory, asset cache).

## Scope (Out)
1. Renderer migration to non-LibGDX stacks.
2. New gameplay features unrelated to web parity.

## Step 1: Capability Graduation to Default-On
1. Promote multiplayer/mods/file picker from staged flag to default-on.
2. Keep explicit runtime opt-out switch for emergency rollback.
3. Document flags and rollback in `arch_web.md`.

## Step 2: Multiplayer Production Hardening
1. Validate multi-client sync with reconnect and conflict resolution.
2. Add WebRTC fallback only if required by backend constraints.
3. Browser E2E: two clients, turn handoff, reconnect, stale recovery.

## Step 3: Mods Production Hardening
1. Enforce checksum + integrity verification.
2. Implement cleanup for partial downloads and failed installs.
3. Verify storage quotas and error surfaces for low-space cases.
4. E2E: install, reload, update, remove.

## Step 4: File I/O Production Hardening
1. File System Access API with safe fallback UI.
2. Ensure save/export/import works across reload and browsers.
3. E2E: import external save, export, re-import.

## Step 5: Audio and Input Parity
1. Resolve autoplay unlock and ensure first interaction audio.
2. Reduce latency by warm-up and caching.
3. Verify keyboard/mouse/touch parity in gameplay.

## Step 6: Performance Budgets
1. Enforce budgets:
   - startup time
   - world-entry time
   - JS/WASM payload size
2. Fail CI when budgets regress beyond thresholds.

## CI Matrix (Phase 4)
1. Chromium required (full suite).
2. Firefox required if WebGL parity is resolved; otherwise keep informational lane with explicit waiver.
3. WebKit optional informational.

## Test Plan (Phase 4 Required)
1. Container build `:web:webBuildJs`.
2. Phase1 + Phase4 validation runs (profile-specific).
3. Browser JS suite (chromium).
4. Multiplayer E2E suite.
5. Mods E2E suite.
6. File I/O E2E suite.
7. Performance budget checks.

## Acceptance Criteria
1. All Phase 4 CI lanes green.
2. No regression vs baseline on mandatory checks.
3. Multiplayer/mods/file picker default-on without critical regressions.
4. Deploy pipeline green with artifacts and reports.

