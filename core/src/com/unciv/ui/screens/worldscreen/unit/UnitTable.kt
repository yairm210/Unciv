package com.unciv.ui.screens.worldscreen.unit

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Spy
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.*
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.pickerscreens.CityRenamePopup
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.UnitRenamePopup
import com.unciv.ui.screens.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table() {
    private val prevIdleUnitButton = IdleUnitButton(this,worldScreen.mapHolder,true)
    private val nextIdleUnitButton = IdleUnitButton(this,worldScreen.mapHolder,false)
    private val unitIconHolder = Table()
    private val unitNameLabel = "".toLabel(fontSize = 24)
    private val unitIconNameGroup = Table()
    private val promotionsTable = Table().apply { defaults().padRight(5f) }
    private val unitDescriptionTable = Table(BaseScreen.skin)

    val selectedUnit : MapUnit?
        get() = selectedUnits.firstOrNull()
    /** This is in preparation for multi-select and multi-move  */
    val selectedUnits = ArrayList<MapUnit>()

    // Whether the (first) selected unit is in unit-swapping mode
    var selectedUnitIsSwapping = false

    // Whether the (first) selected unit is in road-connecting mode
    var selectedUnitIsConnectingRoad = false

    /** Sending no unit clears the selected units entirely */
    fun selectUnit(unit: MapUnit? = null, append: Boolean = false) {
        if (!append) selectedUnits.clear()
        selectedCity = null
        if (unit != null) {
            selectedUnits.add(unit)
            unit.actionsOnDeselect()
        }
        selectedUnitIsSwapping = false
        selectedUnitIsConnectingRoad = false
        selectedSpy = null
    }

    var selectedCity : City? = null

    // This is so that not on every update(), we will update the unit table.
    // Most of the time it's the same unit with the same stats so why waste precious time?
    var selectedUnitHasChanged = false
    val separator: Actor

    var selectedSpy: Spy? = null

    fun selectSpy(spy: Spy?) {
        selectedSpy = spy
        selectedCity = null
        selectedUnits.clear()
        selectedUnitIsSwapping = false
        selectedUnitIsConnectingRoad = false
    }

    private var bg = Image(
        BaseScreen.skinStrings.getUiBackground("WorldScreen/UnitTable",
        BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
        BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)))


    init {
        pad(5f)
        touchable = Touchable.enabled
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/UnitTable", BaseScreen.skinStrings.roundedEdgeRectangleMidShape
        )
        addActor(bg)

        promotionsTable.touchable = Touchable.enabled

        val deselectUnitButton = getCloseButton(50f, 20f, Color.CLEAR, Color.RED) {
            selectUnit()
            worldScreen.shouldUpdate = true
            this@UnitTable.isVisible = false
        }
        deselectUnitButton.keyShortcuts.clear() // This is the only place we don't want the BACK keyshortcut getCloseButton assigns
        add(deselectUnitButton).left()

        add(Table().apply {
            val moveBetweenUnitsTable = Table().apply {
                add(prevIdleUnitButton)
                unitIconNameGroup.add(unitIconHolder)
                unitIconNameGroup.add(unitNameLabel).apply { Fonts.font.descent }
                unitIconHolder.touchable = Touchable.enabled
                unitNameLabel.touchable = Touchable.enabled
                add(unitIconNameGroup)
                add(nextIdleUnitButton)
            }
            add(moveBetweenUnitsTable).fill().row()

            separator = addSeparator().padBottom(5f).actor!!
            add(promotionsTable).row()
            add(unitDescriptionTable)
            touchable = Touchable.enabled
            onClick {
                val position = selectedUnit?.currentTile?.position
                    ?: selectedCity?.location
                if (position != null)
                    worldScreen.mapHolder.setCenterPosition(position, immediately = false, selectUnit = false)
            }
        }).expand()

    }

    fun update() {
        if (selectedUnit != null) {
            isVisible = true
            if (selectedUnit!!.civ != worldScreen.viewingCiv && !worldScreen.viewingCiv.isSpectator()) { // The unit that was selected, was captured. It exists but is no longer ours.
                selectUnit()
                selectedUnitHasChanged = true
            } else if (selectedUnit!! !in selectedUnit!!.getTile().getUnits()) { // The unit that was there no longer exists
                selectUnit()
                selectedUnitHasChanged = true
            }
        }

        if (worldScreen.viewingCiv.units.getIdleUnits().any()) { // more efficient to do this check once for both
            prevIdleUnitButton.enable()
            nextIdleUnitButton.enable()
        } else {
            prevIdleUnitButton.disable()
            nextIdleUnitButton.disable()
        }

        if (selectedUnit != null) { // set texts - this is valid even when it's the same unit, because movement points and health change
            if (selectedUnits.size == 1) { //single selected unit
                separator.isVisible = true
                val unit = selectedUnit!!
                val nameLabelText = buildNameLabelText(unit)
                if (nameLabelText != unitNameLabel.text.toString()) {
                    unitNameLabel.setText(nameLabelText)
                    selectedUnitHasChanged = true // We need to reload the health bar of the unit in the icon - happens e.g. when picking the Heal Instantly promotion
                }

                unitNameLabel.clearListeners()
                unitNameLabel.onClick {
                    if (!worldScreen.canChangeState) return@onClick
                    UnitRenamePopup(
                        screen = worldScreen,
                        unit = unit,
                        actionOnClose = {
                            unitNameLabel.setText(buildNameLabelText(unit))
                            selectedUnitHasChanged = true
                        }
                    )
                }

                unitDescriptionTable.clear()
                unitDescriptionTable.defaults().pad(2f)
                unitDescriptionTable.add(Fonts.movement + unit.getMovementString()).padRight(10f)

                if (!unit.isCivilian())
                    unitDescriptionTable.add(Fonts.strength + unit.baseUnit.strength.tr()).padRight(10f)

                if (unit.baseUnit.rangedStrength != 0)
                    unitDescriptionTable.add(Fonts.rangedStrength + unit.baseUnit.rangedStrength.tr()).padRight(10f)

                if (unit.baseUnit.isRanged())
                    unitDescriptionTable.add(Fonts.range + unit.getRange().tr()).padRight(10f)

                val interceptionRange = unit.getInterceptionRange()
                if (interceptionRange > 0) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("InterceptRange")).size(20f)
                    unitDescriptionTable.add(interceptionRange.tr()).padRight(10f)
                }

                if (!unit.isCivilian()) {
                    unitDescriptionTable.add("XP".toLabel().apply {
                        onClick {
                            if (selectedUnit == null) return@onClick
                            worldScreen.game.pushScreen(PromotionPickerScreen(unit))
                        }
                    })
                    unitDescriptionTable.add(unit.promotions.XP.tr() + "/" + unit.promotions.xpForNextPromotion().tr())
                }

                if (unit.baseUnit.religiousStrength > 0) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("ReligiousStrength")).size(20f)
                    unitDescriptionTable.add((unit.baseUnit.religiousStrength - unit.religiousStrengthLost).tr())
                }

                if (unit.promotions.promotions.size != promotionsTable.children.size) // The unit has been promoted! Reload promotions!
                    selectedUnitHasChanged = true
            } else { // multiple selected units
                unitNameLabel.setText("")
                unitDescriptionTable.clear()
            }
        } else if (selectedCity != null) {
            isVisible = true
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

            unitDescriptionTable.clear()
            unitDescriptionTable.defaults().pad(2f).padRight(5f)
            unitDescriptionTable.add("Strength".tr())
            unitDescriptionTable.add(CityCombatant(city).getDefendingStrength().tr()).row()
            unitDescriptionTable.add("Bombard strength".tr())
            unitDescriptionTable.add(CityCombatant(city).getAttackingStrength().tr()).row()

            selectedUnitHasChanged = true
        } else if (selectedSpy != null) {
            val spy = selectedSpy!!
            isVisible = true
            unitNameLabel.clearListeners()
            unitNameLabel.setText(spy.name)
            unitDescriptionTable.clear()

            unitIconHolder.clear()
            unitIconHolder.add (ImageGetter.getImage("OtherIcons/Spy_White").apply {
                color = Color.WHITE
            }).size(30f)

            separator.isVisible = true
            val color = when(spy.rank) {
                1 -> Color.BROWN
                2 -> Color.LIGHT_GRAY
                3 -> Color.GOLD
                else -> Color.BLACK
            }
            repeat(spy.rank) {
                val star = ImageGetter.getImage("OtherIcons/Star")
                star.color = color
                unitDescriptionTable.add(star).size(20f).pad(1f)
            }
        } else {
            isVisible = false
        }

        if (!selectedUnitHasChanged) return

        unitIconHolder.clear()
        promotionsTable.clear()
        unitDescriptionTable.clearListeners()
        separator.width = 0f  // ImageWithCustomSize remembers width and returns if when Table asks for prefWidth

        if (selectedUnit != null) {
            if (selectedUnits.size == 1) { // single selected unit
                unitIconHolder.add(UnitIconGroup(selectedUnit!!, 30f)).pad(5f)

                for (promotion in selectedUnit!!.promotions.getPromotions(true))
                    promotionsTable.add(ImageGetter.getPromotionPortrait(promotion.name, 20f)).padBottom(2f)
                
                for (status in selectedUnit!!.statuses) {
                    val group = ImageGetter.getPromotionPortrait(status.name)
                    val turnsLeft = "${status.turnsLeft}${Fonts.turn}".toLabel(fontSize = 8).surroundWithCircle(15f, color = Color.BLACK)
                    group.addActor(turnsLeft)
                    turnsLeft.setPosition(group.width, 0f, Align.bottomRight)
                    promotionsTable.add(group).padBottom(2f)
                }

                // Since Clear also clears the listeners, we need to re-add them every time
                promotionsTable.onClick {
                    if (selectedUnit == null || selectedUnit!!.promotions.promotions.isEmpty()) return@onClick
                    worldScreen.game.pushScreen(PromotionPickerScreen(selectedUnit!!))
                }

                unitIconHolder.onClick {
                    worldScreen.openCivilopedia(selectedUnit!!.baseUnit.makeLink())
                }
            } else { // multiple selected units
                for (unit in selectedUnits)
                    unitIconHolder.add(UnitIconGroup(unit, 30f)).pad(5f)
            }
        }

        pack()
        bg.setSize(width-3f, height-3f)
        bg.center(this)
        selectedUnitHasChanged = false
    }

    private fun buildNameLabelText(unit: MapUnit) : String {
        var nameLabelText = unit.displayName().tr(true)
        if (unit.health < 100) nameLabelText += " (${unit.health.tr()})"

        return nameLabelText
    }

    fun citySelected(city: City) : Boolean {
        // If the last selected unit connecting a road, keep it selected. Otherwise, clear.
        if (selectedUnitIsConnectingRoad) {
            selectUnit(selectedUnits[0])
            selectedUnitIsConnectingRoad = true // selectUnit resets this
        } else {
            selectUnit()
        }
        if (city == selectedCity) return false
        selectedCity = city
        selectedUnitHasChanged = true
        worldScreen.shouldUpdate = true
        return true
    }

    fun tileSelected(selectedTile: Tile, forceSelectUnit: MapUnit? = null) {

        val previouslySelectedUnit = selectedUnit
        val previousNumberOfSelectedUnits = selectedUnits.size

        // Do not select a different unit or city center if we click on it to swap our current unit to it
        if (selectedUnitIsSwapping && selectedUnit != null && selectedUnit!!.movement.canUnitSwapTo(selectedTile)) return
        // Do no select a different unit while in Air Sweep mode
        if (selectedUnit != null && selectedUnit!!.isPreparingAirSweep()) return

        fun MapUnit.isEligible(): Boolean = (this.civ == worldScreen.viewingCiv
                || worldScreen.viewingCiv.isSpectator()) && this !in selectedUnits

        // This is the Civ 5 Order of selection:
        // 1. City
        // 2. GP + Settlers
        // 3. Military
        // 4. Other civilian (Workers)
        // 5. None (Deselect)
        // However we deviate from it because there was a poll on Discord that clearly showed that
        // people would prefer the military unit to always be preferred over GP, so we use this:
        // 1. City
        // 2. Military
        // 3. GP + Settlers
        // 4. Other civilian (Workers)
        // 5. None (Deselect)

        val civUnit = selectedTile.civilianUnit
        val milUnit = selectedTile.militaryUnit
        val curUnit = selectedUnit

        val nextUnit: MapUnit?
        val priorityUnit = when {
            milUnit != null && milUnit.isEligible() -> milUnit
            civUnit != null && civUnit.isEligible() -> civUnit
            else -> null
        }

        nextUnit = when {
                curUnit == null -> priorityUnit
                curUnit == civUnit && milUnit != null && milUnit.isEligible() -> null
                curUnit == milUnit && civUnit != null && civUnit.isEligible() -> civUnit
                else -> priorityUnit
            }


        when {
            forceSelectUnit != null ->
                selectUnit(forceSelectUnit)
            selectedTile.isCityCenter() &&
                    (selectedTile.getOwner() == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator()) ->
                citySelected(selectedTile.getCity()!!)
            nextUnit != null -> selectUnit(nextUnit, Gdx.input.isShiftKeyPressed())
            selectedTile == previouslySelectedUnit?.currentTile -> {
                selectUnit()
                isVisible = false
            }
        }

        if (selectedUnit != previouslySelectedUnit || selectedUnits.size != previousNumberOfSelectedUnits)
            selectedUnitHasChanged = true
    }

}
