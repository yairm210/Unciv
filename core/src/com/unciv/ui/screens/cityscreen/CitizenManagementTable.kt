package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.city.CityFocus
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.screens.basescreen.BaseScreen

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
            resetCell.onActivation(binding = KeyboardBinding.ResetCitizens) {
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
            avoidCell.onActivation(binding = KeyboardBinding.AvoidGrowth) {
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
                // Note the binding here only works when visible, so the main one is on CityStatsTable.miniStatsTable
                // If we bind both, both are executed - so only add the one here that re-applies the current focus
                val binding = if (city.cityAIFocus == focus) focus.binding else KeyboardBinding.None
                cell.onActivation(binding = binding) {
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
        return ExpanderTab(
            title = "{Citizen Management}",
            fontSize = Constants.defaultFontSize,
            persistenceID = "CityStatsTable.CitizenManagement",
            startsOutOpened = false,
            toggleKey = KeyboardBinding.CitizenManagement,
            onChange = onChange
        ) {
            it.add(this)
            update()
        }
    }

}
