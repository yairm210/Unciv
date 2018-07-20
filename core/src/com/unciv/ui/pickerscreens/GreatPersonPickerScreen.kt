package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.setFontColor

class GreatPersonPickerScreen : PickerScreen() {
    private var theChosenOne: BaseUnit? = null

    init {
        closeButton.isVisible=false
        rightSideButton.setText("Choose a free great person")
        for (unit in GameBasics.Units.values.filter { it.name.startsWith("Great") }) {
            val button = Button(skin)

            button.add(ImageGetter.getUnitIcon(unit.name)).size(30f).pad(10f)
            button.add(Label(unit.name, skin).setFontColor(Color.WHITE)).pad(10f)
            button.pack()
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
            civInfo.greatPeople.freeGreatPeople--
            UnCivGame.Current.setWorldScreen()
        }

    }
}
