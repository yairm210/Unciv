package com.unciv.ui.screens.worldscreen.minimap

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.GUI
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * Class that unifies the behaviour of the little green map overlay toggle buttons shown next to the minimap.
 *
 * @param iconPath Path to an [Image] to display, will be fetched via [ImageGetter.getImage].
 * @param iconSize inner icon size (2f outer circle will be added).
 * @property getter A function that returns the current backing state of the toggle.
 * @property setter A function for setting the backing state of the toggle.
 */
class MapOverlayToggleButton(
    iconPath: String,
    iconSize: Float = 30f,
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit
) : IconCircleGroup(
    size = iconSize + 2f,
    actor = ImageGetter.getImage(iconPath).apply {
        setSize(iconSize)
    },
    resizeActor = false
) {
    init {
        circle.color = ImageGetter.CHARCOAL
        onClick(::toggle)
    }

    /** Toggle overlay. Called on click. */
    fun toggle() {
        setter(!getter())
        GUI.setUpdateWorldOnNextRender()
        // Setting worldScreen.shouldUpdate implicitly causes this.update() to be called by the WorldScreen on the next update.
    }

    /** Update. Called via [WorldScreen.shouldUpdate] on toggle. */
    fun update() {
        actor.color.a = if (getter()) 1f else 0.5f
    }
}
