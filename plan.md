# Regression Recovery Plan (2026-02-15)

## Objective
Compare the fork against Yair upstream behavior, identify web regressions, fix root causes (not symptoms), and finish local green gates using local Temurin + local Node/Playwright.

## Baselines
- Worktree: `/Users/haimlamper/Unciv_worktrees/regression_02_15`
- Branch: `codex/regression-02-15`
- Fork head at start: `9093ea10d`
- Upstream reference: `upstream/master` at `7f8adf212`
- Regression baseline artifact commit (from web checker): `77ff2c79ea33dbdeb10ade98093a3bc6f3132564`

## Detection Plan
1. Sync upstream refs and compute divergence from fork head.
2. Run local build and e2e gate chain:
   - `./gradlew :web:webBuildJs`
   - `node scripts/web/run-web-validation.js` (`phase1`, `phase4-full`)
   - `node scripts/web/run-js-browser-tests.js`
   - `node scripts/web/run-web-multiplayer-multi-instance.js`
   - `node scripts/web/check-performance-budget.js`
   - `node scripts/web/check-regression.js`
3. For each failure, isolate root cause and patch shared code path.
4. Re-run full gate chain until all checks are green.
5. Record final evidence into `progress_02_15.md` and machine-readable `tmp/*.json` artifacts.

## Regression Inventory and Root-Cause Fix Plan

### R1: Gradle wrapper incompatible with local Temurin 25
- Symptom: local `:web:webBuildJs` fails early on Java version parsing (`IllegalArgumentException: 25.0.2`).
- Root cause: wrapper pinned to `gradle-8.11.1`, which is not safe for this local JDK path.
- Fix:
  - Upgrade wrapper distribution to `gradle-9.3.1`.
- Status: done.

### R2: Web SLF4J provider crashes in JVM-side web build path
- Symptom: `UnsatisfiedLinkError` from `WebConsoleBridge.log` during JVM execution.
- Root cause: `@JSBody` bridge methods invoked in non-TeaVM/JVM context with no safe fallback.
- Fix:
  - Add bridge mode detection and one-way fallback to JVM stdout/stderr in `WebConsoleBridge`.
  - Keep JS-native behavior when running in browser runtime.
- Status: done.

### R3: Chromium GL launch instability on macOS local Playwright gates
- Symptom: WebGL startup failures/page errors with hardcoded `--use-gl=swiftshader` defaults.
- Root cause: one-size Chromium launch flags are not cross-platform reliable.
- Fix:
  - Introduce shared `scripts/web/lib/chromium-args.js` resolver.
  - Use platform-aware defaults (macOS uses ANGLE swiftshader path).
  - Reuse resolver in all web Playwright runners to stay DRY.
- Status: done.

### R4: Multiplayer multi-instance probe startup/query flakiness
- Symptom: intermittent probe startup timeout/query loss/noisy false negatives.
- Root cause:
  - Probe runner assumed one-shot navigation retained query params.
  - No explicit "probe started" wait contract.
  - Over-strict console error filtering included known non-blocking deserialize noise.
- Fix:
  - Add probe URL retention check + retry navigation.
  - Add explicit `waitForProbeStart` and startup retry budget.
  - Treat known non-fatal deserialize message as ignorable probe noise.
  - Improve Kotlin probe host sync to use preview polling and safer guest fallback state updates.
- Status: done.

## Execution Results
All local gates pass after fixes:
1. `phase1` validation: PASS
2. `phase4-full` validation: PASS
3. JS browser suite: PASS
4. Multiplayer multi-instance probe: PASS
5. Performance budget: PASS
6. Regression diff check: PASS (`regressionIssues: []`)

## Verification Artifacts
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/web-validation-summary.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/js-browser-tests-result.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/web-multiplayer-multi-instance-result.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/perf-budget.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/regression-check.json`

## TeaVM Fork Contingency
No TeaVM compiler fork was required for this recovery pass. Existing local dependency chain is sufficient with the applied root-cause fixes.
