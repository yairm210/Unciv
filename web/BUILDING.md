# Building the browser target

Web support is opt-in. No TeaVM or gdx-teavm source is downloaded or configured
for the normal desktop, server, or Android builds.

## Build

From the repository root, the first `-Pweb` invocation bootstraps the pinned
TeaVM and gdx-teavm source archives. Later invocations reuse the verified
archives and extracted source trees in the Gradle user cache.

```powershell
.\gradlew.bat -Pweb :web:compileKotlin
.\gradlew.bat -Pweb -PwebCompilerMemory=8192 :web:gdx_teavm_web_js_build
.\gradlew.bat -Pweb -PwebCompilerMemory=8192 :web:preparePages
.\gradlew.bat -Pweb -PwebCompilerMemory=8192 :web:gdx_teavm_web_js_run
```

The TeaVM build writes its working site to `web/build/dist/js/webapp`.
`preparePages` syncs a deployment-ready copy to `web/build/pages/game`, omitting
source maps, source directories, and servlet-only `WEB-INF` files. It scans the
public text assets (including `app.js` and `index.html`) for local Windows or
Unix home/repository paths and fails without printing a matched path. Source
maps are off by default; use `-PwebSourceMaps=true` for local debugging. Even
then, `preparePages` excludes map files from the deployment copy.

The full game compiler currently needs an 8 GiB TeaVM worker heap;
`webCompilerMemory` sets that heap in MiB.

## Browser multiplayer

Both players open **Multiplayer** from the main menu and share their player IDs.
The host selects **Create host offer** and sends it privately. The guest pastes
the offer, selects **Create join answer**, and sends the answer back. The host
pastes that answer and selects **Accept peer answer**. Both can then select
**Continue to multiplayer**.

The host starts an **Online multiplayer** game from **Start new game**, assigns
the two player IDs to human civilizations, then shares the game ID. The guest
uses **Add multiplayer game** to enter that ID. Keep the host tab open during
play. Saves are stored in the host browser, so the same host can reconnect
after a reload and resume the game.

This mode pairs two browsers. A connected peer can read or change hosted saves
by game ID. Exchange signaling privately with someone you trust. The default
ICE configuration uses public Google STUN. For networks that need a relay,
enter your TURN service as an array of WebRTC ICE server objects in both setup
dialogs. Use a browser with WebRTC and IndexedDB and serve the game over HTTPS
or localhost.

## CI and Pages deployment

`.github/workflows/web.yml` runs `:web:test`, `:web:preparePages`, a generated
JavaScript syntax check, and the browser font and WebRTC contracts on relevant
pull requests and when called by the documentation workflow. It uploads test
logs and the game package as run artifacts.

On `master`, the documentation workflow publishes the game to the
[`/Unciv/web/` preview](https://yairm210.github.io/Unciv/web/) on GitHub Pages.
If the browser build fails, the workflow can still publish the documentation
and keep the previously published browser build.

## Pinned toolchains and cache

`dependencies.lock.properties` pins each fork commit and the SHA-256 digest of
its codeload archive. When a release asset and its digest are available in the
lock, Gradle tries that asset first. It checks the asset against
the lock and checks its release `SHA256SUMS` entry against the same digest. If
the release asset is not available, Gradle downloads the archive for the exact
pinned commit and verifies its separately pinned digest. It never selects a
branch head or mutable snapshot.

Verified archives and extracted sources are stored under
`<Gradle user home>/caches/unciv-web-toolchains`. The cache entry is keyed by
fork name, full commit, and archive digest. A missing/corrupt cache entry is
downloaded and verified again. The system `tar` command is used to unpack the
source archives; Windows, macOS, and Linux build environments need `tar` on
`PATH`.

To advance a fork, update its full commit, release archive SHA-256, and codeload
SHA-256 together in the lock file. The release archive is expected at the
`unciv-<full-commit>` tag as `source.tar.gz`, with a standard `SHA256SUMS` file.

## Local source overrides

For fork development, either source may be replaced with an absolute checkout
path. An omitted override continues to use the pinned downloaded archive.

```powershell
.\gradlew.bat -Pweb "-PgdxTeaVMSource=$env:GDX_TEAVM_SOURCE" :web:compileKotlin
.\gradlew.bat -Pweb "-PteavmPath=$env:TEAVM_SOURCE" :web:compileKotlin
```

Overrides are checked for the pinned plugin/backend and TeaVM version pairing.
The normal build does not read these properties.

When testing a TeaVM source checkout with a newer project version, also pass
`-PteavmVersion=0.17.0`. This changes the local source validation only; downloaded
builds continue to use the lock file. The source composite includes TeaVM's
matching Gradle plugin, reflection SPI, and annotation processor so tooling and
compiler APIs agree.

If the TeaVM worker command exceeds Windows' command length limit, use
`-PwebCompilerInProcess=true` and give the Gradle daemon the compiler heap through
`org.gradle.jvmargs`. The `webCompilerMemory` setting applies to a separate
worker and does not set the heap for in-process compilation.
