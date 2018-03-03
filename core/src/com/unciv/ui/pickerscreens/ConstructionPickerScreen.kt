package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.city.CityInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen

class ConstructionPickerScreen(val city: CityInfo) : PickerScreen() {
    private var selectedProduction: String?=null

    private fun getProductionButton(production: String, buttonText: String,
                                    description: String?, rightSideButtonText: String): TextButton {
        val productionTextButton = TextButton(buttonText, CameraStageBaseScreen.skin)
        productionTextButton.addClickListener {
                selectedProduction = production
                pick(rightSideButtonText)
                descriptionLabel.setText(description)
            }
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

        for (unit in GameBasics.Units.values.filter { it.isConstructable }) {
            units.addActor(getProductionButton(unit.name,
                    unit.name + "\r\n" + cityConstructions.turnsToConstruction(unit.name) + " turns",
                    unit.description, "Train " + unit.name))
        }

        if (civInfo.tech.isResearched("Education"))
            specials.addActor(getProductionButton("Science", "Produce Science",
                    "Convert production to science at a rate of 4 to 1", "Produce Science"))

        if (civInfo.tech.isResearched("Currency"))
            specials.addActor(getProductionButton("Gold", "Produce Gold",
                    "Convert production to gold at a rate of 4 to 1", "Produce Gold"))

        topTable.add(units)
        topTable.add(regularBuildings)
        topTable.add(wonders)
        topTable.add(specials)
    }

}