package com.unciv.ui.components.tilegroups.citybutton

import com.unciv.logic.city.City
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.BorderedTable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 *  This is the topmost badge showing number of air units above the main city button and defence badge.
 *  @param size Used both as size for the aircraft icon and font size for the [numberOfUnits] label
 */
internal class AirUnitTable(
    city: City,
    numberOfUnits: Int,
    size: Float = 14f
) : BorderedTable(
    path="WorldScreen/CityButton/AirUnitTable",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape
) {
    init {
        pad(2f, 10f, 2f, 10f)

        val nation = city.civ.nation
        val textColor = nation.getInnerColor()
        bgColor = nation.getOuterColor()
        bgBorderColor = nation.getOuterColor()

        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = textColor
        aircraftImage.setSize(size, size)

        add(aircraftImage)
        add(numberOfUnits.tr().toLabel(textColor, size.toInt()))
    }
}
