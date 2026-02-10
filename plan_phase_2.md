# `<repo>` Web E2E Phase-2 Plan (Parity + Hardening + Release Gating)

## Summary
Phase 1 delivered a working web target and green CI for build/e2e/deploy.  
Phase 2 makes web release-ready by enforcing deterministic browser parity, removing remaining drift from desktop/mobile behavior, and tightening quality gates so regressions are blocked before deploy.
Phase 2 additionally requires explicit no-regression proof on every merge to `master`.

## Locked Goals
1. Keep Docker-only build/test pipeline.
2. Keep browser-based validation as primary gate for web.
3. Keep performance-sensitive code DRY and avoid web-only forks unless required by runtime constraints.
4. No silent fallbacks for critical gameplay logic.
5. Not tested = not done.
6. No regression accepted without a documented waiver.

## Out of Scope
1. Enabling multiplayer in phase 2.
2. Enabling online mod download/update in phase 2.
3. Re-architecting rendering stack away from LibGDX.

## Phase-2 Deliverables
1. Deterministic startup and world generation parity checks in browser.
2. Stable font rendering metrics (no baseline jitter) across main UI and world UI.
3. Deterministic unit control behavior in browser (move, explore, automate, found city, combat).
4. Full browser JS test execution integrated and reported in CI artifacts.
5. Release gating policy updated so deploy requires browser gameplay validation + browser JS suite pass.
6. Updated architecture and test docs for phase-2 runtime behavior.
7. Regression lock protocol integrated into CI and release workflow.

## Regression Lock Protocol (Mandatory)
1. Every web-affecting PR must publish a baseline-vs-candidate comparison artifact.
2. Required comparison targets:
   - Startup success and time-to-main-menu
   - Start new game to world-screen success
   - Unit action validity (settle, move, explore, automate, basic combat)
   - Save/load and clipboard roundtrip
   - Browser JS suite totals and failing-class diff
3. Merge to `master` is blocked if any required comparison has:
   - new failure
   - increased crash count
   - increased console/page critical errors
4. Allowed degradations require explicit waiver note in `progress.md` and PR description with rollback plan.

## Step 0: Baseline Freeze (Before Any New Changes)
1. Pin a baseline commit hash from green `master`.
2. Store baseline artifacts under `tmp/baseline/<commit>/`:
   - `web-validation-result.json`
   - `web-validation-summary.json`
   - `js-browser-tests-result.json`
   - key screenshots
3. Store baseline timing and counts:
   - startup duration
   - world-entry duration
   - total JS tests, failures, ignored
4. Add baseline hash and artifact paths into `arch_web.md`.

## Step 1: Baseline and Drift Audit
1. Compare candidate artifacts against the frozen baseline.
2. Capture browser artifacts for:
   - Main menu screenshot
   - New game options screenshot
   - World-start screenshot
   - JS suite summary JSON
3. Capture matching desktop reference artifacts for visual and logic comparisons.
4. Build a parity checklist in `features.csv` notes for each critical flow.
5. Fail the candidate if any required flow regresses from baseline.

## Step 2: Gameplay Parity Hardening
1. Enforce deterministic new-game spawn validation:
   - Settler and warrior do not overlap incorrectly.
   - First-turn movement points are valid and non-zero when expected.
2. Validate tile movement rules:
   - Movement cost and impassable terrain parity.
   - Explore/automate actions trigger and advance state.
3. Validate city founding flow:
   - Found city action appears and executes.
   - City naming and initialization are stable.
4. Add browser-side assertions in runtime validation harness for each flow.
5. Add deterministic checks that selected unit changes after each action (state delta required).
6. Add guard checks that tiles are not erroneously globally traversable or globally blocked.

## Step 3: Font and UI Rendering Hardening
1. Normalize glyph baseline/ascent/descent calculations in web font rasterizer.
2. Add regression assertions:
   - No per-character vertical drift for same font size/style.
   - Legibility thresholds for key UI labels.
3. Keep fallback path explicit and logged when used.
4. Save reference screenshots for smoke diffing on CI failures.
5. Add text layout sanity checks for key labels to ensure stable baseline alignment.

## Step 4: JSON/Serialization Safety Layer
1. Audit all web-reachable JSON load paths for missing-field behavior.
2. Replace fragile reflection-dependent behavior with explicit hydration where needed.
3. Add guardrails:
   - Fail-fast with actionable message for required fields.
   - Safe defaults only for non-critical optional fields.
