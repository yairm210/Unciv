package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.extensions.addRoundCloseButton
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.clearActivationActions
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * This is an unobtrusive popup which will close itself after a given amount of time.
 * - Will show on top of other Popups, but not on top of other ToastPopups
 * - Several calls in a short time will be shown sequentially, each "waiting their turn"
 * - The user can close a Toast by clicking it
 * - Supports color markup via [ColorMarkupLabel], using «» instead of Gdx's [].
 * @param time Duration in milliseconds, defaults to 2 seconds
 */
class ToastPopup (message: String, stageToShowOn: Stage, val time: Long = 2000) : Popup(stageToShowOn) {

    constructor(message: String, screen: BaseScreen, time: Long = 2000) : this(message, screen.stage, time)

    private var timerJob: Job? = null

    init {
        //Make this popup unobtrusive
        setFillParent(false)
        onClick(::stayVisible) // or `touchable = Touchable.disabled` so you can operate what's behind

        add(ColorMarkupLabel(message).apply {
            wrap = true
            setAlignment(Align.center)
        }).width(stageToShowOn.width / 2)

        open(force = stageToShowOn.actors.none { it is ToastPopup })

        // move it to the top so its not in the middle of the screen
        // has to be done after open() because open() centers the popup
        y = stageToShowOn.height - (height + 20f)
    }

    private fun startTimer() {
        timerJob = Concurrency.run("ResponsePopup") {
            delay(time)
            timerJob = null
            launchOnGLThread { this@ToastPopup.close() }
        }
    }
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun close() {
        stopTimer()
        super.close()
    }

    override fun setVisible(visible: Boolean) {
        if (visible)
            startTimer()
        super.setVisible(visible)
    }

    private fun stayVisible() {
        stopTimer()
        clearActivationActions(ActivationTypes.Tap, false)
        onClick(::close)
        addRoundCloseButton(this, ::close)
    }
}
