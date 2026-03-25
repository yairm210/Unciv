# Upstream Sync Plan

## Goal
Update this fork so `master` incorporates the current `upstream/master` from `https://github.com/yairm210/unciv` while preserving the fork's web-specific functionality, build tasks, and validation tooling.

## Current Measured State
- Local branch: `master`
- Working tree: clean before changes
- Fork remote: `origin=https://github.com/haimlm/unciv-web`
- Upstream remote: `upstream=https://github.com/yairm210/unciv.git`
- Divergence vs upstream at planning time: `master` is `275` commits ahead and `77` commits behind `upstream/master`
- Upstream head fetched for merge target: `51c07b743cf5827b9da21871f66140237f079e0f` (`4.19.19`)

## Strategy
1. Create a safety restore point before changing history.
2. Merge `upstream/master` into local `master` rather than rebasing.
3. Resolve conflicts in favor of keeping upstream gameplay changes and re-applying this fork's web platform adaptations where both touch the same area.
4. Re-run the fork's existing build and browser validation pipeline to catch integration regressions immediately.
5. Only keep the merge if the branch is coherent, builds, and passes targeted verification.

## Execution Steps
1. Create a timestamped backup branch from the pre-merge `master`.
2. Merge `upstream/master` into `master` with a normal merge commit.
3. Inspect all conflicted files and classify them:
   - shared gameplay logic in `core/`
   - build wiring in `build.gradle.kts`, `settings.gradle.kts`, `buildSrc/`, and `web/build.gradle.kts`
   - web runtime, browser interop, and probe code in `web/`
   - tests and workflow files
4. For each conflict, prefer this rule:
   - upstream wins for new engine, gameplay, data, and bugfix behavior
   - fork wins for web-only platform support, deterministic validation hooks, and CI/browser tooling
   - if both are needed, manually compose both sides and keep code DRY
5. Regenerate or rebuild anything required by the web target after code resolution.
6. Run focused verification:
   - `./gradlew :tests:test`
   - `./gradlew :web:webBuildJs`
   - at least one browser validation path already used by this fork's CI, starting with `node scripts/web/run-js-browser-tests.js`
   - if environment permits, run `node scripts/web/run-web-validation.js` with the default local settings
7. Fix any regressions introduced by the merge.
8. Confirm final git status is clean except for the intended merge result and updated `plan.md`.

## Conflict Hotspots Expected
- `core/src/com/unciv/logic/**`
- `core/src/com/unciv/models/**`
- `core/src/com/unciv/ui/**`
- `build.gradle.kts`
- `buildSrc/src/main/kotlin/WebBuildTasks.kt`
- `web/build.gradle.kts`
- `tests/src/**`
- `.github/workflows/**`

## Success Criteria
- `master` contains `upstream/master` history
- fork-specific web code still compiles
- targeted automated tests pass
- no unresolved conflicts remain
- final branch state is reviewable and ready to push
