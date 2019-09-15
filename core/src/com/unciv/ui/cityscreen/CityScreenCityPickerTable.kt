package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontSize
import com.unciv.ui.worldscreen.optionstable.PopupTable

class CityScreenCityPickerTable(val cityScreen: CityScreen) : Table(){

    fun update() {
        clear()
        val city = cityScreen.city
        val civInfo = city.civInfo

        if (civInfo.cities.size > 1) {
            val prevCityButton = TextButton("<", CameraStageBaseScreen.skin)
            prevCityButton.onClick {
                val indexOfCity = civInfo.cities.indexOf(city)
                val indexOfNextCity = if (indexOfCity == 0) civInfo.cities.size - 1 else indexOfCity - 1
                cityScreen.game.screen = CityScreen(civInfo.cities[indexOfNextCity])
            }
            add(prevCityButton).pad(20f)
        } else add()

        val cityNameTable = Table()
        if(city.isBeingRazed){
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            cityNameTable.add(fireImage).size(20f).padRight(5f)
        }

        if(city.isCapital()){
            val starImage = Image(ImageGetter.getDrawable("OtherIcons/Star").tint(Color.LIGHT_GRAY))
            cityNameTable.add(starImage).size(20f).padRight(5f)
        }

        val currentCityLabel = Label(city.name + " (" + city.population.population + ")", CameraStageBaseScreen.skin)
        currentCityLabel.setFontSize(25)
        currentCityLabel.onClick {
            val editCityNamePopup = PopupTable(cityScreen)
            val textArea = TextField(city.name, CameraStageBaseScreen.skin)
            textArea.setAlignment(Align.center)
            editCityNamePopup.add(textArea).colspan(2).row()
            editCityNamePopup.addCloseButton()
            editCityNamePopup.addButton("Save".tr()){
                city.name = textArea.text
                cityScreen.game.screen = CityScreen(city)
            }
            editCityNamePopup.open()
        }

        cityNameTable.add(currentCityLabel)

        add(cityNameTable)


        if (civInfo.cities.size > 1) {
            val nextCityButton = TextButton(">", CameraStageBaseScreen.skin)
            nextCityButton.onClick {
                val indexOfCity = civInfo.cities.indexOf(city)
                val indexOfNextCity = if (indexOfCity == civInfo.cities.size - 1) 0 else indexOfCity + 1
                cityScreen.game.screen = CityScreen(civInfo.cities[indexOfNextCity])
            }
            add(nextCityButton).pad(20f)
        } else add()
        row()

        val exitCityButton = TextButton("Exit city".tr(), CameraStageBaseScreen.skin)
        exitCityButton.labelCell.pad(10f)

        exitCityButton.onClick {
            val game = cityScreen.game
            game.setWorldScreen()
            game.worldScreen.tileMapHolder.setCenterPosition(city.location)
            game.worldScreen.bottomBar.unitTable.selectedUnit=null
        }

        add(exitCityButton).pad(10f).colspan(columns)

        pack()
    }

}