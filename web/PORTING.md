# Browser port revival

## Current status

The browser build runs vanilla single-player in headless Chrome with SwiftShader
and has been deployed through GitHub Pages. Local extended gameplay reached turn
6 with research and production completing; overwriting the named save, reloading
the page, and loading it again preserved turn 6. Earlier save exports also
confirmed nonzero explored-region coordinates.

The save-list preview required two additional reflection registrations. The
generated-JavaScript regression and final browser preview/load check both pass.
The save timestamp persistence fix is included in the deployed backend; its
write/reload contract passed. Save-date sorting has not been stress-tested.

The public pinned `:web:preparePages` build passed on 2026-11-08 in 5m 41s with
TeaVM `16bc89b1e31e3c3e09ab6bfe4f26c430be910b13`, gdx-teavm
`d2df1013d297a848734e9928c6bfd508ab053657`, and Unciv runtime commit
`e80f7330a2175141bfe84387b95ceb5ba0b9912e`. Its generated `app.js` SHA-256 is
`69BC78688843FCE1D70A4878AF2AE4A2C79D1D377794DF2ADDF4ADDE51865EFE`, matching
the artifact before the dependency-owner migration. The earlier Yosef CI and
Pages run `42190407113` deployed runtime `873ae6a2a`; it remains the live-site
evidence until this newer build is deployed. Deployed source-map URLs returned
404 as intended. A live Chrome check also passed English selection, Quickstart,
named save, page reload, and save preview with a current 2026 timestamp.
Exact build details and prior checkpoints are in
[COMPATIBILITY.md](COMPATIBILITY.md).

