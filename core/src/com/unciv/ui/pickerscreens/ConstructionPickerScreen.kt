package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.SpecialConstruction
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontColor

class ConstructionPickerScreen(val city: CityInfo) : PickerScreen() {
    private var selectedProduction: String? = null

    private var buySelectedProductionButton = TextButton("BUY ME!",skin)

    private fun getProductionButton(production: String, buttonText: String,
                                    description: String?, rightSideButtonText: String): Button {
        val productionTextButton = Button(skin)
        productionTextButton.add(ImageGetter.getConstructionImage(production)).size(40f).padRight(5f)
        productionTextButton.add(Label(buttonText,skin).setFontColor(Color.WHITE))
        productionTextButton.onClick {
            selectedProduction = production
            pick(rightSideButtonText)
            descriptionLabel.setText(description)
        }
        if(production==city.cityConstructions.currentConstruction) productionTextButton.color= Color.GREEN
        return productionTextButton
    }

    init {
        val currentPlayerCiv = game.gameInfo.getCurrentPlayerCivilization()

        closeButton.onClick {
            game.screen = CityScreen(this@ConstructionPickerScreen.city)
            dispose()
        }
        onBackButtonClicked {
            game.screen = CityScreen(this@ConstructionPickerScreen.city)
            dispose()
        }

        rightSideButton.setText("Pick construction".tr())
        rightSideButton.onClick {
            city.cityConstructions.currentConstruction = selectedProduction!!
            city.cityStats.update() // Because maybe we set/removed the science or gold production options.
            game.screen = CityScreen(this@ConstructionPickerScreen.city)
            dispose()
        }

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
                    building.getDescription(true, currentPlayerCiv.policies.getAdoptedPolicies()),
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

        topTable.add(units)
        topTable.add(regularBuildings)
        topTable.add(wonders)
        topTable.add(specials)
    }

}