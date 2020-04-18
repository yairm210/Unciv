package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Align
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class CityScreenCityPickerTable(val cityScreen: CityScreen) : Table(){

    fun update() {
        clear()
        val city = cityScreen.city
        val civInfo = city.civInfo

        if (civInfo.cities.size > 1) {
            val prevCityButton = "<".toTextButton()
            prevCityButton.onClick { cityScreen.page(-1) }
            add(prevCityButton).pad(20f)
        } else add()

        val cityNameTable = Table()
        if(city.isBeingRazed){
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            cityNameTable.add(fireImage).size(20f).padRight(5f)
        }

        if(city.isCapital()){
            val starImage = ImageGetter.getImage("OtherIcons/Star").apply { color= Color.LIGHT_GRAY }
            cityNameTable.add(starImage).size(20f).padRight(5f)
        }

        if(city.isPuppet){
            val starImage = ImageGetter.getImage("OtherIcons/Puppet").apply { color= Color.LIGHT_GRAY }
            cityNameTable.add(starImage).size(20f).padRight(5f)
        }


        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            cityNameTable.add(resistanceImage).size(20f).padRight(5f)
        }

        val currentCityLabel = city.name.toLabel(fontSize = 30)
        currentCityLabel.onClick {
            val editCityNamePopup = Popup(cityScreen)
            val textArea = TextField(city.name, CameraStageBaseScreen.skin)
            textArea.alignment = Align.center
            editCityNamePopup.add(textArea).colspan(2).row()
            editCityNamePopup.addCloseButton()
            editCityNamePopup.addButton("Save".tr()){
                city.name = textArea.text
                cityScreen.game.setScreen(CityScreen(city))
            }
            editCityNamePopup.open()
        }

        cityNameTable.add(currentCityLabel)

        add(cityNameTable)


        if (civInfo.cities.size > 1) {
            val nextCityButton = ">".toTextButton()
            nextCityButton.onClick { cityScreen.page(1) }
            add(nextCityButton).pad(20f)
        } else add()
        row()

        val exitCityButton = "Exit city".toTextButton()
        exitCityButton.labelCell.pad(10f)

        exitCityButton.onClick { cityScreen.exit() }

        add(exitCityButton).pad(10f).colspan(columns)

        pack()
    }

}
