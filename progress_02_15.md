# Progress 2026-02-15

## Baseline
- Date: 2026-02-15
- Workspace: /Users/haimlamper/Unciv_worktrees/regression_02_15
- Branch: codex/regression-02-15
- HEAD: 9093ea10d
- Upstream baseline: upstream/master @ 7f8adf212
- Delta vs upstream: 110 commits ahead, 10 commits behind
- Goal: detect regressions vs Yair upstream behavior using local web build + browser/e2e gates, plan + implement root-cause fixes, and finish green.

## Planned gate sequence
1. ./gradlew :web:webBuildJs
2. bash scripts/web/check-slf4j-provider.sh .
3. Serve web/build/dist locally + start multiplayer test server
4. node scripts/web/run-web-validation.js (phase1 then phase4-full)
5. node scripts/web/run-js-browser-tests.js
6. node scripts/web/run-web-multiplayer-multi-instance.js
7. node scripts/web/check-performance-budget.js
8. node scripts/web/check-regression.js

## Notes
- Prefer local Temurin Java and local Node/Playwright runs.
- Containerized fallback only if local dependency is missing.

## Execution Log
- 2026-02-15T00:59Z | worktree | created `/Users/haimlamper/Unciv_worktrees/regression_02_15` from `master` on `codex/regression-02-15` | ready for isolated recovery | fetch upstream + diff
- 2026-02-15T01:00Z | baseline | fetched `upstream/master` at `7f8adf212`; compared divergence from local head `9093ea10d` | baseline established | run local gates
- 2026-02-15T01:02Z | build | ran `./gradlew :web:webBuildJs` | failed with Java version parse error (`25.0.2`) on Gradle 8.11.1 | upgrade wrapper
- 2026-02-15T01:05Z | build-fix | updated `gradle/wrapper/gradle-wrapper.properties` to Gradle 9.3.1 | Java 25 compatibility restored | rerun build
- 2026-02-15T01:14Z | build | reran `./gradlew :web:webBuildJs` | failed with `UnsatisfiedLinkError` in `WebConsoleBridge.log` | patch SLF4J bridge fallback
- 2026-02-15T01:18Z | runtime-fix | patched `web/src/main/java/org/slf4j/impl/WebConsoleBridge.java` with JS/JVM mode fallback | JVM build path no longer crashes on JS native bridge | rerun build
- 2026-02-15T01:29Z | build | reran `./gradlew :web:webBuildJs` | PASS | run web validations
- 2026-02-15T01:34Z | e2e | ran phase validation with Chromium | intermittent macOS GL startup failures observed | unify platform-aware Chromium args
- 2026-02-15T01:40Z | e2e-fix | added `scripts/web/lib/chromium-args.js` and reused in validation/js-suite/mp scripts | launch args DRY and platform-safe | rerun e2e suite
- 2026-02-15T01:52Z | multiplayer | multi-instance probe showed startup/query flakiness and noisy deserialize false negatives | root cause isolated to startup contract/query retention | harden probe runner
- 2026-02-15T02:01Z | multiplayer-fix | patched `scripts/web/run-web-multiplayer-multi-instance.js` and `web/src/main/kotlin/com/unciv/app/web/WebMultiplayerProbeRunner.kt` | startup retries + probe-start wait + safer preview-based sync | rerun full local gates
- 2026-02-15T03:08Z | gate | `node scripts/web/run-web-validation.js` (`phase1`) | PASS (`pass=12 fail=0 blocked=0`, no page/console errors) | continue
- 2026-02-15T03:08Z | gate | `node scripts/web/run-web-validation.js` (`phase4-full`) | PASS (`pass=12 fail=0 blocked=0`, no page/console errors) | continue
- 2026-02-15T03:09Z | gate | `node scripts/web/run-js-browser-tests.js` | PASS (`status=PASSED`, `totalRun=226`, `totalFailures=0`) | continue
- 2026-02-15T03:10Z | gate | `node scripts/web/run-web-multiplayer-multi-instance.js` | PASS (`status=PASSED`, host/guest both passed, no page/console errors) | continue
- 2026-02-15T03:11Z | gate | `node scripts/web/check-performance-budget.js` | PASS (`issues=[]`) | continue
- 2026-02-15T03:11Z | gate | `node scripts/web/check-regression.js` | PASS (`regressionIssues=[]`, baseline `77ff2c79`) | finalize docs

## Final Artifacts
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/web-validation-summary.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/js-browser-tests-result.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/web-multiplayer-multi-instance-result.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/perf-budget.json`
- `/Users/haimlamper/Unciv_worktrees/regression_02_15/tmp/regression-check.json`

## Outcome
- Full local web build + e2e + regression/performance gates are green after root-cause fixes.
- TeaVM compiler fork was evaluated as contingency and not required for this pass.
