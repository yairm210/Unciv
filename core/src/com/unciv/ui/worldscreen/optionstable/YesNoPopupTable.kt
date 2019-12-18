package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class YesNoPopupTable(question:String, action:()->Unit,
                      screen: CameraStageBaseScreen = UncivGame.Current.worldScreen, restoredefault:()->Unit = {}) : PopupTable(screen){
    init{
        if(!screen.hasPopupOpen) {
            screen.hasPopupOpen=true
            add(question.toLabel()).colspan(2).row()
            add(TextButton("No".tr(), skin).onClick { close(); restoredefault() })
            add(TextButton("Yes".tr(), skin).onClick { close(); action() })
            open()
        }
    }

    override fun close(){
        super.close()
        screen.hasPopupOpen=false
    }
}