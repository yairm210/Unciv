package com.unciv.ui.worldscreen.minimap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.UncivGame
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle

/**
 * Class that unifies the behaviour of the little green map overlay toggle buttons shown next to the minimap.
 *
 * @param icon An [Image] to display.
 * @property getter A function that returns the current backing state of the toggle.
 * @property setter A function for setting the backing state of the toggle.
 * @param backgroundColor If non-null, a background colour to show behind the image.
 */
class MapOverlayToggleButton(
    icon: Image,
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit,
    backgroundColor: Color? = null
) {
    /** [Actor] of the button. Add this to whatever layout. */
    val actor: IconCircleGroup by lazy {
        var innerActor: Actor = icon
        val iconSize = 30f
        if (backgroundColor != null) {
            innerActor = innerActor
                .surroundWithCircle(iconSize)
                .apply { circle.color = backgroundColor }
        } else innerActor.setSize(iconSize,iconSize)
        // So, the "Food" and "Population" stat icons have green as part of their image, but the "Cattle" icon needs a background colour, which is… An interesting mixture/reuse of texture data and render-time processing.
        innerActor.surroundWithCircle(32f, resizeActor = false).apply { circle.color = Color.BLACK }
    }

    init {
        actor.onClick(::toggle)
    }

    /** Toggle overlay. Called on click. */
    fun toggle() {
        setter(!getter())
        UncivGame.Current.worldScreen!!.shouldUpdate = true
        // Setting worldScreen.shouldUpdate implicitly causes this.update() to be called by the WorldScreen on the next update.
    }

    /** Update. Called via [WorldScreen.shouldUpdate] on toggle. */
    fun update() {
        actor.actor.color.a = if (getter()) 1f else 0.5f
    }
}
