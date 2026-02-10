# `<repo>` Web E2E Phase-3 Plan (Capability Expansion + Full Parity)

## Summary
Phase 3 moves web from "stable single-player subset" to "broad product parity" by enabling currently disabled capabilities and hardening them for real users.  
Scope is expansion, not just stabilization: multiplayer paths, online mod flows, advanced I/O, and production runtime quality at scale.

## Locked Outcomes
1. Preserve Phase-2 no-regression gates and baseline diff requirements.
2. Add new features only behind deterministic browser tests.
3. Keep Docker-only toolchain and browser-first CI validation.
4. Keep core logic shared; web-specific behavior only where runtime constraints require it.

## Phase-3 Scope (In)
1. Web multiplayer enablement (UI + network runtime + sync behavior).
2. Online mod download/update enablement on web.
3. Web file import/export UX beyond clipboard-only flows.
4. Audio behavior parity improvements (unlock, latency, fallback handling).
5. Runtime packaging/performance improvements for production traffic.

## Phase-3 Scope (Out)
1. Renderer migration to Three.js.
2. Large gameplay-rule rewrites unrelated to web compatibility.
3. New game features not present on desktop/mobile.

## Step 0: Phase-2 Baseline Lock
1. Pin a baseline from latest green phase-2 commit.
2. Snapshot artifacts:
   - `tmp/baseline/<commit>/web-validation-result.json`
   - `tmp/baseline/<commit>/js-browser-tests-result.json`
   - key screenshots + startup timing
3. Record baseline in `arch_web.md`.

## Step 1: Capability Flags Migration Plan
1. Audit every `PlatformCapabilities` gate currently disabling web.
2. Classify each as:
   - `ready_to_enable`
   - `requires_runtime_work`
   - `requires_product_decision`
3. Convert phase-1 hard disables into phase-3 staged flags:
   - alpha flag
   - beta flag
   - default-on

## Step 2: Multiplayer on Web
1. Re-enable multiplayer UI entry points behind staged flag.
2. Replace/complete web stubs for multiplayer networking with production-safe implementation.
3. Validate:
   - connect/auth
   - turn submit/poll loop
   - reconnect and stale-state recovery
4. Add browser E2E scenarios:
   - two-client turn handoff
   - transient disconnect and recovery
   - conflict resolution on out-of-date state
5. Gate deploy if multiplayer smoke fails when flag is enabled in CI matrix.

## Step 3: Online Mod Download/Update on Web
1. Re-enable mod browser/update UI behind staged flag.
2. Harden web HTTP and archive extraction flows:
   - retries/backoff
   - checksum validation
   - partial download cleanup
3. Implement safe storage layout for downloaded mod assets in browser storage.
4. Add browser E2E:
   - install mod
   - restart/reload persistence
   - update installed mod
   - remove mod cleanly

## Step 4: Advanced Import/Export Flows
1. Add browser-native file picker/save flows (File System Access API with fallback).
2. Keep clipboard path as fallback, not primary.
3. Validate:
   - import external save file
   - export save file
   - re-import exported save
4. Add strict error surfacing for unsupported browser capabilities.

## Step 5: Audio and Input Parity
1. Improve first-interaction audio unlock reliability.
2. Reduce first-play sound latency and cache warm-up issues.
3. Validate pointer/touch/keyboard parity in browser:
   - desktop mouse + keyboard
   - touch interactions for core world actions
4. Add browser matrix runs for Chromium + Firefox at minimum.

## Step 6: Runtime Performance and Packaging
1. Add production bundle budgets:
   - JS/WASM size thresholds
   - startup time thresholds
2. Add compressed asset delivery validation (`gzip`/`brotli`) in deploy profile.
3. Add cache headers and versioned asset strategy validation.
4. Evaluate optional texture pipeline enhancements only if measurable gains are proven.

## Step 7: CI Matrix Expansion
1. Keep existing required checks:
   - `Build and test`
   - `Detekt`
   - `Docker`
   - `Web Build + Pages (TeaVM)`
2. Add web capability matrix jobs:
   - base mode (phase-2 compatibility)
   - multiplayer-enabled mode
   - mods-enabled mode
3. Add browser matrix:
   - Chromium required
   - Firefox required
   - WebKit optional informational lane

## Step 8: Release Gating for Expanded Features
1. Deploy allowed only when:
   - base mode green
   - enabled-feature matrix green
   - no regression vs phase-2 baseline on mandatory checks
2. If expanded feature lanes fail:
   - deploy blocked when feature is default-on
   - deploy allowed only if feature remains default-off and explicitly marked beta

## Step 9: Documentation and Operations
1. Update `arch_web.md` with phase-3 capability decisions and rollout status.
2. Update `features.csv` with per-feature status and last verification timestamp.
3. Add operator runbook section:
   - how to disable feature flags quickly
   - rollback triggers and commands
4. Keep `progress.md` append-only with each meaningful success/failure.

## Test Plan (Phase-3 Required)
1. Build:
   - `./scripts/web/in-container.sh './gradlew :web:webBuildJs'`
2. Base browser validation:
   - `node scripts/web/run-web-validation.js`
3. Browser JS suite:
   - `node scripts/web/run-js-browser-tests.js`
4. Feature-lane validations:
   - multiplayer E2E scenario suite
   - mod install/update/remove E2E suite
   - file import/export E2E suite
5. Full regression check:
   - baseline diff must show no regressions in mandatory metrics

## Acceptance Criteria
1. Phase-2 checks remain green with no regressions.
2. Multiplayer works in browser E2E with reconnect recovery.
3. Mod install/update/remove works in browser and survives reload.
4. File import/export works in supported browsers with clear fallback behavior.
5. Expanded capability matrix is green for release configuration.
6. Deploy pipeline remains green end-to-end with feature flags set for target release mode.

## Risks and Mitigations
1. Risk: Browser APIs differ across vendors.
   Mitigation: capability detection + explicit fallbacks + browser matrix CI.
2. Risk: Multiplayer instability under browser timing/network variance.
   Mitigation: deterministic sync assertions, reconnect tests, strict timeout handling.
3. Risk: Mod pipeline introduces storage/perf regressions.
   Mitigation: checksum, cleanup, quotas, and bundle/runtime budgets with fail thresholds.

## Defaults and Assumptions
1. `master` remains release branch.
2. Pushes to `master` can be done through `tmux` workflow as established.
3. Expanded features ship staged (`alpha` -> `beta` -> default-on) with explicit CI gates.
4. No feature graduates to default-on without passing full browser matrix + regression diff gates.
