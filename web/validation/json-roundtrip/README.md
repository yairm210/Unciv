# JSON compatibility contract

This standalone fixture exercises Unciv's real `json()` factory and production
data classes. It checks settings and save-game round trips (including private,
nested, Unicode and collection fields), inherited ruleset fields, and skin
colors/variants. Rulesets and skins are tested as inputs, matching game use.

Run from the Unciv root with Gradle 9.6.1, JDK 21, and Node.js.

```sh
gradle -Pweb :web:writeValidationClasspath
gradle -p web/validation/json-roundtrip '-Dorg.gradle.jvmargs=-Xmx2g' jvmContract runJavaScriptContract
```

Both builds resolve TeaVM and gdx-teavm through the web target's dependency
lock. If using source overrides, pass the same overrides to both commands.

`jvmContract` is the JDK reference. `runJavaScriptContract` compiles the same
checks through TeaVM and runs them in Node with real Web Crypto. The fixture
loads the production web reflection policy through its service descriptor.
Neither contract establishes browser rendering or playable-game behavior.
