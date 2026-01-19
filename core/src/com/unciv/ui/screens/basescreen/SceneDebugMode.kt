package com.unciv.ui.screens.basescreen

import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.models.translations.tr

/**
 *  This controls what the "Scene2D debug mode" actually does.
 *
 *  Class can be directly used in a SelectBox.
 *
 *  Note - regrettably, there is no way to have mouseover debug lines for the next ascendant Table if there is one,
 *  but fall back the parent if there is one, or the hit actor if it's floating alone.
 *
 *  @property label UI label, translatable. Not automatically templated.
 *  @property basic Enables any mouse-over debug lines. Passed to [Stage.setDebugUnderMouse].
 *  @property parent Enables preferring the parent to the hit actor if it has one. Passed to [Stage.setDebugParentUnderMouse].
 *  @property tables Enables looking _only_ for Table instances from the hit actor up. Passed to [Stage.setDebugTableUnderMouse].
 *  @property all Enables recursion of the debug flag to children. Passed to [Stage.setDebugAll].
 *  @property overlay Enables the info overlay [StageMouseOverDebug]. Independent of the other flags.
 *  @see Stage.drawDebug
 */
enum class SceneDebugMode(
    val label: String,
    val basic: Boolean,
    val parent: Boolean,
    val tables: Boolean,
    val all: Boolean,
    val overlay: Boolean
) {
    None("Off", false, false, false, false, false),
    Tables("Tables", true, false, true, false, true),
    All("All actors", true, false, false, true, true),
    ;

    val active get() = basic || overlay
    fun next() = entries[(ordinal + 1) % entries.size]
    override fun toString() = label.tr()
}