Baseline: Unciv `608d9de26b16f834d5fed19df4fd1bd65bfcbf3e`; old
[PR #14709](https://github.com/yairm210/Unciv/pull/14709) is the reference.
The shared ancestor has 1,070 intervening upstream commits. Core changes are
14 files, 88 added lines and 27 removed lines; most implementation is in the
opt-in web module and the two dependency forks.

## Scope and constraints

The first gameplay target is vanilla single-player in a browser. Multiplayer,
mods, sound, and WebAssembly remain separate follow-up stages. Named save writing and loading after a page reload are demonstrated.

- Keep web support opt-in; normal builds must not acquire local source overrides.
- Use current Unciv APIs and preserve ordinary initialization and disposal.
- Fix reusable JVM/API gaps in TeaVM and browser behavior in gdx-teavm. Keep
  Unciv-specific changes narrow and tied to demonstrated platform needs.
- Do not use mutable snapshots, counterfeit test classes in production, fake
  synchronous coroutine dispatchers, blanket retries, or swallowed startup errors.
- Add reflection metadata from actual JSON and runtime use, not a broad package
  preservation list.

### Local files and Java NIO

`WebFileHandle.file()` is deliberately unsupported for browser handles in the
pinned gdx-teavm backend (`d2df1013d297a848734e9928c6bfd508ab053657`;
[WebFileHandle.java](https://github.com/YosefLm/gdx-teavm/blob/d2df1013d297a848734e9928c6bfd508ab053657/backends/backend-web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/WebFileHandle.java)).
Its `Local` files live in the backend's LocalDB/IndexedDB storage ([WebFiles.java](https://github.com/YosefLm/gdx-teavm/blob/d2df1013d297a848734e9928c6bfd508ab053657/backends/backend-web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/WebFiles.java),
[LocalDBStorage.java](https://github.com/YosefLm/gdx-teavm/blob/d2df1013d297a848734e9928c6bfd508ab053657/backends/backend-web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/filesystem/types/LocalDBStorage.java));
reads and directory operations go through `WebFileHandle` rather than a host
filesystem. TeaVM's current pinned `java.io.File` and NIO implementation
(`16bc89b1e31e3c3e09ab6bfe4f26c430be910b13`, version 0.17.0; [TFile.java](https://github.com/YosefLm/teavm/blob/16bc89b1e31e3c3e09ab6bfe4f26c430be910b13/classlib/src/main/java/org/teavm/classlib/java/io/TFile.java),
[TDefaultFileSystemProvider.java](https://github.com/YosefLm/teavm/blob/16bc89b1e31e3c3e09ab6bfe4f26c430be910b13/classlib/src/main/java/org/teavm/classlib/java/nio/file/impl/TDefaultFileSystemProvider.java),
[VirtualFileSystemProvider.java](https://github.com/YosefLm/teavm/blob/16bc89b1e31e3c3e09ab6bfe4f26c430be910b13/core/src/main/java/org/teavm/runtime/fs/VirtualFileSystemProvider.java))
uses `VirtualFileSystemProvider`, which defaults to an independent in-memory
filesystem. It is not wired to gdx-teavm LocalDB, so returning a path from
`WebFileHandle.file()` alone would make NIO inspect the wrong store.

For mod fonts ([ModFonts.kt](../core/src/com/unciv/ui/components/fonts/ModFonts.kt)), keep the
existing NIO scanner on desktop and supported Android, and use the equivalent
`FileHandle.list/isDirectory/exists` operations in the WebFont platform override.
The shared font interface defaults to the NIO scanner, while the WebGL backend
handles its virtual files directly. A reusable
`java.io.File`/NIO view of LocalDB could be built in gdx-teavm,
but it would require a VirtualFileSystem adapter with explicit path, storage,
and persistence semantics; that broader filesystem integration is separate
from the font-scanning fix.

## Build and run

The opt-in module uses libGDX 1.14.2, Kotlin 2.4.20 and TeaVM 0.17.0.
The public forks are [YosefLm/teavm](https://github.com/YosefLm/teavm) and
[YosefLm/gdx-teavm](https://github.com/YosefLm/gdx-teavm). Exact source commits and
archive SHA-256 values are in `web/dependencies.lock.properties`.

```sh
./gradlew -Pweb -PwebCompilerMemory=8192 :web:preparePages
./gradlew -Pweb -PwebCompilerMemory=8192 :web:gdx_teavm_web_js_run
```

No private checkout or local Maven artifact is required. The settings bootstrap
verifies a pinned release archive, with a separately checksummed public source
archive fallback. It caches verified sources in Gradle's user cache. See
[BUILDING.md](BUILDING.md) for optional local source overrides.

The clean public output is `web/build/pages/game`. Debug source maps and copied
source trees are excluded, and packaging rejects workstation paths. Normal builds
omit `-Pweb` and do not download the browser toolchain.

## Validation boundaries

- The current full compiler build and browser sequence above pass.
- Local browser testing reached turn 6 and preserved an overwritten save across
  reload. It does not establish long-game stability or support across browsers.
- JVM and generated-JavaScript JSON contracts, TeaVM core tests, Fetch contracts,
  executor/input suspension, reflection, and browser runtime regressions are
  recorded in [COMPATIBILITY.md](COMPATIBILITY.md).
- Successful compilation alone does not establish gameplay. Conversely, the
  current browser run proves the listed path only; it does not claim multiplayer,
  arbitrary mods, or broad compatibility.

## Review and follow-up sequence

The implementation is split locally by owning repository. Review reusable TeaVM
changes first (class-library contracts and optimizer regressions), then the three
gdx-teavm fixes (Pixmap buffer semantics, render callback suspension, queued input).
Review Unciv's save-path/platform seams separately from the opt-in web module,
launcher, Fetch engine and reflection registrations. Public fork branches preserve those boundaries; no upstream pull request has
been opened.

The public branches are `codex/web-revival` (Unciv), `codex/unciv-teavm-0.16`
(TeaVM) and `codex/pixmap-teavm-0.16` (gdx-teavm). The optional shared agent
workflow lives under `$CODEX_HOME/skills/unciv-teavm-port/SKILL.md`.

WebRTC work is deferred to the separate `codex/webrtc-peer` branch/worktree for
a later PR. It is an unintegrated draft, not part of the main browser build.

Before broad release, validate longer games, browser lifecycle/backgrounding,
more browsers and hardware, and bundle size/performance. Multiplayer, mods,
file sharing, audio and WebAssembly need separate implementation/validation.
The current milestone is the observed minimal vanilla gameplay path.

## Historical work

The old PR #14709 implementation is a reference, not the base of this port. The
revival starts from current Unciv upstream and preserves the useful split between
game changes, browser backend changes, and reusable TeaVM fixes. Earlier 0.15
diagnostics, intermediate 0.16 compiler failures, and resolved startup/runtime
errors are retained as historical evidence in [COMPATIBILITY.md](COMPATIBILITY.md).
