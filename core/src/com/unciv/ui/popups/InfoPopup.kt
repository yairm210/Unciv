package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.ui.components.extensions.toLabel
import com.unciv.utils.concurrency.Concurrency

/** Variant of [Popup] with one label and a cancel button
 * @param stageToShowOn Parent [Stage], see [Popup.stageToShowOn]
 * @param text The text for the label
 * @param action A lambda to execute when the button is pressed, after closing the popup
 */
open class InfoPopup(
    stageToShowOn: Stage,
    text: String,
    action: (() -> Unit)? = null
) : Popup(stageToShowOn) {

    /** The [Label][com.badlogic.gdx.scenes.scene2d.ui.Label] created for parameter `text` for optional layout tweaking */
    private val label = text.toLabel()

    init {
        label.setAlignment(Align.center)
        add(label).colspan(2).row()
        addCloseButton(action = action)
        open()
    }

    companion object {

        /**
         * Wrap the execution of a coroutine to display an [InfoPopup] when a [UncivShowableException] occurs
         */
        suspend fun <T> wrap(stage: Stage, function: suspend () -> T): T? {
            try {
                return function()
            } catch (e: UncivShowableException) {
                Concurrency.runOnGLThread {
                    InfoPopup(stage, e.localizedMessage)
                }
            }
            return null
        }

    }
}
