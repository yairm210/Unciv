# Browser reflection and save contract

This fixture reproduces reflective access used by `TextureData.getReadonlyPixmap()`
and new-game resource options. It checks the pixmap field read/write path, enum
constant lookup, deprecated constant annotations, and `MapResourceSetting`'s
filtered labels using the production `UncivReflectionPolicy` service.

It also loads `browser-game-save.json`, a 44 KB save exported from an actual
headless browser run (Tiny map, Civ V - Vanilla, turn 1, England, game ID
`e914aa79-84c1-4900-88ea-dde6432882bd`). The fixture reads the full `GameInfo`,
then inserts nonzero legacy `Vector2` bounds into a variant of that payload and
verifies JSON write/read-back preserves them. The captured original predates the
coordinate metadata fix and contains empty bounds.

To reproduce the save-picker's `LoadOrSaveScreen.showSaveInfo` path, the fixture
also reads the same full save as `GameInfoPreview`, calls `migrateCivID`, filters
human player civilizations and their labels, reads game parameters, and formats
a `Date`. Before the targeted metadata rule, generated JavaScript failed with
`Class cannot be created (missing no-arg constructor): com.unciv.logic.GameInfoPreview`.
The production policy now lists both `GameInfoPreview` and its nested
`CivilizationInfoPreview` explicitly rather than widening the general policy.
A build task embeds the tracked JSON as a generated Java string, so the test does
not depend on browser resource loading.

Export the web module's resolved runtime classpath first:

```sh
./gradlew -Pweb -PgdxTeaVMSource=/path/to/gdx-teavm :web:writeValidationClasspath
```

Run the fixture as a standalone Gradle build with a matched immutable TeaVM fork set:

```sh
gradle jvmContract runJavaScriptContract \
  -PclasslibJar=/path/to/classlib.jar \
  -PcoreJar=/path/to/core.jar \
  -PjsoJar=/path/to/jso.jar
```

The JVM contract checks the JDK reference. The JavaScript contract compiles with
TeaVM and runs in Node using the exported web runtime classpath and reflection
policy service. It validates reflection and save serialization; it does not
validate graphics rendering or game startup.
