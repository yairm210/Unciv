package com.unciv.ui.utils

import com.unciv.UncivGame

class YesNoPopup(question:String, action:()->Unit,
                 screen: CameraStageBaseScreen = UncivGame.Current.worldScreen, restoredefault:()->Unit = {}) : Popup(screen){
    init{
        add(question.toLabel()).colspan(2).row()
        add("No".toTextButton().onClick { close(); restoredefault() })
        add("Yes".toTextButton().onClick { close(); action() })
    }
}
