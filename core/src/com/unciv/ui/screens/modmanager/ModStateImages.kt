package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.images.ImageGetter

/** Helper class keeps references to decoration images of installed mods to enable dynamic visibility
 * (actually we do not use isVisible but refill a container selectively which allows the aggregate height to adapt and the set to center vertically)
 * @param visualImage   image indicating _enabled as permanent visual mod_
 * @param hasUpdateImage  image indicating _online mod has been updated_
 */
internal class ModStateImages (
    isVisual: Boolean = false,
    isUpdated: Boolean = false,
    private val visualImage: Image = ImageGetter.getImage("UnitPromotionIcons/Scouting"),
    private val hasUpdateImage: Image = ImageGetter.getImage("OtherIcons/Mods")
) {
    /** The table containing the indicators (one per mod, narrow, arranges up to three indicators vertically) */
    val container: Table = Table().apply { defaults().size(20f).align(Align.topLeft) }
    // mad but it's really initializing with the primary constructor parameter and not calling update()
    var isVisual: Boolean = isVisual
        set(value) { if (field!=value) { field = value; update() } }
    var hasUpdate: Boolean = isUpdated
        set(value) { if (field!=value) { field = value; update() } }
    private val spacer = Table().apply { width = 20f; height = 0f }

    fun update() {
        container.run {
            clear()
            if (isVisual) add(visualImage).row()
            if (hasUpdate) add(hasUpdateImage).row()
            if (!isVisual && !hasUpdate) add(spacer)
            pack()
        }
    }

    fun sortWeight() = when {
        hasUpdate && isVisual -> 3
        hasUpdate -> 2
        isVisual -> 1
        else -> 0
    }
}
