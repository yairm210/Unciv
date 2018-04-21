package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Unit
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen

class GreatPersonPickerScreen : PickerScreen() {
    private var theChosenOne: Unit? = null
    internal var civInfo: CivilizationInfo? = null

    init {
        rightSideButton.setText("Choose a free great person")
        for (unit in GameBasics.Units.values) {
            if (!unit.name.startsWith("Great")) continue
            val button = TextButton(unit.name, CameraStageBaseScreen.skin)
            button.addClickListener {
                theChosenOne = unit
                pick(unit.name)
            }
            topTable.add(button).pad(10f)
        }

        rightSideButton.addClickListener {
            civInfo!!.placeUnitNearTile(civInfo!!.cities[0].location, theChosenOne!!.name)
        }

    }
}
