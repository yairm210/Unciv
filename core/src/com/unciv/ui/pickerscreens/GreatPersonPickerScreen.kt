package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.unciv.UnCivGame
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontColor
import com.unciv.ui.utils.toLabel

class GreatPersonPickerScreen : PickerScreen() {
    private var theChosenOne: BaseUnit? = null

    init {
        closeButton.isVisible=false
        rightSideButton.setText("Choose a free great person".tr())
        for (unit in GameBasics.Units.values
                .filter { it.name in GreatPersonManager().statToGreatPersonMapping.values || it.name == "Great General"})
        {
            val button = Button(skin)

            button.add(ImageGetter.getUnitIcon(unit.name)).size(30f).pad(10f)
            button.add(unit.name.toLabel().setFontColor(Color.WHITE)).pad(10f)
            button.pack()
            button.onClick {
                theChosenOne = unit
                var  unitDescription=HashSet<String>()
                unit.uniques.forEach { unitDescription.add(it.tr()) }
                pick("Get ".tr() +unit.name.tr())
                descriptionLabel.setText(unitDescription.joinToString())
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick("choir") {
            val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
            currentPlayerCiv.placeUnitNearTile(currentPlayerCiv.cities[0].location, theChosenOne!!.name)
            currentPlayerCiv.greatPeople.freeGreatPeople--
            UnCivGame.Current.setWorldScreen()
        }

    }
}