4. Add targeted tests for high-risk payloads:
   - Save/load roundtrip
   - Ruleset hydration
   - Skin/style object loading
5. Add schema drift detector for critical save/ruleset payload fields used on web.

## Step 5: CI and Deploy Gate Strengthening
1. Keep `Web Build + Pages (TeaVM)` as deploy source of truth.
2. Require all three jobs for deploy:
   - `web-build`
   - `web-e2e`
   - browser JS suite
3. Ensure artifacts are uploaded on both success and failure:
   - `web-validation-result.json`
   - `web-validation-summary.json`
   - `js-browser-tests-result.json`
   - latest screenshots
4. Add explicit workflow summary output with:
   - pass/fail counts
   - blocking test list
   - deploy decision reason
5. Add regression summary section:
   - baseline hash
   - changed metrics
   - changed failing tests/classes
6. Enforce `fail-on-regression` even when absolute test counts are still mostly green.

## Step 6: Performance and Stability Guardrails
1. Track web startup time and first-interaction time in validation output.
2. Track memory-sensitive paths (asset load, ruleset load, first world render).
3. Add non-flaky time budgets for CI (warn/fail thresholds).
4. Ensure no unnecessary duplicate logic in web path vs core path.
5. Add guard budget thresholds:
   - startup regression fail if >20% slower than baseline
   - world-entry regression fail if >20% slower than baseline
   - warn threshold at >10%

## Step 7: Documentation and Traceability
1. Update `arch_web.md` with phase-2 decisions and final gating rules.
2. Keep `features.csv` current with `last_verified` timestamps from CI runs.
3. Keep `progress.md` append-only with every meaningful step and failure.
4. Document known intentional deviations from desktop/mobile behavior.
5. Keep a running "regressions prevented" changelog in `arch_web.md`.

## Step 8: Regression Triage and Rollback Rules
1. On CI regression:
   - stop deploy
   - classify root cause: gameplay, rendering, serialization, test harness, infra
   - attach artifacts and failing commit
2. If fix is not ready in same PR:
   - revert web-affecting commit(s) before merge
   - reopen follow-up issue with artifact links
3. No "temporary skip" of gameplay-critical tests without explicit expiry and owner.

## Test Plan (Phase-2 Required)
1. Container build:
   - `./scripts/web/in-container.sh './gradlew :web:webBuildJs'`
2. Browser runtime validation:
   - `node scripts/web/run-web-validation.js`
3. Browser JS suite:
   - `node scripts/web/run-js-browser-tests.js`
4. Non-browser regression:
   - `./gradlew check`
5. Release gate:
   - Deploy blocked unless all required jobs pass.
6. Regression diff gate:
   - Candidate must be >= baseline on all required parity checks.

## Required Checks Per Merge
1. `Build and test` green.
2. `Detekt` green.
3. `Docker` green.
4. `Web Build + Pages (TeaVM)` green.
5. Regression diff report present and non-regressing.

## Acceptance Criteria
1. Latest `Web Build + Pages (TeaVM)` run is fully green, including deploy.
2. Browser validation proves:
   - New game starts
   - Settler can move and found city
   - Warrior can move and engage combat
   - End-turn loop advances
3. Browser JS suite completes with zero failures in release-gating run.
4. No unresolved critical console/page errors in validation run.
5. `features.csv` reflects current truth with verification timestamp.
6. Regression diff report shows no newly failing mandatory checks vs baseline.
7. Deploy job is blocked automatically when regression is detected.

## Risks and Mitigations
1. Risk: Browser timing flakes.
   Mitigation: deterministic waits on state flags, bounded retries, fail with artifacts.
2. Risk: Reflection/serialization drift in TeaVM.
   Mitigation: explicit hydrators for critical types + targeted tests.
3. Risk: Visual regressions not caught by logic tests.
   Mitigation: screenshot artifacts + baseline comparisons for key screens.

## Defaults and Assumptions
1. `master` remains release branch.
2. Pushes use `tmux` when requested by workflow policy constraints.
3. Browser CI uses deterministic mode appropriate for runner stability.
4. Phase-2 maintains phase-1 feature gates for unsupported web capabilities.
