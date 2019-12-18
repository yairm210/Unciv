package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.models.translations.tr
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class GreatPersonPickerScreen(val civInfo:CivilizationInfo) : PickerScreen() {
    private var theChosenOne: BaseUnit? = null

    init {
        closeButton.isVisible=false
        rightSideButton.setText("Choose a free great person".tr())
        for (unit in civInfo.gameInfo.ruleSet.Units.values
                .filter { it.name in GreatPersonManager().statToGreatPersonMapping.values || it.name == "Great General"})
        {
            val button = Button(skin)

            button.add(ImageGetter.getUnitIcon(unit.name)).size(30f).pad(10f)
            button.add(unit.name.toLabel()).pad(10f)
            button.pack()
            button.onClick {
                theChosenOne = unit
                val unitDescription=HashSet<String>()
                unit.uniques.forEach { unitDescription.add(it.tr()) }
                pick("Get ".tr() +unit.name.tr())
                descriptionLabel.setText(unitDescription.joinToString())
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick("choir") {
            civInfo.placeUnitNearTile(civInfo.cities[0].location, theChosenOne!!.name)
            civInfo.greatPeople.freeGreatPeople--
            UncivGame.Current.setWorldScreen()
        }

    }
}