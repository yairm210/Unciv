# `/Users/haimlamper/Unciv` Web E2E Phase-1 Implementation Plan (WASM-first, Docker-only)

## Summary
Ship a static-host-ready web build of Unciv using `gdx-teavm 1.4.0` with these locked decisions:
1. Runtime is WASM-first.
2. Scope is playable core + local saves.
3. Hosting target is static bundle for CDN/static host.
4. No Three.js.
5. No libKTX in phase 1.
6. Multiplayer, custom-location file picker, and online mod update/download are disabled on web in phase 1 via explicit capability gating.

## Step 0: Required persistence and tracking files (first execution commit)
Create these files immediately when execution mode starts:
1. `/Users/haimlamper/Unciv/plan.md` with this full plan.
2. `/Users/haimlamper/Unciv/progress.md` as append-only log.
3. `/Users/haimlamper/Unciv/features.csv` with phase tracking rows.
4. `/Users/haimlamper/Unciv/tmp/` as local temporary workspace.

Apply these process rules:
1. Read `/Users/haimlamper/Unciv/progress.md` before every new exploration/read pass.
2. Append one progress line after every meaningful step using format `timestamp | area | action | result | next`.
3. Never use ad-hoc temp paths outside `/Users/haimlamper/Unciv/tmp/` for this effort.

## Step 1: Container-first development environment
Add:
1. `/Users/haimlamper/Unciv/.devcontainer/devcontainer.json`
2. `/Users/haimlamper/Unciv/.devcontainer/Dockerfile`
3. `/Users/haimlamper/Unciv/docker-compose.web.yml`
4. `/Users/haimlamper/Unciv/scripts/web/in-container.sh`

Container image contents:
1. JDK 21.
2. `git`, `gh`, `jq`.
3. Node LTS and Playwright dependencies.
4. Gradle prerequisites and cached directories.

Required runnable commands inside container:
1. `./gradlew :web:webBuildWasm`
2. `./gradlew :web:webBuildJs`
3. `./gradlew :web:webServeDist`

## Step 2: Add web module and TeaVM build pipeline
Update:
1. `/Users/haimlamper/Unciv/settings.gradle.kts` to include `web`.

Create:
1. `/Users/haimlamper/Unciv/web/build.gradle.kts`
2. `/Users/haimlamper/Unciv/web/src/main/java/com/unciv/app/web/BuildWebWasm.java`
3. `/Users/haimlamper/Unciv/web/src/main/java/com/unciv/app/web/BuildWebJs.java`
4. `/Users/haimlamper/Unciv/web/src/main/java/com/unciv/app/web/WebLauncher.java`

Web dependencies:
1. `project(":core")`
2. `com.badlogicgames.gdx:gdx:1.14.0`
3. `com.github.xpenatan.gdx-teavm:backend-web:1.4.0`
4. `com.github.xpenatan.gdx-teavm:gdx-freetype-teavm:1.4.0` (fallback path only)

TeaVM build config:
1. WASM task uses `setWebAssembly(true)`.
2. JS task uses `setWebAssembly(false)`.
3. Assets source is `/Users/haimlamper/Unciv/android/assets`.
4. Output folder is `/Users/haimlamper/Unciv/web/build/dist`.
5. Auto-jetty disabled in CI.
6. Add reflection patterns for Unciv packages needed by runtime serialization paths.

## Step 3: Web runtime wiring
Create:
1. `/Users/haimlamper/Unciv/web/src/main/kotlin/com/unciv/app/web/WebDisplay.kt`
2. `/Users/haimlamper/Unciv/web/src/main/kotlin/com/unciv/app/web/WebFont.kt`
3. `/Users/haimlamper/Unciv/web/src/main/kotlin/com/unciv/app/web/WebLogBackend.kt`
4. `/Users/haimlamper/Unciv/web/src/main/kotlin/com/unciv/app/web/WebGame.kt` (if needed to centralize web overrides)

Startup wiring in `WebLauncher`:
1. `Display.platform = WebDisplay()`
2. `Fonts.fontImplementation = WebFont()`
3. `UncivFiles.saverLoader = PlatformSaverLoader.None`
4. `UncivFiles.preferExternalStorage = false`
5. Configure responsive canvas in `TeaApplicationConfiguration`.

