package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UnCivGame
import com.unciv.logic.city.SpecialConstruction
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.ConstructionPickerScreen
import com.unciv.ui.utils.*

class ConstructionsTable(val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin){

    private fun getProductionButton(production: String, buttonText: String,
                                    description: String?, rightSideButtonText: String): Button {
        val productionTextButton = Button(CameraStageBaseScreen.skin)
        productionTextButton.add(ImageGetter.getConstructionImage(production)).size(40f).padRight(5f)
        productionTextButton.add(Label(buttonText, CameraStageBaseScreen.skin).setFontColor(Color.WHITE))
        productionTextButton.onClick {
            cityScreen.city.cityConstructions.currentConstruction = production
            update()
        }
        if(production==cityScreen.city.cityConstructions.currentConstruction)
            productionTextButton.color= Color.GREEN
        return productionTextButton
    }


    fun update() {
        val city = cityScreen.city
        val buttonScale = 0.9f
        pad(20f)
        columnDefaults(0).padRight(10f)
        clear()

        val cityConstructions = city.cityConstructions
        val regularBuildings = VerticalGroup().space(10f)
        val wonders = VerticalGroup().space(10f)
        val units = VerticalGroup().space(10f)
        val specials = VerticalGroup().space(10f)

        for (unit in GameBasics.Units.values.filter { it.isBuildable(cityConstructions)}) {
            units.addActor(getProductionButton(unit.name,
                    unit.name + "\r\n" + cityConstructions.turnsToConstruction(unit.name) + " {turns}".tr(),
                    unit.getDescription(true), "Train [${unit.name}]".tr()))
        }

        for (building in GameBasics.Buildings.values) {
            if (!building.isBuildable(cityConstructions) && building.name!=cityConstructions.currentConstruction) continue
            val productionTextButton = getProductionButton(building.name,
                    building.name + "\r\n" + cityConstructions.turnsToConstruction(building.name) + " {turns}".tr(),
                    building.getDescription(true, city.civInfo.policies.getAdoptedPolicies()),
                    "Build [${building.name}]".tr())
            if (building.isWonder)
                wonders.addActor(productionTextButton)
            else
                regularBuildings.addActor(productionTextButton)
        }


        for(specialConstruction in SpecialConstruction.getSpecialConstructions().filter { it.isBuildable(cityConstructions) }){
            specials.addActor(getProductionButton(specialConstruction.name, "Produce [${specialConstruction.name}]".tr(),
                    specialConstruction.description, "Produce [${specialConstruction.name}]".tr()))
        }

        val constructionPickerTable = Table()
        constructionPickerTable.add(ExpanderTab("Units".tr(),skin).apply { this.innerTable.add(units) }).row()
        constructionPickerTable.add(ExpanderTab("Buildings".tr(),skin).apply { this.innerTable.add(regularBuildings) }).row()
        constructionPickerTable.add(ExpanderTab("Wonders".tr(),skin).apply { this.innerTable.add(wonders) }).row()
        constructionPickerTable.add(ExpanderTab("Special".tr(),skin).apply { this.innerTable.add(specials) }).row()

        val scrollPane = ScrollPane(constructionPickerTable,skin)
        add(scrollPane).height(cityScreen.stage.height/2).row()

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
            buildingBuyButton.onClick("coin") {
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