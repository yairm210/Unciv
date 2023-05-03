package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.logic.UncivShowableException
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.runBlocking

/** Variant of [Popup] with one label and a cancel button
 * @param stageToShowOn Parent [Stage], see [Popup.stageToShowOn]
 * @param texts The texts for the popup, as separated good-sized labels
 * @param action A lambda to execute when the button is pressed, after closing the popup
 */
class InfoPopup(
    stageToShowOn: Stage,
    vararg texts: String,
    action: (() -> Unit)? = null
) : Popup(stageToShowOn) {

    init {
        for (element in texts) {
            addGoodSizedLabel(element).row()
        }
        addCloseButton(action = action).row()
        open(force = true)
    }

    companion object {

        /**
         * Wrap the execution of a [coroutine] to display an [InfoPopup] when a [UncivShowableException] occurs
         */
        suspend fun <T> wrap(stage: Stage, vararg texts: String, coroutine: suspend () -> T): T? {
            try {
                return coroutine()
            } catch (e: UncivShowableException) {
                Concurrency.runOnGLThread {
                    InfoPopup(stage, *texts, e.localizedMessage)
                }
            }
            return null
        }

        /**
         * Show a loading popup while running a [coroutine] and return its optional result
         *
         * This function will display an [InfoPopup] when a [UncivShowableException] occurs.
         */
        fun <T> load(stage: Stage, vararg texts: String, coroutine: suspend () -> T): T? {
            val popup = Popup(stage).apply { addGoodSizedLabel("Working...").row() }
            popup.open(force = true)
            var result: T? = null
            val job = Concurrency.run {
                try {
                    result = coroutine()
                    Concurrency.runOnGLThread {
                        popup.close()
                    }
                } catch (e: UncivShowableException) {
                    Concurrency.runOnGLThread {
                        popup.close()
                        InfoPopup(stage, *texts, e.localizedMessage)
                    }
                }
            }
            runBlocking { job.join() }
            return result
        }
    }

}
