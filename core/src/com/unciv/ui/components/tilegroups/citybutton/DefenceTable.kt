package com.unciv.ui.components.tilegroups.citybutton

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.widgets.BorderedTable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 *  This is the little badge showing city defence, just above the main button, below the ait unit incidator.
 */
internal class DefenceTable(city: City, selectedCiv: Civilization) : BorderedTable(
    path="WorldScreen/CityButton/DefenceTable",
    defaultBgShape = BaseScreen.skinStrings.roundedTopEdgeRectangleSmallShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedTopEdgeRectangleSmallBorderShape
) {
    init {
        pad(2f, 3f, 0f, 3f)

        borderSize = 4f
        bgColor = ImageGetter.CHARCOAL

        bgBorderColor = when {
            city.civ == selectedCiv -> colorFromRGB(255, 237, 200)
            city.civ.isAtWarWith(selectedCiv) -> Color.RED
            else -> ImageGetter.CHARCOAL
        }

        val cityStrength = CityCombatant(city).getDefendingStrength()
        val cityStrengthLabel = "${Fonts.strength}$cityStrength"
            .toLabel(fontSize = 12, alignment = Align.center)
        add(cityStrengthLabel).grow().center()
    }
}
