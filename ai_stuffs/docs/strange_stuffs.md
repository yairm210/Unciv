# Strange Stuffs

This file records the non-obvious compatibility choices made while syncing this fork with upstream `unciv`.

## 1. Source-generated browser test suite

Strange thing:
- The web target does not use normal JUnit discovery and runners.

Why this was chosen:
- TeaVM/browser builds need a deterministic, explicit list of tests.
- Generating `WebJsTestSuite.kt` from `tests/src` keeps the browser suite aligned with upstream tests without hand-maintaining a second list.

## 2. Parameterized tests are expanded into concrete browser cases

Strange thing:
- The generator converts `@RunWith(Parameterized::class)` tests into concrete `WebJsGeneratedTestClass` entries instead of skipping them.

Why this was chosen:
- Upstream added parameterized tests for pathfinding-related behavior.
- Skipping them would leave the browser target behind upstream behavior coverage.
- Expanding them at generation time keeps the browser harness simple while still running every parameter set.

## 3. Browser-side `Assume` throws and the runner counts it as ignored

Strange thing:
- The web-only JUnit shim defines `AssumptionViolatedException`, `Assume.assumeTrue`, and `Assume.assumeThat`, and the browser runner treats those as ignored tests.

Why this was chosen:
- Upstream tests use assumptions to mark algorithm-specific cases.
- A no-op `Assume` is incorrect because it runs tests that were supposed to be skipped.
- Counting assumptions as ignored preserves the intended upstream test semantics.

## 4. `LongPriorityQueue.stream()` uses `LongStream.of(...)` instead of `StreamSupport.longStream(...)`

Strange thing:
- The core implementation avoids `StreamSupport.longStream(spliterator(), false)` and builds the stream from a copied `LongArray`.

Why this was chosen:
- TeaVM does not provide `StreamSupport.longStream(...)` for the web build.
- Upstream added `LongPriorityQueueTest`, and the browser target must compile and execute it.
- `LongStream.of(*toArray(...))` preserves the public API and passes both JVM and browser validation.

## 5. `PathingMapTest` normalizes browser float formatting

Strange thing:
- Browser-side debug-string assertions normalize values like `.3` to `0.3`.

Why this was chosen:
- The JS runtime and the JVM format some fractional values differently even when the numeric value is identical.
- The test is verifying pathing state, not renderer-specific string formatting trivia.

## 6. Browser multiplayer shim follows upstream server API shape

Strange thing:
- The web multiplayer shim carries small fork-specific glue for upstream method signatures and notification behavior.

Why this was chosen:
- Upstream changed multiplayer call signatures during the sync.
- The fork still needs browser-specific integration points while matching upstream game logic.

## 7. JVM test runner keeps custom GDX behavior and upstream parameterized support

Strange thing:
- `GdxTestRunner` now handles both classic tests and upstream parameterized tests without dropping the fork's GL/headless behavior.

Why this was chosen:
- The fork needs GDX-aware execution for tests.
- Upstream now relies on parameterized JUnit flows, so the runner had to support both instead of choosing one.

## 8. Machine-local artifacts stay out of git

Strange thing:
- Browser validation writes temporary output under `tmp/`, not tracked source paths.

Why this was chosen:
- Build/test artifacts can contain machine-local paths, timestamps, and screenshots.
- Keeping them in ignored temp space avoids pushing machine-identifying noise or accidental PII.
