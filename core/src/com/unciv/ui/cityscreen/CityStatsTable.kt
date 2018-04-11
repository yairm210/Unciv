package com.unciv.ui.cityscreen

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.models.gamebasics.Building
import com.unciv.UnCivGame
import com.unciv.ui.pickerscreens.ConstructionPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.disable
import java.util.*

class CityStatsTable(val cityScreen: CityScreen) : Table(){
    fun update() {
        val city = cityScreen.city
        val buttonScale = cityScreen.game.settings.buttonScale
        val stats = city.cityStats.currentCityStats
        pad(20f)
        columnDefaults(0).padRight(10f)
        clear()

        val cityStatsHeader = Label("City Stats", CameraStageBaseScreen.skin)

        cityStatsHeader.setFontScale(2f)
        add(cityStatsHeader).colspan(2).pad(10f)
        row()

        val cityStatsValues = LinkedHashMap<String, String>()
        cityStatsValues["Production"] = Math.round(stats.production).toString() + city.cityConstructions.getAmountConstructedText()
        cityStatsValues["Food"] = (Math.round(stats.food).toString()
                + " (" + city.population.foodStored + "/" + city.population.foodToNextPopulation + ")")
        cityStatsValues["Gold"] = Math.round(stats.gold).toString() + ""
        cityStatsValues["Science"] = Math.round(stats.science).toString() + ""
        cityStatsValues["Culture"] = (Math.round(stats.culture).toString()
                + " (" + city.expansion.cultureStored + "/" + city.expansion.cultureToNextTile + ")")
        cityStatsValues["Population"] = city.population.freePopulation.toString() + "/" + city.population.population

        for (key in cityStatsValues.keys) {
            add(ImageGetter.getStatIcon(key)).align(Align.right)
            add(Label(cityStatsValues[key], CameraStageBaseScreen.skin)).align(Align.left)
            row()
        }

        val buildingText = city.cityConstructions.getCityProductionTextForCityButton()
        val buildingPickButton = TextButton(buildingText, CameraStageBaseScreen.skin)
        buildingPickButton.addClickListener {
            UnCivGame.Current.screen = ConstructionPickerScreen(city)
            cityScreen.dispose()
        }

        buildingPickButton.label.setFontScale(buttonScale)
        add(buildingPickButton).colspan(2).pad(10f)
                .size(buildingPickButton.width * buttonScale, buildingPickButton.height * buttonScale)


        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        val construction = city.cityConstructions.getCurrentConstruction()
        if (!(construction is Building && construction.isWonder)) {
            row()
            val buildingGoldCost = construction.getGoldCost(city.civInfo.policies.getAdoptedPolicies())
            val buildingBuyButton = TextButton("Buy for \r\n$buildingGoldCost gold", CameraStageBaseScreen.skin)
            buildingBuyButton.addClickListener {
                city.cityConstructions.purchaseBuilding(city.cityConstructions.currentConstruction)
                update()
            }
            if (buildingGoldCost > city.civInfo.gold) {
                buildingBuyButton.disable()
            }
            buildingBuyButton.label.setFontScale(buttonScale)
            add(buildingBuyButton).colspan(2).pad(10f)
                    .size(buildingBuyButton.width * buttonScale, buildingBuyButton.height * buttonScale)
        }

        setPosition(10f, 10f)
        pack()
    }
}