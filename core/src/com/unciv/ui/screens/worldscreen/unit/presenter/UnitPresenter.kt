package com.unciv.ui.screens.worldscreen.unit.presenter

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.UnitRenamePopup
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.unit.UnitTable

class UnitPresenter(private val unitTable: UnitTable, private val worldScreen: WorldScreen) : UnitTable.Presenter {

    val selectedUnit : MapUnit?
        get() = selectedUnits.firstOrNull()
    
    /** This is in preparation for multi-select and multi-move  */
    val selectedUnits = ArrayList<MapUnit>()

    // Whether the (first) selected unit is in unit-swapping mode
    var selectedUnitIsSwapping = false

    // Whether the (first) selected unit is in road-connecting mode
    var selectedUnitIsConnectingRoad = false

    override val position: Vector2?
        get() = selectedUnit?.currentTile?.position

    fun selectUnit(unit: MapUnit? = null, append: Boolean = false) {
        if (!append) selectedUnits.clear()
        if (unit != null) {
            selectedUnits.add(unit)
            unit.actionsOnDeselect()
        }
        selectedUnitIsSwapping = false
        selectedUnitIsConnectingRoad = false
    }

    override fun update() = selectedUnit?.let { unit ->
        // The unit that was selected, was captured. It exists but is no longer ours.
        val captured = unit.civ != worldScreen.viewingCiv && !worldScreen.viewingCiv.isSpectator()
        // The unit that was there no longer exists
        val disappeared = unit !in unit.getTile().getUnits()
        if (captured || disappeared) {
            unitTable.selectUnit()
            worldScreen.shouldUpdate = true
            return
        }

        // set texts - this is valid even when it's the same unit, because movement points and health change
        // single selected unit
        if (selectedUnits.size == 1) with(unitTable) { 
            separator.isVisible = true
            nameLabelText = buildNameLabelText(unit)
            unitNameLabel.clearListeners()
            unitNameLabel.onClick {
                if (!worldScreen.canChangeState) return@onClick
                UnitRenamePopup(
                    screen = worldScreen,
                    unit = unit,
                    actionOnClose = {
                        unitNameLabel.setText(buildNameLabelText(unit))
                        shouldUpdate = true
                    }
                )
            }

            descriptionTable.clear()
            descriptionTable.defaults().pad(2f)
            descriptionTable.add(Fonts.movement + unit.getMovementString()).padRight(10f)

            if (!unit.isCivilian())
                descriptionTable.add(Fonts.strength + unit.baseUnit.strength.tr()).padRight(10f)

            if (unit.baseUnit.rangedStrength != 0)
                descriptionTable.add(Fonts.rangedStrength + unit.baseUnit.rangedStrength.tr()).padRight(10f)

            if (unit.baseUnit.isRanged())
                descriptionTable.add(Fonts.range + unit.getRange().tr()).padRight(10f)

            val interceptionRange = unit.getInterceptionRange()
            if (interceptionRange > 0) {
                descriptionTable.add(ImageGetter.getStatIcon("InterceptRange")).size(20f)
                descriptionTable.add(interceptionRange.tr()).padRight(10f)
            }

            if (!unit.isCivilian()) {
                descriptionTable.add("XP".toLabel().apply {
                    onClick {
                        if (selectedUnit == null) return@onClick
                        worldScreen.game.pushScreen(PromotionPickerScreen(unit))
                    }
                })
                descriptionTable.add(unit.promotions.XP.tr() + "/" + unit.promotions.xpForNextPromotion().tr())
            }

            if (unit.baseUnit.religiousStrength > 0) {
                descriptionTable.add(ImageGetter.getStatIcon("ReligiousStrength")).size(20f)
                descriptionTable.add((unit.baseUnit.religiousStrength - unit.religiousStrengthLost).tr())
            }

            if (unit.promotions.promotions.size != promotionsTable.children.size) // The unit has been promoted! Reload promotions!
                shouldUpdate = true
        } else with(unitTable) { // multiple selected units
            nameLabelText = ""
            descriptionTable.clear()
        }

    } ?: Unit

    override fun updateWhenNeeded() = selectedUnit?.let { unit ->
        // single selected unit
        if (selectedUnits.size == 1) with(unitTable) {
            
            unitIconHolder.add(UnitIconGroup(unit, 30f)).pad(5f)

            for (promotion in unit.promotions.getPromotions(true))
                promotionsTable.add(ImageGetter.getPromotionPortrait(promotion.name, 20f))
                    .padBottom(2f)

            for (status in unit.statuses) {
                val group = ImageGetter.getPromotionPortrait(status.name)
                val turnsLeft = "${status.turnsLeft}${Fonts.turn}".toLabel(fontSize = 8)
                    .surroundWithCircle(15f, color = ImageGetter.CHARCOAL)
                group.addActor(turnsLeft)
                turnsLeft.setPosition(group.width, 0f, Align.bottomRight)
                promotionsTable.add(group).padBottom(2f)
            }

            // Since Clear also clears the listeners, we need to re-add them every time
            promotionsTable.onClick {
                if (selectedUnit == null || unit.promotions.promotions.isEmpty()) return@onClick
                worldScreen.game.pushScreen(PromotionPickerScreen(unit))
            }

            unitIconHolder.onClick {
                worldScreen.openCivilopedia(unit.baseUnit.makeLink())
            }
        } else { // multiple selected units
            for (selectedUnit in selectedUnits)
                unitTable.unitIconHolder.add(UnitIconGroup(selectedUnit, 30f)).pad(5f)
        }
        Unit
    } ?: Unit

    private fun buildNameLabelText(unit: MapUnit) : String {
        var nameLabelText = unit.displayName().tr(true)
        if (unit.health < 100) nameLabelText += " (${unit.health.tr()})"
        return nameLabelText
    }

}
