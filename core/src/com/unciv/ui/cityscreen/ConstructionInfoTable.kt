package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.SpecialConstruction
import com.unciv.ui.utils.ImageGetter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class ConstructionInfoTable(val city: CityInfo): Table() {
    val selectedConstructionTable = Table()
    init{
        selectedConstructionTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        add(selectedConstructionTable).pad(2f).fill()
        background = ImageGetter.getBackground(Color.WHITE)
    }

    fun update(selectedConstruction: IConstruction?) {
        selectedConstructionTable.clear()
        selectedConstructionTable.pad(20f)

        if (selectedConstruction == null) {
            isVisible = false
            return
        }
        isVisible = true

        addSelectedConstructionTable(selectedConstruction)

        pack()
    }

    private fun addSelectedConstructionTable(construction: IConstruction) {
        val cityConstructions = city.cityConstructions

        //val selectedConstructionTable = Table()
        selectedConstructionTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK,0.5f))
        selectedConstructionTable.pad(10f)

        selectedConstructionTable.add(
                ImageGetter.getConstructionImage(construction.name).surroundWithCircle(50f))
                .pad(5f)


        var buildingText = construction.name.tr()
        if (SpecialConstruction.getSpecialConstructions().none { it.name == construction.name  }) {
            val turnsToComplete = cityConstructions.turnsToConstruction(construction.name)
            buildingText += ("\r\n" + "Cost".tr() + " " + construction.getProductionCost(city.civInfo).toString()).tr()
            buildingText += "\r\n" + turnsToComplete + turnOrTurns(turnsToComplete)
        }
        selectedConstructionTable.add(buildingText.toLabel()).row()


        val description: String
        if (construction is BaseUnit)
            description = construction.getDescription(true)
        else if (construction is Building)
            description = construction.getDescription(true, city.civInfo, city.civInfo.gameInfo.ruleSet)
        else if(construction is SpecialConstruction)
            description = construction.description.tr()
        else description="" // Should never happen

        val descriptionLabel = description.toLabel()
        descriptionLabel.setWrap(true)
        descriptionLabel.width = stage.width / 4

        val descriptionScroll = ScrollPane(descriptionLabel)
        selectedConstructionTable.add(descriptionScroll).colspan(2).width(stage.width / 4).height(stage.height / 8)

    }

    private fun turnOrTurns(number: Int): String = if(number > 1) " {turns}".tr() else " {turn}".tr()
}