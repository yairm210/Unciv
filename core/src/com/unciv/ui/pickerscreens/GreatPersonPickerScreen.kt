package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.isEnabled
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class GreatPersonPickerScreen(val civInfo:CivilizationInfo) : PickerScreen() {
    private var theChosenOne: BaseUnit? = null

    init {
        closeButton.isVisible = false
        rightSideButton.setText("Choose a free great person".tr())

        val greatPersonUnits = civInfo.getGreatPeople()
        val useMayaLongCount = civInfo.greatPeople.mayaLimitedFreeGP > 0

        for (unit in greatPersonUnits) {
            val button = Button(skin)

            button.add(ImageGetter.getUnitIcon(unit.name)).size(30f).pad(10f)
            button.add(unit.name.toLabel()).pad(10f)
            button.pack()
            button.isEnabled = !useMayaLongCount || unit.name in civInfo.greatPeople.longCountGPPool
            if (button.isEnabled) button.onClick {
                theChosenOne = unit
                pick("Get [${unit.name}]".tr())
                descriptionLabel.setText(unit.getShortDescription())
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick(UncivSound.Choir) {
            civInfo.addUnit(theChosenOne!!.name, civInfo.getCapital())
            civInfo.greatPeople.freeGreatPeople--
            if (useMayaLongCount) {
                civInfo.greatPeople.mayaLimitedFreeGP--
                civInfo.greatPeople.longCountGPPool.remove(theChosenOne!!.name)
            }
            UncivGame.Current.setWorldScreen()
        }

    }
}

