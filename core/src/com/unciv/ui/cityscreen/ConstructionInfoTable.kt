package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.surroundWithCircle
import com.unciv.ui.utils.toLabel

class ConstructionInfoTable(val cityScreen: CityScreen): Table() {
    private val selectedConstructionTable = Table()
    val city = cityScreen.city

    init {
        selectedConstructionTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        add(selectedConstructionTable).pad(2f).fill()
        background = ImageGetter.getBackground(Color.WHITE)
    }

    fun update(selectedConstruction: IConstruction?) {
        selectedConstructionTable.clear()

        if (selectedConstruction == null) {
            isVisible = false
            return
        }
        isVisible = true

        updateSelectedConstructionTable(selectedConstruction)

        pack()
    }

    private fun updateSelectedConstructionTable(construction: IConstruction) {
        val cityConstructions = city.cityConstructions

        //val selectedConstructionTable = Table()
        selectedConstructionTable.run {
            pad(10f)

            add(ImageGetter.getConstructionImage(construction.name).surroundWithCircle(50f))
                .pad(5f)

            var buildingText = construction.name.tr()
            val specialConstruction = PerpetualConstruction.perpetualConstructionsMap[construction.name]

            buildingText += specialConstruction?.getProductionTooltip(city)
                    ?: cityConstructions.getTurnsToConstructionString(construction.name)

            add(buildingText.toLabel()).row()

            val (description, link) = when (construction) {
                is BaseUnit -> construction.getDescription() to construction.makeLink()
                is Building -> construction.getDescription(city) to construction.makeLink()
                is PerpetualConstruction -> construction.description.replace("[rate]", "[${construction.getConversionRate(city)}]") to ""
                else -> "" to "" // Should never happen
            }

            val descriptionLabel = description.toLabel()
            descriptionLabel.wrap = true
            add(descriptionLabel).colspan(2).width(stage.width / 4)

            clearListeners()
            if (link.isEmpty()) return
            touchable = Touchable.enabled
            onClick {
                UncivGame.Current.setScreen(CivilopediaScreen(city.getRuleset(), cityScreen, link = link))
            }
        }
    }

}
