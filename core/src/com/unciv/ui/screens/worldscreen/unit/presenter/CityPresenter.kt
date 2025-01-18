package com.unciv.ui.screens.worldscreen.unit.presenter

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.models.translations.tr
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.pickerscreens.CityRenamePopup
import com.unciv.ui.screens.worldscreen.unit.UnitTable

class CityPresenter(private val unitTable: UnitTable, private val unitPresenter: UnitPresenter) : UnitTable.Presenter {

    var selectedCity : City? = null

    override val position: Vector2?
        get() = selectedCity?.location

    fun selectCity(city: City) : Boolean {
        // If the last selected unit connecting a road, keep it selected. Otherwise, clear.
        unitPresenter.apply {
            if (selectedUnitIsConnectingRoad) {
                selectUnit(selectedUnits[0])
                selectedUnitIsConnectingRoad = true // selectUnit resets this
            } else {
                selectUnit()
            }
        }
        if (city == selectedCity) return false
        selectedCity = city
        return true
    }

    override fun updateWhenNeeded() = with(unitTable) {
        separator.isVisible = true
        val city = selectedCity!!
        var nameLabelText = city.name.tr()
        if (city.health < city.getMaxHealth()) nameLabelText += " (${city.health.tr()})"
        unitNameLabel.setText(nameLabelText)

        unitNameLabel.clearListeners()
        unitNameLabel.onClick {
            if (!worldScreen.canChangeState) return@onClick
            CityRenamePopup(
                screen = worldScreen,
                city = city,
                actionOnClose = {
                    unitNameLabel.setText(city.name.tr())
                    worldScreen.shouldUpdate = true
                })
        }

        descriptionTable.clear()
        descriptionTable.defaults().pad(2f).padRight(5f)
        descriptionTable.add("Strength".tr())
        descriptionTable.add(CityCombatant(city).getDefendingStrength().tr()).row()
        descriptionTable.add("Bombard strength".tr())
        descriptionTable.add(CityCombatant(city).getAttackingStrength().tr()).row()

        shouldUpdate = true

    }
}
