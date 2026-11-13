# TeaVM reflection-policy review

This is a read-only scope review of `UncivReflectionPolicy`. No policy edits were made as part of this review; the follow-up policy/fixture work is recorded below.

## Current scope

`UncivReflectionPolicy` calls `GdxReflectionHelper.applyDefaults`, then grants name lookup, all non-static/non-transient fields, and no-argument constructors to every `com.unciv.models.*` class and every `IsPartOfGameInfoSerialization` type. It also grants no-argument constructors (only) to all reachable `Collection`, `Map`, and `Json.Serializable` implementations.

The gdx-teavm helper's exact defaults are:

- `com.badlogic.gdx.scenes.scene2d.**`
- `net.mgsx.gltf.data.**`
- GDX `Array`, `ArrayMap`, `IntIntMap`, `IntMap`, `IntSet`, `LongMap`, `ObjectFloatMap`, `ObjectIntMap`, `ObjectMap`, `ObjectSet`, and `Queue`

For every matching type, `applyDefaults` grants both `foundByName()` and all members, including private methods and constructors. It does not preserve classes or scan the classpath. In TeaVM's reflection listener, `foundByName()` only admits a type after it reaches the reflection class-value node; the policy itself does not add every matching package type to the graph. It can still widen analysis when JSON or other reflective code has dynamic class values.

## Skin JSON surface

The packaged `android/assets/Skin.json` has these top-level type keys:

- `com.badlogic.gdx.graphics.Color`
- `com.badlogic.gdx.graphics.g2d.BitmapFont`
- `com.badlogic.gdx.scenes.scene2d.ui.Button$ButtonStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.CheckBox$CheckBoxStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.List$ListStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.ScrollPane$ScrollPaneStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.SelectBox$SelectBoxStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.Skin$TintedDrawable`
- `com.badlogic.gdx.scenes.scene2d.ui.Slider$SliderStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.SplitPane$SplitPaneStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.TextButton$TextButtonStyle`
- `com.badlogic.gdx.scenes.scene2d.ui.TextField$TextFieldStyle`
- `com.unciv.ui.screens.pickerscreens.PromotionScreenColors`

`BaseScreen.setSkin()` loads this fixed file. LibGDX `Skin.getJsonLoader()` resolves full class names through `ClassReflection.forName` when they are not simple class tags. It has custom serializers for `Skin`, `BitmapFont`, `Color`, and `Skin.TintedDrawable`. For the style objects, its JSON reader reflectively instantiates the style class and reads fields from each declared class in the inheritance chain.

The style chain that needs field metadata includes `ButtonStyle`, `TextButtonStyle`, and `CheckBoxStyle` (`CheckBoxStyle` extends `TextButtonStyle`, which extends `ButtonStyle`), plus `ProgressBarStyle` (the superclass of `SliderStyle`). The other listed style types are standalone. All style types used in this asset expose no-argument constructors. `PromotionScreenColors` is an ordinary Unciv object with instance fields and an implicit no-argument constructor; it is outside `com.unciv.models.*`. The follow-up policy now explicitly preserves it, and a dynamic class-name JSON fixture passes on both the JVM and generated JavaScript.

`Color` is already explicitly listed in `ADDITIONAL_JSON_TYPES`; its field access is also needed by ordinary Unciv JSON, apart from Skin's custom serializer. `BitmapFont` and `Skin.TintedDrawable` use Skin-specific serializers, so their internal fields and all methods do not need reflective access for this file. They still need name resolution for the fully qualified Skin type keys.

## Narrowing candidate

After optimizer behavior is understood and a playable baseline exists, replace the helper's package-wide grants with a small rule for the exact Skin type keys above:

1. Permit name lookup for the exact keys needed by `Skin.json`.
2. Permit instance fields and no-argument constructors only for the ten style classes, `ProgressBarStyle`, and `PromotionScreenColors`.
3. Keep `Color`'s existing explicit JSON rule. Do not expose all fields/methods for `BitmapFont` or `TintedDrawable` solely for Skin JSON; their Skin readers use custom serializers.
4. Reassess the helper's GDX collection entries separately. The current `Collection`/`Map` constructor rule already covers concrete collection implementations without granting their fields or name lookup. Do not retain their all-members allowance without a failing JSON/skin case.
5. The project sources contain no `net.mgsx.gltf`/GLTF references. Confirm the web runtime classpath has no such type before removing that pattern; if absent, it cannot explain this build's graph growth.

Keep the `com.unciv.models.*` and save-marker JSON rules during this first narrowing pass. The packaged rulesets are loaded from JSON into data-driven model types, and save data includes model types; reducing those requires a separate inventory of the actual deserialized model roots and gameplay/save regression tests. The JSON fixture's success under the current policy does not establish that the full-game reflection set can be trimmed safely.

## Memory interpretation

The 4 GiB attempt was intentionally stopped after heap saturation was confirmed; it was not an unexplained daemon failure. The 8 GiB attempt passed dependency analysis and then failed in optimization on `WorldScreen$nextTurn$1.invokeSuspend` with an array-index error. Those runs do not identify reflection as the sole memory cause. The all-members Scene2D policy is the clearest avoidable metadata surface to measure after the optimizer issue is fixed; the current `foundByName()` semantics also mean it should not be described as blindly preserving every class in those packages.
