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
    private val numCol = 4

    fun update() {
        clear()

        val colorSelected = BaseScreen.skin.getColor("base-90")
        val colorButton = BaseScreen.skin.getColor("base-40")

        val topTable = Table() // holds 2 buttons
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
        topTable.add(resetCell).pad(3f)

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
        topTable.add(avoidCell).pad(3f)
        add(topTable).colspan(numCol).growX()
        row()

        val focusLabel = "Citizen Focus".toLabel()
        val focusCell = Table()
        focusCell.add(focusLabel).pad(5f)
        add(focusCell).colspan(numCol).growX().pad(3f)
        row()

        var currCol = numCol
        val defaultTable = Table()
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
                val binding = if (city.getCityFocus() == focus) focus.binding else KeyboardBinding.None
                cell.onActivation(binding = binding) {
                    city.setCityFocus(focus)
                    city.reassignPopulation()
                    cityScreen.update()
                }
            }
            cell.background = BaseScreen.skinStrings.getUiBackground(
                "CityScreen/CitizenManagementTable/FocusCell",
                tintColor = if (city.getCityFocus() == focus) colorSelected else colorButton
            )
            // make NoFocus and Manual their own special row
            if(focus == CityFocus.NoFocus) {
                defaultTable.add(cell).growX().pad(3f)
            } else if (focus == CityFocus.Manual) {
                defaultTable.add(cell).growX().pad(3f)
                add(defaultTable).colspan(numCol).growX()
                row()
            } else {
                cell.padTop(5f)  // Stat symbols need extra padding on top
                add(cell).growX().pad(3f)
                --currCol
                if (currCol == 0) {  // make new row
                    row()
                    currCol = numCol
                }
            }
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
