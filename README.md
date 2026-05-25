# Reciv

Reciv is a DeCiv-focused fork of [Unciv](https://github.com/yairm210/Unciv). The goal is to turn a curated set of DeCiv community projects into one built-in, default 4X experience: post-collapse factions, rulesets, resources, units, buildings, tilesets, translations, and balance changes integrated into the base game rather than treated as optional downloads.

This project is early in that transition. The current repository includes the source-tracking and update automation for the DeCiv mod stack; the generated `Reciv - Vanilla` base ruleset and bundled assets are the next implementation step.

> Screenshot placeholder: integrated Reciv world map.

## Source Projects

Reciv stands on two layers of open source work:

- [Unciv](https://github.com/yairm210/Unciv), the original open source Civ V-inspired Android and desktop game engine.
- [DeCiv 2](https://github.com/SpacedOutChicken/Deciv-2), the primary DeCiv ruleset foundation for this fork.
- [Forgotten Factions for DeCiv](https://github.com/carriontrooper/Forgotten-Factions-for-Deciv), restored and reimagined DeCiv faction content.
- [Outlaws of the Wastes](https://github.com/the-s-is-silent/Outlaws-of-the-Wastes), a major DeCiv expansion and rebalance project.
- [Steampunk Nations for DeCiv](https://github.com/carriontrooper/Steampunk-Nations-for-Deciv), additional steampunk-inspired DeCiv nations.

The mod repositories are tracked in [mods-manifest.json](mods-manifest.json). Each source is pinned and updated through review PRs so changes can be audited instead of copied manually.

## Project Status

- Repo-backed mod source tracking is in place.
- The `Mod updates` GitHub Action checks source repositories and opens separate PRs per mod update.
- Attribution notes live in [docs/mod-attribution](docs/mod-attribution).
- Actual bundling into a generated `Reciv - Vanilla` base ruleset is planned but not complete yet.

Mod source metadata keeps README and credits information close to each imported source, but it does not block imports.

## Screenshots

Screenshots will be added as the integrated build comes together.

> Screenshot placeholder: world map.

> Screenshot placeholder: faction selection.

> Screenshot placeholder: Civilopedia.

## Building A Debug APK

Requirements:

- JDK 21
- Android SDK with `ANDROID_HOME` set, or a `local.properties` file containing `sdk.dir=...`
- A shell that can run the Gradle wrapper

Build the debug APK:

```powershell
.\gradlew.bat :android:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :android:assembleDebug
```

The debug APK is written under:

```text
android/build/outputs/apk/debug/
```

If the `android` Gradle project is not included, check that the Android SDK is discoverable. This project only includes the Android module when `ANDROID_HOME` or `local.properties` points to a valid SDK.

## Building On Windows

For a desktop JAR on Windows:

```powershell
.\gradlew.bat desktop:dist
```

The JAR is written to:

```text
desktop/build/libs/Unciv.jar
```

Run it with:

```powershell
java -jar desktop\build\libs\Unciv.jar
```

For a portable Windows package with a bundled runtime, first build the JAR, then provide Packr and a Windows JRE archive in the repository root:

```powershell
.\gradlew.bat desktop:dist
```

Required files for the Packr task:

```text
packr-all-4.0.0.jar
jdk-windows-64.zip
```

Then run:

```powershell
.\gradlew.bat desktop:packrWindows64
```

The portable ZIP is written to:

```text
deploy/Unciv-Windows64.zip
```

The GitHub release workflow downloads Packr and the Windows JRE automatically, so the manual Packr setup is only needed for local Windows packaging.

## GitHub APK Builds

The `Android debug APK` workflow:

- Run on push, pull request, and manual dispatch.
- Set up JDK 21, Android SDK, and Gradle caching.
- Run `./gradlew :android:assembleDebug`.
- Upload the APK as a GitHub Actions artifact.

Release signing is intentionally separate. Do not add signing secrets until a release workflow is needed.

## Versioned Releases

Reciv releases use SemVer tags such as `v0.1.0`, `v0.2.0`, or `v1.0.0-beta.1`. The tag version must match both `BuildConfig.appVersion` and `UncivGame.VERSION`.

Prepare a release locally from PowerShell:

```powershell
.\tools\releases\prepare_reciv_release.ps1 -Version 0.1.0
```

That script updates:

- `buildSrc/src/main/kotlin/BuildConfig.kt`
- `core/src/com/unciv/UncivGame.kt`
- `RECIV_CHANGELOG.md`

Before tagging, edit the generated section in [RECIV_CHANGELOG.md](RECIV_CHANGELOG.md) and replace the TODO with real release notes.

Create and push the tag:

```powershell
git add buildSrc\src\main\kotlin\BuildConfig.kt core\src\com\unciv\UncivGame.kt RECIV_CHANGELOG.md
git commit -m "Prepare Reciv 0.1.0 release"
git tag v0.1.0
git push origin HEAD --tags
```

The `Reciv release builds` workflow then:

- Validates the tag/version/changelog.
- Builds Android debug and unsigned release APKs.
- Builds a Windows x64 portable ZIP.
- Publishes a GitHub Release with artifacts and changelog notes.

You can also run the workflow manually from GitHub Actions by entering the version, but the checked-in version files must already match.

## Mod Source Updates

The mod update pipeline is documented in [docs/Mod-source-updates.md](docs/Mod-source-updates.md).

Locally, once Python and Git are available:

```powershell
python tools\mods\mod_repo_manager.py validate-manifest
python tools\mods\mod_repo_manager.py check-updates
python tools\mods\mod_repo_manager.py update-one --mod deciv-2 --dry-run
```

The scheduled GitHub workflow checks each repo-backed mod and opens a separate pull request when a source ref advances.

## Development Notes

Reciv keeps upstream Unciv visible and updateable. The intended update flow is:

- Unciv upstream updates land through a dedicated upstream sync PR.
- DeCiv mod source updates land through separate mod update PRs.
- Generated base ruleset changes are reviewed separately from source-pinning changes where practical.

This keeps upstream engine conflicts, mod content conflicts, and generated asset changes easier to reason about.

## Credits And Attribution

Unciv is licensed under the MPL-2.0 license; see [LICENSE](LICENSE). Reciv inherits Unciv engine code and must preserve upstream notices.

DeCiv mod content may include third-party art, audio, and community contributions. Attribution notes are tracked in [docs/mod-attribution](docs/mod-attribution) and updated from the source repositories where practical.

## Upstream Credits

For Unciv engine and original project credits, see [docs/Credits.md](docs/Credits.md) and the upstream [Unciv repository](https://github.com/yairm210/Unciv).
