package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Unit
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen

class GreatPersonPickerScreen : PickerScreen() {
    private var theChosenOne: Unit? = null

    init {
        closeButton.isVisible=false
        rightSideButton.setText("Choose a free great person")
        for (unit in GameBasics.Units.values.filter { it.name.startsWith("Great") }) {
            val button = TextButton(unit.name, CameraStageBaseScreen.skin)
            button.addClickListener {
                theChosenOne = unit
                pick("Get " +unit.name)
                descriptionLabel.setText(unit.baseDescription)
            }
            topTable.add(button).pad(10f)
        }

        rightSideButton.addClickListener {
            val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()
            civInfo.placeUnitNearTile(civInfo.cities[0].location, theChosenOne!!.name)
            UnCivGame.Current.setWorldScreen()
        }

    }
}
