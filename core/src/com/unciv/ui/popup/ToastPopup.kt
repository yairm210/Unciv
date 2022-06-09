package com.unciv.ui.popup

import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick
import kotlinx.coroutines.delay

/**
 * This is an unobtrusive popup which will close itself after a given amount of time.
 * Default time is two seconds (in milliseconds)
 */
class ToastPopup (message: String, stage: Stage, val time: Long = 2000) : Popup(stage){

    constructor(message: String, screen: BaseScreen, time: Long = 2000) : this(message, screen.stage, time)

    init {
        //Make this popup unobtrusive
        setFillParent(false)
        onClick { close() }  // or `touchable = Touchable.disabled` so you can operate what's behind

        addGoodSizedLabel(message)
        open()
        //move it to the top so its not in the middle of the screen
        //have to be done after open() because open() centers the popup
        y = stage.height - (height + 20f)
    }

    private fun startTimer(){
        launchCrashHandling("ResponsePopup") {
            delay(time)
            postCrashHandlingRunnable { this@ToastPopup.close() }
        }
    }

    override fun setVisible(visible: Boolean) {
        if (visible)
            startTimer()
        super.setVisible(visible)
    }
}
