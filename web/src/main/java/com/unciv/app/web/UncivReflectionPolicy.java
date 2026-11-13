package com.unciv.app.web;

import com.github.xpenatan.gdx.teavm.backends.shared.config.reflection.GdxReflectionHelper;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.teavm.extension.introspect.IntrospectField;
import org.teavm.extension.introspect.IntrospectMethod;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/** Compiler-side metadata for libGDX JSON and Scene2D; this does not change game serialization. */
public final class UncivReflectionPolicy extends SimpleReflectionPolicy {
    private static final Set<String> ADDITIONAL_JSON_TYPES = Set.of(
            // Settings and ruleset fields whose data classes live outside the models package.
            "com.unciv.ui.components.fonts.FontFamilyData",
            "com.unciv.logic.multiplayer.FriendList$Friend",
            "com.unciv.ui.screens.civilopediascreen.FormattedLine",
            // A fully qualified type key in the packaged Skin.json.
            "com.unciv.ui.screens.pickerscreens.PromotionScreenColors",
            // Skin colors are ordinary JSON objects with r/g/b/a fields.
            "com.badlogic.gdx.graphics.Color",
            // Legacy viewport bounds persisted in Civilization.exploredRegion.
            "com.badlogic.gdx.math.Vector2",
            // Typed version parser used when loading a save fails.
            "com.unciv.logic.GameInfoSerializationVersion",
            // Save picker reads this compact view from the full save JSON.
            "com.unciv.logic.GameInfoPreview",
            "com.unciv.logic.civilization.CivilizationInfoPreview"
    );
    private static final Set<String> REFLECTIVE_ENUMS = Set.of(
            "com.unciv.logic.map.mapgenerator.MapResourceSetting",
            "com.unciv.models.ruleset.unique.Countables",
            "com.unciv.models.ruleset.unique.DeprecatedUniqueType",
            "com.unciv.models.ruleset.unique.UniqueType"
    );

    @Override
    protected void setup() {
        GdxReflectionHelper.applyDefaults(this::selectClasses);
        var savedGameData = extending("com.unciv.logic.IsPartOfGameInfoSerialization");
        selectClasses(type -> savedGameData.test(type)
                || type.name().startsWith("com.unciv.models.")
                || ADDITIONAL_JSON_TYPES.contains(type.name()))
                .foundByName()
                .reflectableFields(field -> !Modifier.isStatic(field.modifiers())
                        && !Modifier.isTransient(field.modifiers()))
                .reflectableMethods(UncivReflectionPolicy::isDefaultConstructor);

        // TextureData.getReadonlyPixmap() reads this libGDX implementation detail on the
        // new-game screen; expose only the field it asks for via Class.getDeclaredField().
        selectClasses(type -> "com.badlogic.gdx.graphics.glutils.FileTextureData".equals(type.name()))
                .reflectableFields(field -> "pixmap".equals(field.name()));

        // These enum helpers call Class.getField(name) to inspect constant annotations.
        selectClasses(type -> REFLECTIVE_ENUMS.contains(type.name()))
                .reflectableFields(IntrospectField::isEnumConstant);

        // JSON collection construction and Json.Serializable implementations need constructors,
        // but their internal fields and arbitrary methods do not need reflective access.
        var collection = extending("java.util.Collection");
        var map = extending("java.util.Map");
        var customJson = extending("com.badlogic.gdx.utils.Json$Serializable");
        selectClasses(type -> collection.test(type) || map.test(type) || customJson.test(type))
                .reflectableMethods(UncivReflectionPolicy::isDefaultConstructor);
    }

    private static boolean isDefaultConstructor(IntrospectMethod method) {
        return "<init>".equals(method.name()) && method.parameters().isEmpty();
    }
}
