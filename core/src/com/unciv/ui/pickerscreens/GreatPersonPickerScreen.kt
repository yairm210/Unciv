package com.unciv.ui.pickerscreens

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.onDoubleClick

class GreatPersonPickerScreen(val civInfo:Civilization) : PickerScreen() {
    private var theChosenOne: BaseUnit? = null

    init {
        closeButton.isVisible = false
        rightSideButton.setText("Choose a free great person".tr())

        val greatPersonUnits = civInfo.greatPeople.getGreatPeople()
        val useMayaLongCount = civInfo.greatPeople.mayaLimitedFreeGP > 0

        for (unit in greatPersonUnits) {
            val button = PickerPane.getPickerOptionButton(ImageGetter.getUnitIcon(unit.name), unit.name)
            button.pack()
            button.isEnabled = !useMayaLongCount || unit.name in civInfo.greatPeople.longCountGPPool
            if (button.isEnabled) {
                button.onClick {
                    theChosenOne = unit
                    pick("Get [${unit.name}]".tr())
                    descriptionLabel.setText(unit.getShortDescription())
                }

                button.onDoubleClick(UncivSound.Choir) { confirmAction(useMayaLongCount) }
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick(UncivSound.Choir) {
            confirmAction(useMayaLongCount)
        }

    }

    private fun confirmAction(useMayaLongCount: Boolean){
        civInfo.units.addUnit(theChosenOne!!.name, civInfo.getCapital())
        civInfo.greatPeople.freeGreatPeople--
        if (useMayaLongCount) {
            civInfo.greatPeople.mayaLimitedFreeGP--
            civInfo.greatPeople.longCountGPPool.remove(theChosenOne!!.name)
        }
        UncivGame.Current.popScreen()
    }
}

