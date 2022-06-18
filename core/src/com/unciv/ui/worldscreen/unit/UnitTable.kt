package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaCategories
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UnitGroup
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val prevIdleUnitButton = IdleUnitButton(this,worldScreen.mapHolder,true)
    private val nextIdleUnitButton = IdleUnitButton(this,worldScreen.mapHolder,false)
    private val unitIconHolder = Table()
    private val unitNameLabel = "".toLabel()
    private val unitIconNameGroup = Table()
    private val promotionsTable = Table()
    private val unitDescriptionTable = Table(BaseScreen.skin)

    val selectedUnit : MapUnit?
        get() = selectedUnits.firstOrNull()
    /** This is in preparation for multi-select and multi-move  */
    val selectedUnits = ArrayList<MapUnit>()

    // Whether the (first) selected unit is in unit-swapping mode
    var selectedUnitIsSwapping = false

    /** Sending no unit clears the selected units entirely */
    fun selectUnit(unit:MapUnit?=null, append:Boolean=false) {
        if (!append) selectedUnits.clear()
        selectedCity = null
        if (unit != null) {
            selectedUnits.add(unit)
            unit.actionsOnDeselect()
        }
        selectedUnitIsSwapping = false
    }

    var selectedCity : CityInfo? = null
    private val deselectUnitButton = Table()

    // This is so that not on every update(), we will update the unit table.
    // Most of the time it's the same unit with the same stats so why waste precious time?
    var selectedUnitHasChanged = false
    val separator: Actor

    init {
        pad(5f)
        touchable = Touchable.enabled
        background = ImageGetter.getBackground(ImageGetter.getBlue().darken(0.5f))

        promotionsTable.touchable=Touchable.enabled

        add(VerticalGroup().apply {
            pad(5f)

            deselectUnitButton.add(ImageGetter.getImage("OtherIcons/Close")).size(20f).pad(10f)
            deselectUnitButton.pack()
            deselectUnitButton.touchable = Touchable.enabled
            deselectUnitButton.onClick { selectUnit(); worldScreen.shouldUpdate=true; this@UnitTable.isVisible=false }
            addActor(deselectUnitButton)
        }).left()

        add(Table().apply {
            val moveBetweenUnitsTable = Table().apply {
                add(prevIdleUnitButton)
                unitIconNameGroup.add(unitIconHolder)
                unitIconNameGroup.add(unitNameLabel).pad(5f)
                unitIconNameGroup.touchable = Touchable.enabled
                add(unitIconNameGroup)
                add(nextIdleUnitButton)
            }
            add(moveBetweenUnitsTable).colspan(2).fill().row()

            separator = addSeparator().actor!!
            add(promotionsTable).colspan(2).row()
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
            if (selectedUnit!!.civInfo != worldScreen.viewingCiv && !worldScreen.viewingCiv.isSpectator()) { // The unit that was selected, was captured. It exists but is no longer ours.
                selectUnit()
                selectedUnitHasChanged = true
            } else if (selectedUnit!! !in selectedUnit!!.getTile().getUnits()) { // The unit that was there no longer exists}
                selectUnit()
                selectedUnitHasChanged = true
            }
        }

        if (worldScreen.viewingCiv.getIdleUnits().any()) { // more efficient to do this check once for both
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
                var nameLabelText = unit.displayName().tr()
                if (unit.health < 100) nameLabelText += " (" + unit.health + ")"
                if (nameLabelText != unitNameLabel.text.toString()) {
                    unitNameLabel.setText(nameLabelText)
                    selectedUnitHasChanged = true // We need to reload the health bar of the unit in the icon - happens e.g. when picking the Heal Instantly promotion
                }
                unitIconNameGroup.clearListeners()
                unitIconNameGroup.onClick {
                    worldScreen.game.pushScreen(CivilopediaScreen(worldScreen.gameInfo.ruleSet, CivilopediaCategories.Unit, unit.name))
                }

                unitDescriptionTable.clear()
                unitDescriptionTable.defaults().pad(2f)
                unitDescriptionTable.add(ImageGetter.getStatIcon("Movement")).size(20f)
                unitDescriptionTable.add(unit.getMovementString()).padRight(10f)

                if (!unit.isCivilian()) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("Strength")).size(20f)
                    unitDescriptionTable.add(unit.baseUnit().strength.toString()).padRight(10f)
                }

                if (unit.baseUnit().rangedStrength != 0) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("RangedStrength")).size(20f)
                    unitDescriptionTable.add(unit.baseUnit().rangedStrength.toString()).padRight(10f)
                }

                if (unit.baseUnit.isRanged()) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("Range")).size(20f)
                    unitDescriptionTable.add(unit.getRange().toString()).padRight(10f)
                }

                if (unit.baseUnit.interceptRange > 0) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("InterceptRange")).size(20f)
                    val range = if (unit.baseUnit.isRanged()) unit.getRange() else unit.baseUnit.interceptRange
                    unitDescriptionTable.add(range.toString()).padRight(10f)
                }

                if (!unit.isCivilian()) {
                    unitDescriptionTable.add("XP")
                    unitDescriptionTable.add(unit.promotions.XP.toString() + "/" + unit.promotions.xpForNextPromotion())
                }

                if (unit.canDoReligiousAction(Constants.spreadReligionAbilityCount)) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("Faith")).size(20f)
                    unitDescriptionTable.add(unit.getActionString(Constants.spreadReligionAbilityCount))
                }

                if (unit.canDoReligiousAction(Constants.removeHeresyAbilityCount)) {
                    unitDescriptionTable.add(ImageGetter.getImage("OtherIcons/Remove Heresy")).size(20f)
                    unitDescriptionTable.add(unit.getActionString(Constants.removeHeresyAbilityCount))
                }

                if (unit.baseUnit.religiousStrength > 0) {
                    unitDescriptionTable.add(ImageGetter.getStatIcon("ReligiousStrength")).size(20f)
                    unitDescriptionTable.add((unit.baseUnit.religiousStrength - unit.religiousStrengthLost).toString())
                }

                if (unit.promotions.promotions.size != promotionsTable.children.size) // The unit has been promoted! Reload promotions!
                    selectedUnitHasChanged = true
            } else { // multiple selected units
                unitNameLabel.setText("")
                unitDescriptionTable.clear()
            }
        }

        else if (selectedCity != null) {
            separator.isVisible = true
            val city = selectedCity!!
            var nameLabelText = city.name.tr()
            if(city.health<city.getMaxHealth()) nameLabelText+=" ("+city.health+")"
            unitNameLabel.setText(nameLabelText)

            unitDescriptionTable.clear()
            unitDescriptionTable.defaults().pad(2f).padRight(5f)
            unitDescriptionTable.add("Strength".tr())
            unitDescriptionTable.add(CityCombatant(city).getDefendingStrength().toString()).row()
            unitDescriptionTable.add("Bombard strength".tr())
            unitDescriptionTable.add(CityCombatant(city).getAttackingStrength().toString()).row()

            selectedUnitHasChanged = true
        } else {
            isVisible = false
        }

        if (!selectedUnitHasChanged) return

        unitIconHolder.clear()
        promotionsTable.clear()
        unitDescriptionTable.clearListeners()

        if (selectedUnit != null) {
            if (selectedUnits.size == 1) { // single selected unit
                unitIconHolder.add(UnitGroup(selectedUnit!!, 30f)).pad(5f)

                for (promotion in selectedUnit!!.promotions.getPromotions(true))
                    promotionsTable.add(ImageGetter.getPromotionIcon(promotion.name))

                // Since Clear also clears the listeners, we need to re-add it every time
                promotionsTable.onClick {
                    if (selectedUnit == null || selectedUnit!!.promotions.promotions.isEmpty()) return@onClick
                    UncivGame.Current.pushScreen(PromotionPickerScreen(selectedUnit!!))
                }
            } else { // multiple selected units
                for (unit in selectedUnits)
                    unitIconHolder.add(UnitGroup(unit, 30f)).pad(5f)
            }
        }

        pack()
        selectedUnitHasChanged=false
    }

    fun citySelected(cityInfo: CityInfo) : Boolean {
        selectUnit()
        if (cityInfo == selectedCity) return false
        selectedCity = cityInfo
        selectedUnitHasChanged = true
        worldScreen.shouldUpdate = true
        return true
    }

    fun tileSelected(selectedTile: TileInfo, forceSelectUnit: MapUnit? = null) {

        val previouslySelectedUnit = selectedUnit
        val previousNumberOfSelectedUnits = selectedUnits.size

        // Do not select a different unit or city center if we click on it to swap our current unit to it
        if (selectedUnitIsSwapping && selectedUnit != null && selectedUnit!!.movement.canUnitSwapTo(selectedTile)) return

        when {
            forceSelectUnit != null ->
                selectUnit(forceSelectUnit)
            selectedTile.isCityCenter() &&
                    (selectedTile.getOwner() == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator()) ->
                citySelected(selectedTile.getCity()!!)
            selectedTile.militaryUnit != null &&
                    (selectedTile.militaryUnit!!.civInfo == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator()) &&
                    selectedTile.militaryUnit!! !in selectedUnits &&
                    (selectedTile.civilianUnit == null || selectedUnit != selectedTile.civilianUnit) -> // Only select the military unit there if we do not currently have the civilian unit selected
                selectUnit(selectedTile.militaryUnit!!, Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT))
            selectedTile.civilianUnit != null
                    && (selectedTile.civilianUnit!!.civInfo == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator())
                    && selectedUnit != selectedTile.civilianUnit ->
                selectUnit(selectedTile.civilianUnit!!, Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT))
            selectedTile == previouslySelectedUnit?.currentTile -> {
                // tapping the same tile again will deselect a unit.
                // important for single-tap-move to abort moving easily
                selectUnit()
                isVisible = false
            }
        }

        if (selectedUnit != previouslySelectedUnit || selectedUnits.size != previousNumberOfSelectedUnits)
            selectedUnitHasChanged = true
    }

}

