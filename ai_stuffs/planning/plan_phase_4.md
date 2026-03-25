# `<repo>` Web E2E Phase-4 Plan (Default-On + Production Gates)

## Summary
Phase 4 graduates web capabilities to default-on while keeping rollback switches, strict browser E2E gates, and performance/regression budgets.

## Phase 4 Scope
1. Default-on web profile for multiplayer, mod downloads, and custom file chooser.
2. Runtime rollback flags for emergency disable without code rollback.
3. CI lanes upgraded from phase3 labels to phase4 labels.
4. Mandatory gates: browser gameplay validation, browser JS suite, performance budget, regression diff.

## Implemented Changes
1. Capability/profile graduation:
   - `PlatformCapabilities.WebProfile.PHASE4_FULL` added.
   - `PlatformCapabilities.webDefaultsProfile()` now returns `PHASE4_FULL`.
   - `webPhase4Full()` and `webPhase4Staging()` added.
2. Runtime rollback controls:
   - `applyWebFeatureRollbacks(...)`
   - `applyWebStagingRollbacks(...)`
   - `hasWebRollbacksApplied(...)`
   - `describeWebRollbacks(...)`
3. Web launcher/profile resolution:
   - profile aliases include `phase4-*`.
   - default profile is phase4-full when no profile is provided.
   - runtime overrides via `webRollback`, `webDisableMultiplayer`, `webDisableFileChooser`, `webDisableMods`/`webDisableModDownloads`.
4. JS test/runtime profile handling:
   - browser JS suite defaults to `phase4-full`.
   - `WebJsTestsGame` no longer force-sets phase1 capabilities.
5. CI workflow migration:
   - `web-phase4-alpha`, `web-phase4-beta`, `web-phase4-full`.
   - `web-e2e` runs phase1 baseline + phase4-full validation + browser JS suite.
   - added performance budget gate and artifact (`tmp/performance-budget-summary.json`).
6. Performance budget gate:
   - script: `scripts/web/check-performance-budget.js`
   - validates startup/world-entry timings and dist size budgets.

## Validation Matrix (Required)
1. `:web:webBuildJs` in container.
2. `run-web-validation.js` with `WEB_PROFILE=phase1`.
3. `run-web-validation.js` with `WEB_PROFILE=phase4-full`.
4. `run-js-browser-tests.js` with `WEB_PROFILE=phase4-full`.
5. `check-performance-budget.js`.
6. `check-regression.js`.
7. Informational phase4-alpha and phase4-beta validations.

## Current Status
1. Local container validations passed on 2026-02-11:
   - phase4-full gameplay validation: pass.
   - phase4-alpha and phase4-beta validations: pass.
   - browser JS suite: pass (225 run, 0 failures, 5 ignored).
   - performance budget gate: pass.
   - regression diff gate: pass.
2. Workflow is updated to enforce phase4 lanes and gates.
3. Remaining closeout step: keep CI green after phase4 commit push.

## Rollback Policy
1. Emergency disable-all: `webRollback=1`.
2. Targeted disables:
   - `webDisableMultiplayer=1`
   - `webDisableFileChooser=1`
   - `webDisableMods=1` or `webDisableModDownloads=1`
3. Rollback state is logged on startup.

