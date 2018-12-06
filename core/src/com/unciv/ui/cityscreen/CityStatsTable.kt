package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.city.SpecialConstruction
import com.unciv.models.gamebasics.Building
import com.unciv.ui.pickerscreens.ConstructionPickerScreen
import com.unciv.ui.utils.*
import java.util.*

class CityStatsTable(val cityScreen: CityScreen) : Table(){
    fun update() {
        val city = cityScreen.city
        val buttonScale = 0.9f
        val stats = city.cityStats.currentCityStats
        pad(20f)
        columnDefaults(0).padRight(10f)
        clear()

        val cityStatsHeader = Label("City Stats", CameraStageBaseScreen.skin)

        cityStatsHeader.setFontSize(15)
        add(cityStatsHeader).colspan(2).pad(10f)
        row()

        val cityStatsValues = LinkedHashMap<String, String>()
        cityStatsValues["Production"] = Math.round(stats.production).toString() + city.cityConstructions.getAmountConstructedText()
        cityStatsValues["Food"] = (Math.round(stats.food).toString()
                + " (" + city.population.foodStored + "/" + city.population.getFoodToNextPopulation() + ")")
        cityStatsValues["Gold"] = Math.round(stats.gold).toString()
        cityStatsValues["Science"] = Math.round(stats.science).toString()
        cityStatsValues["Culture"] = (Math.round(stats.culture).toString()
                + " (" + city.expansion.cultureStored + "/" + city.expansion.getCultureToNextTile() + ")")
        cityStatsValues["Population"] = city.population.getFreePopulation().toString() + "/" + city.population.population
        cityStatsValues["Happiness"] = city.cityStats.getCityHappiness().values.sum().toInt().toString()
        if (city.resistanceCounter > 0) {
            cityStatsValues["Resistance"] = city.resistanceCounter.toString()
        }

        for (key in cityStatsValues.keys) {
            add(ImageGetter.getStatIcon(key)).size(20f).align(Align.right)
            add(Label(cityStatsValues[key], CameraStageBaseScreen.skin)).align(Align.left)
            row()
        }


        val buildingPickButton = Button(CameraStageBaseScreen.skin)
        val buildingText = city.cityConstructions.getCityProductionTextForCityButton()
        buildingPickButton.add(ImageGetter.getConstructionImage(city.cityConstructions.currentConstruction))
                .size(30f).pad(5f)
        buildingPickButton.add(Label(buildingText , CameraStageBaseScreen.skin).setFontColor(Color.WHITE))
        buildingPickButton.onClick {
            UnCivGame.Current.screen = ConstructionPickerScreen(city)
            cityScreen.dispose()
        }
        buildingPickButton.pack()

        add(buildingPickButton).colspan(2).pad(10f)
                .size(buildingPickButton.width * buttonScale, buildingPickButton.height * buttonScale)


        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        val construction = city.cityConstructions.getCurrentConstruction()
        if (construction !is SpecialConstruction &&
                !(construction is Building && construction.isWonder)) {
            row()
            val buildingGoldCost = construction.getGoldCost(city.civInfo.policies.getAdoptedPolicies())
            val buildingBuyButton = TextButton("Buy for [$buildingGoldCost] gold".tr(), CameraStageBaseScreen.skin)
            buildingBuyButton.onClick {
                city.cityConstructions.purchaseBuilding(city.cityConstructions.currentConstruction)
                update()
            }
            if (buildingGoldCost > city.civInfo.gold) {
                buildingBuyButton.disable()
            }
            add(buildingBuyButton).colspan(2).pad(10f)
                    .size(buildingBuyButton.width * buttonScale, buildingBuyButton.height * buttonScale)
        }

        setPosition(10f, 10f)
        pack()
    }
}