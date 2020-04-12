package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick

class GreatPersonPickerScreen(val civInfo:CivilizationInfo) : PickerScreen() {
    private var theChosenOne: BaseUnit? = null

    init {
        closeButton.isVisible = false

        val greatPersonNames = GreatPersonManager().statToGreatPersonMapping.values
                .union(listOf("Great General"))
        val greatPersonUnits = greatPersonNames.map { civInfo.getEquivalentUnit(it) }
        for (unit in greatPersonUnits)
        {
            val button = Button(skin)

            val unitTranslated = unit.name.tr()
            val unitDescription = unit.getShortDescription()
            button.add(ImageGetter.getUnitIcon(unit.name)).size(30f).pad(10f)
            button.add(Label(unitTranslated,skin)).pad(10f)
            button.pack()
            val action = {
                theChosenOne = unit
                pick("Get ".tr() + unitTranslated)
                descriptionLabel.setText(unitDescription)
            }
            button.onClick (UncivSound.Choir, action)
            registerKeyHandler (unitTranslated, action)
            topTable.add(button).pad(10f).row()
        }

        val action = {
            civInfo.placeUnitNearTile(civInfo.cities[0].location, theChosenOne!!.name)
            civInfo.greatPeople.freeGreatPeople--
            UncivGame.Current.setWorldScreen()
        }
        setAcceptButtonAction("Choose a free great person", UncivSound.Choir, action)

    }
}