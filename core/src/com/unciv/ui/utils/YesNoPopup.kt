package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.models.translations.tr

class YesNoPopup(question:String, action:()->Unit,
                 screen: CameraStageBaseScreen = UncivGame.Current.worldScreen, restoredefault:()->Unit = {}) : Popup(screen){
    init{
        add(question.toLabel()).colspan(2).row()
        add(TextButton("No".tr(), skin).onClick { close(); restoredefault() })
        add(TextButton("Yes".tr(), skin).onClick { close(); action() })
    }
}
