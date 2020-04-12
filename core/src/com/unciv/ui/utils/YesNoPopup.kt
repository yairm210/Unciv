package com.unciv.ui.utils

import com.unciv.UncivGame

class YesNoPopup(question:String, action:()->Unit,
                 screen: CameraStageBaseScreen = UncivGame.Current.worldScreen, restoreDefault:()->Unit = {}) : Popup(screen){
    init{
        add(question.toLabel()).colspan(2).row()
        addInlineButton("No") { close(); restoreDefault() }
        addInlineButton("Yes") { close(); action() }
    }
}
