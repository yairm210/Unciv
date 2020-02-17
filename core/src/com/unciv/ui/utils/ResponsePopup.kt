package com.unciv.ui.utils

import kotlin.concurrent.thread

//Its a popUp which will close itself after a given amount of time
//Standard time is one second (in milliseconds)
class ResponsePopup (message: String, screen: CameraStageBaseScreen, time: Long = 1000) : Popup(screen){
    init {
        thread (name = "ResponsePopup") {
            val responsePopup = Popup(screen)
            responsePopup.addGoodSizedLabel(message)
            responsePopup.open()
            //move it to the top so its not in the middle of the screen
            //have to be done after open() because open() centers the popup
            responsePopup.y = screen.stage.height - (responsePopup.height + responsePopup.padTop)
            Thread.sleep(time)
            responsePopup.close()
        }
    }
}
