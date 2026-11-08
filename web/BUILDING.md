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

## CI and optional Pages deployment

`.github/workflows/web.yml` runs `:web:test`, `:web:preparePages`, a generated
JavaScript syntax check, and the browser font contracts on pull requests and
pushes that change the web target or shared code/assets. It uploads test logs
and the clean game package as run artifacts. Pull requests never deploy.

To deploy from a trusted branch, set repository variables
`WEB_DEPLOY_PAGES=true` and `WEB_DEPLOY_BRANCH` to that branch name, and enable
GitHub Pages with GitHub Actions as the source. The deployment job checks out
the existing `gh-pages` branch, replaces only its `game/` directory, and keeps
the rest of that site. A repository using deployment must therefore have a
`gh-pages` branch; repositories without the variables only build and test.

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
