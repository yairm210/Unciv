# Upstream Alignment Contract

This fork already contains `upstream/master`. Future alignment work is therefore about reducing drift safely, not replaying a catch-up merge.

## Protected Web Bucket

Keep these areas out of routine drift-reduction:

- `web/`
- `scripts/web/`
- `.github/actions/web-test-env/action.yml`
- `.github/workflows/web-build.yml`
- `.github/workflows/web-war-deep-nightly.yml`
- `.devcontainer/`
- `buildSrc/src/main/kotlin/WebBuildTasks.kt`
- `docker-compose.web.yml`

These files are the browser runtime, TeaVM shims, browser probes, build wiring, and CI/test infrastructure that preserve web correctness.

## Shared-File Decision Rule

For every changed file outside the protected web bucket and outside explicit user-meta files:

- `keep`: the shared diff is still required for web correctness or shared test support
- `compose`: upstream behavior must remain the default, but a minimal web adaptation still belongs on top
- `revert`: the shared diff should be dropped in favor of upstream behavior

`PlatformCapabilities` remains the only allowed feature gate for web staging and rollback behavior. `BuildWebCommon` remains the single source of truth for TeaVM reflection preservation.

## Mandatory Audit Check

Run this before landing drift-reduction work:

```bash
node scripts/web/check-upstream-audit.js
```

The check compares `HEAD` to `upstream/master` and fails if a non-protected shared diff is missing from `ai_stuffs/upstream_diff_audit.csv`, or lacks an explicit `keep` / `compose` / `revert` decision.

## First Shared Slice

The current browser file-I/O slice keeps only minimal shared changes:

- stable actor names for clickOps/browser smoke targeting
- portable serialization for custom-location save/export flow
- no gameplay or desktop-only logic changes
