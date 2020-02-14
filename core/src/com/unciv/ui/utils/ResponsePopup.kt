package com.unciv.ui.utils

import kotlin.concurrent.thread

class ResponsePopup (message: String, screen: CameraStageBaseScreen, time: Long = 1000) : Popup(screen){
    init {
        thread (name = "ResponsePopup") {
            val responsePopup = Popup(screen)
            responsePopup.bottom()
            responsePopup.addGoodSizedLabel(message)
            responsePopup.open()
            responsePopup.y = screen.stage.height - (responsePopup.height + responsePopup.padTop)
            Thread.sleep(time)
            responsePopup.close()
        }
    }
}
