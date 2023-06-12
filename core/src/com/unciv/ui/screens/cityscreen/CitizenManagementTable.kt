package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.city.CityFocus
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel

class CitizenManagementTable(val cityScreen: CityScreen) : Table(BaseScreen.skin) {
    val city = cityScreen.city

    fun update() {
        clear()

        val colorSelected = BaseScreen.skin.getColor("selection")
        val colorButton = BaseScreen.skin.getColor("color")
        // effectively a button, but didn't want to rewrite TextButton style
        // and much more compact and can control backgrounds easily based on settings
        val resetLabel = "Reset Citizens".toLabel()
        val resetCell = Table()
        resetCell.add(resetLabel).pad(5f)
        if (cityScreen.canCityBeChanged()) {
            resetCell.touchable = Touchable.enabled
            resetCell.onClick {
                city.reassignPopulation(true)
                cityScreen.update()
            }
        }
        resetCell.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CitizenManagementTable/ResetCell",
            tintColor = colorButton
        )
        add(resetCell).colspan(2).growX().pad(3f)
        row()

        val avoidLabel = "Avoid Growth".toLabel()
        val avoidCell = Table()
        avoidCell.add(avoidLabel).pad(5f)
        if (cityScreen.canCityBeChanged()) {
            avoidCell.touchable = Touchable.enabled
            avoidCell.onClick {
                city.avoidGrowth = !city.avoidGrowth
                city.reassignPopulation()
                cityScreen.update()
            }
        }
        avoidCell.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CitizenManagementTable/AvoidCell",
            tintColor = if (city.avoidGrowth) colorSelected else colorButton
        )
        add(avoidCell).colspan(2).growX().pad(3f)
        row()

        var newRow = false
        for (focus in CityFocus.values()) {
            if (!focus.tableEnabled) continue
            if (focus == CityFocus.FaithFocus && !city.civ.gameInfo.isReligionEnabled()) continue
            val label = focus.label.toLabel()
            val cell = Table()
            cell.add(label).pad(5f)
            if (cityScreen.canCityBeChanged()) {
                cell.touchable = Touchable.enabled
                cell.onClick {
                    city.cityAIFocus = focus
                    city.reassignPopulation()
                    cityScreen.update()
                }
            }
            cell.background = BaseScreen.skinStrings.getUiBackground(
                "CityScreen/CitizenManagementTable/FocusCell",
                tintColor = if (city.cityAIFocus == focus) colorSelected else colorButton
            )
            add(cell).growX().pad(3f)
            if (newRow)  // every 2 make new row
                row()
            newRow = !newRow
        }

        pack()
    }

    fun asExpander(onChange: (() -> Unit)?): ExpanderTab {
        update()
        return ExpanderTab(
            title = "{Citizen Management}",
            fontSize = Constants.defaultFontSize,
            persistenceID = "CityStatsTable.CitizenManagement",
            startsOutOpened = false,
            content = this,
            onChange = onChange
        )
    }

}
