# JSON compatibility contract

This standalone fixture exercises Unciv's real `json()` factory and production
data classes. It checks settings and save-game round trips (including private,
nested, Unicode and collection fields), inherited ruleset fields, and skin
colors/variants. Rulesets and skins are tested as inputs, matching game use.

Export the web module's resolved runtime classpath from the Unciv root first:

```sh
./gradlew -Pweb -PgdxTeaVMSource=/path/to/gdx-teavm :web:writeValidationClasspath
```

Run this directory as a separate Gradle build, using Gradle 9.4.1 and JDK 21:

```sh
gradle jvmContract runJavaScriptContract \
  -PclasslibJar=/path/to/copied/classlib.jar \
  -PcoreJar=/path/to/copied/core.jar \
  -PjsoJar=/path/to/copied/jso.jar
```

Use a matched set of source-built TeaVM 0.16 fork artifacts. Finish their build
before copying them: concurrent copies of jars being written can be truncated.
The source composite selects relocated jars; do not assume a separate original
jar was refreshed. Store fixture copies under ignored `build/inputs` and record
their SHA-256 hashes with the validation result.

`jvmContract` is the JDK reference. `runJavaScriptContract` compiles the same
checks through TeaVM and runs them in Node with real Web Crypto. The fixture
loads the production web reflection policy through its service descriptor.
Neither contract establishes browser rendering or playable-game behavior.