## Step 4: Core portability fixes required for TeaVM compile
Edit:
1. `/Users/haimlamper/Unciv/core/src/com/unciv/models/metadata/GameSettings.kt` to remove `java.awt.Rectangle` dependency.
2. `/Users/haimlamper/Unciv/core/src/com/unciv/ui/screens/worldscreen/unit/UnitTable.kt` to replace `java.awt.Label.CENTER` with LibGDX alignment.
3. `/Users/haimlamper/Unciv/core/src/com/unciv/ui/popups/options/AdvancedTab.kt` to remove `java.nio.file` and `Thread.sleep` usage from shared path.
4. `/Users/haimlamper/Unciv/core/src/com/unciv/ui/screens/savescreens/LoadOrSaveScreen.kt` to remove `DosFileAttributes` dependency.
5. `/Users/haimlamper/Unciv/core/src/com/unciv/ui/audio/MusicController.kt` to replace thread sleep with coroutine delay path.
6. `/Users/haimlamper/Unciv/core/src/com/unciv/logic/files/IMediaFinder.kt` to remove reflective sound enumeration in web-reachable path by explicit static registry.

## Step 5: Capability contract and phase-1 feature gating
Create:
1. `/Users/haimlamper/Unciv/core/src/com/unciv/platform/PlatformCapabilities.kt`

Fields for phase 1:
1. `onlineMultiplayer = false`
2. `customFileChooser = false`
3. `onlineModDownloads = false`
4. `systemFontEnumeration = false`

Wire capability usage in:
1. Main menu and options tabs for multiplayer visibility.
2. Save/load screens for custom-location actions.
3. Mod manager online update/download actions.
4. Font options to hide system font enumeration on web.

## Step 6: Startup/network behavior gating for web phase 1
Adjust:
1. `/Users/haimlamper/Unciv/core/src/com/unciv/UncivGame.kt` to skip startup multiplayer server check on web.
2. Multiplayer/chat entry points to remain dormant when capability is disabled.
3. Keep all single-player logic unchanged.

## Step 7: Font strategy for deterministic web behavior
`WebFont` implementation:
1. Implement `FontImplementation` using browser-canvas glyph rasterization into `Pixmap` for `NativeBitmapFontData`.
2. Keep one deterministic default family in phase 1.
3. Hide system font selector on web.
4. Keep `gdx-freetype-teavm` as fallback path only if canvas glyph rendering proves unstable.

## Step 8: CI build and deploy artifacts
Add:
1. `/Users/haimlamper/Unciv/.github/workflows/web-build.yml`

Workflow stages:
1. Build in containerized Linux runner.
2. Run `./gradlew :web:webBuildWasm`.
3. Upload `/Users/haimlamper/Unciv/web/build/dist` artifact.
4. Optional manual deploy job for static host preview.

## Step 9: `features.csv` initialization and lifecycle
Create `/Users/haimlamper/Unciv/features.csv` with schema:
1. `feature,web_status,phase,blocking_issue,notes,last_verified`

Initial rows:
1. `Boot/Main menu`
2. `Start new game`
3. `End turn loop`
4. `Local save/load`
5. `Clipboard import/export`
6. `Audio`
7. `Multiplayer`
8. `Mod download/update`
9. `Custom file picker save/load`
10. `Translation/font selection`
11. `External links`

Status semantics:
1. `PASS`
2. `FAIL`
3. `BLOCKED`
4. `DISABLED_BY_DESIGN`

## Step 10: Validation matrix
Build validation:
1. Clean container build of WASM target succeeds.
2. JS debug target succeeds.

Runtime smoke:
1. App boots from static server without fatal console errors.
2. Main menu is interactive.
3. Start game and play 10 turns.
4. Save locally, reload page, load save.
5. Clipboard export/import roundtrip.

Gating validation:
1. Multiplayer UI hidden/disabled.
2. Custom-location file save/load hidden/disabled.
3. Online mod download/update hidden/disabled.

CI acceptance:
1. Artifact is static-host compatible and boots in browser smoke test.

## Public API/interface/type changes
1. Add `PlatformCapabilities` in core and consume it in UI/init flows.
2. Add web module entrypoints (`BuildWebWasm`, `BuildWebJs`, `WebLauncher`).
3. Replace AWT-dependent `WindowState` constructor path with platform-neutral variant.
4. Add explicit sound/media registry path replacing reflection-dependent discovery in web-reachable code.

## Assumptions and defaults
1. No local Java is available, so all build/test must run inside Docker/devcontainer.
2. Phase 1 intentionally excludes multiplayer and online mod downloading on web.
3. Three.js and libKTX are intentionally excluded in phase 1.
4. `progress.md` and `features.csv` are mandatory and updated continuously from first implementation commit onward.
