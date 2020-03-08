package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.unciv.UncivGame
import com.unciv.logic.map.AncientRuins
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class RuinBonusPickerScreen(val actions: ArrayList<AncientRuins.RuinAction>, val tile: TileInfo) : PickerScreen() {
    private var theChosenOne: AncientRuins.RuinAction? = null

    init {
        closeButton.isVisible=false
        rightSideButton.setText("Choose an ancient ruins bonus".tr())

        for (action in actions) {
            val button = Button(skin)
            button.add(action.bonus.desc.toLabel()).pad(10f)
            button.pack()
            button.onClick {
                theChosenOne = action
                pick(action.bonus.desc)
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick(UncivSound.Choir) {
            theChosenOne!!.action.invoke()
            UncivGame.Current.setWorldScreen()
        }

    }
}