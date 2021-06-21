package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import kotlin.concurrent.thread

/**
 * This is an unobtrusive popup which will close itself after a given amount of time.
 * Default time is two seconds (in milliseconds)
 */
class ToastPopup (message: String, screen: CameraStageBaseScreen, val time: Long = 2000) : Popup(screen){
    init {
        //Make this popup unobtrusive
        setFillParent(false)

        addGoodSizedLabel(message)
        open()
        //move it to the top so its not in the middle of the screen
        //have to be done after open() because open() centers the popup
        y = screen.stage.height - (height + 20f)
    }

    private fun startTimer(){
        thread (name = "ResponsePopup") {
            Thread.sleep(time)
            Gdx.app.postRunnable { this.close() }
        }
    }

    override fun setVisible(visible: Boolean) {
        if (visible)
            startTimer()
        super.setVisible(visible)
    }
}
