package com.unciv.ui.components.tilegroups.citybutton

import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.BorderedTable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.ForeignCityView

/**
 *  This is the topmost badge showing number of air units above the main city button and defence badge.
 *  @param size Used both as size for the aircraft icon and font size for the [numberOfUnits] label
 */
internal class AirUnitTable(
    cityView: ForeignCityView,
    numberOfUnits: Int,
    size: Float = 14f
) : BorderedTable(
    path="WorldScreen/CityButton/AirUnitTable",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape
) {
    init {
        pad(2f, 10f, 2f, 10f)

        val textColor = cityView.getCivInnerColor()
        bgColor = cityView.getNationOuterColor()
        bgBorderColor = cityView.getNationOuterColor()

        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = textColor
        aircraftImage.setSize(size, size)

        add(aircraftImage)
        add(numberOfUnits.tr().toLabel(textColor, size.toInt()))
    }
}
