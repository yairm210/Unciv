package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle

class ConstructionInfoTable(val cityScreen: CityScreen): Table() {
    private val selectedConstructionTable = Table()
    val city = cityScreen.city

    init {
        selectedConstructionTable.background = ImageGetter.getBackground(ImageGetter.getBlue().darken(0.5f))
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

            add(Label(buildingText, BaseScreen.skin)).row()  // already translated

            val description = when (construction) {
                is BaseUnit -> construction.getDescription(city)
                is Building -> construction.getDescription(city, true)
                is PerpetualConstruction -> construction.description.replace("[rate]", "[${construction.getConversionRate(city)}]").tr()
                else -> ""  // Should never happen
            }

            val descriptionLabel = Label(description, BaseScreen.skin)  // already translated
            descriptionLabel.wrap = true
            add(descriptionLabel).colspan(2).width(stage.width / 4)

            clearListeners()
            val link = (construction as? RulesetObject)?.makeLink() ?: return
            if (link.isEmpty()) return
            touchable = Touchable.enabled
            onClick {
                UncivGame.Current.setScreen(CivilopediaScreen(city.getRuleset(), cityScreen, link = link))
            }
        }
    }

}
