package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.city.CityInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener

class ConstructionPickerScreen(val city: CityInfo) : PickerScreen() {
    private var selectedProduction: String? = null

    private fun getProductionButton(production: String, buttonText: String,
                                    description: String?, rightSideButtonText: String): TextButton {
        val productionTextButton = TextButton(buttonText, skin)
        productionTextButton.addClickListener {
            selectedProduction = production
            pick(rightSideButtonText)
            descriptionLabel.setText(description)
        }
        if(production==city.cityConstructions.currentConstruction) productionTextButton.color= Color.GREEN
        return productionTextButton
    }

    init {
        val civInfo = game.gameInfo.getPlayerCivilization()

        closeButton.clearListeners() // Don't go back to the world screen, unlike the other picker screens!
        closeButton.addClickListener {
            game.screen = CityScreen(this@ConstructionPickerScreen.city)
            dispose()
        }

        rightSideButton.setText("Pick building")
        rightSideButton.addClickListener {
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

        for (building in GameBasics.Buildings.values) {
            if (!building.isBuildable(cityConstructions)) continue
            val productionTextButton = getProductionButton(building.name,
                    building.name + "\r\n" + cityConstructions.turnsToConstruction(building.name) + " turns",
                    building.getDescription(true, civInfo.policies.getAdoptedPolicies()),
                    "Build " + building.name)
            if (building.isWonder)
                wonders.addActor(productionTextButton)
            else
                regularBuildings.addActor(productionTextButton)
        }

        for (unit in GameBasics.Units.values.filter { it.isBuildable(cityConstructions) || it.name==cityConstructions.currentConstruction}) {
            units.addActor(getProductionButton(unit.name,
                    unit.name + "\r\n" + cityConstructions.turnsToConstruction(unit.name) + " turns",
                    unit.getDescription(true), "Train " + unit.name))
        }

        for(specialConstruction in cityConstructions.getSpecialConstructions().filter { it.isBuildable(cityConstructions) }){
            specials.addActor(getProductionButton(specialConstruction.name, "Produce ${specialConstruction.name}",
                    specialConstruction.description, "Produce ${specialConstruction.name}"))
        }

        topTable.add(units)
        topTable.add(regularBuildings)
        topTable.add(wonders)
        topTable.add(specials)
    }

}