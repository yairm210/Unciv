package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val prevIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,true)
    private val nextIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,false)
    private val unitIconHolder=Table()
    private val unitNameLabel = Label("",CameraStageBaseScreen.skin)
    private val promotionsTable = Table()
    private val unitDescriptionTable = Table(CameraStageBaseScreen.skin)
    var selectedUnit : MapUnit? = null
    var selectedCity : CityInfo? = null
    var currentlyExecutingAction : String? = null
    var lastSelectedCityButton : Boolean = false
    val deselectUnitButton = Table()

    // This is so that not on every update(), we will update the unit table.
    // Most of the time it's the same unit with the same stats so why waste precious time?
    var selectedUnitHasChanged = false
    val separator: Image

    init {
        pad(5f)

        add(Table().apply {
            add(prevIdleUnitButton)
            add(unitIconHolder)
            add(unitNameLabel).pad(5f)
            add(nextIdleUnitButton)
        }).colspan(2).row()
        separator= addSeparator().actor!!
        add(promotionsTable).colspan(2).row()
        add(unitDescriptionTable)

        deselectUnitButton.add(Label("X",CameraStageBaseScreen.skin).setFontColor(Color.WHITE)).pad(20f)
        deselectUnitButton.pack()
        deselectUnitButton.touchable = Touchable.enabled
        deselectUnitButton.onClick { selectedUnit=null; selectedCity=null; worldScreen.shouldUpdate=true }
        addActor(deselectUnitButton)
    }

    fun update() {
        if(selectedUnit!=null) {
            if (selectedUnit!!.civInfo != worldScreen.currentPlayerCiv) { // The unit that was selected, was captured. It exists but is no longer ours.
                selectedUnit = null
                selectedCity = null
                currentlyExecutingAction = null
                selectedUnitHasChanged = true
            } else if (selectedUnit!! !in selectedUnit!!.getTile().getUnits()) { // The unit that was there no longer exists}
                selectedUnit = null
                selectedCity = null
                currentlyExecutingAction = null
                selectedUnitHasChanged = true
            }
        }

        if(prevIdleUnitButton.getTilesWithIdleUnits().isNotEmpty()) { // more efficient to do this check once for both
            prevIdleUnitButton.enable()
            nextIdleUnitButton.enable()
        }
        else{
            prevIdleUnitButton.disable()
            nextIdleUnitButton.disable()
        }

        if(selectedUnit!=null) { // set texts - this is valid even when it's the same unit, because movement points and health change
            separator.isVisible=true
            val unit = selectedUnit!!
            var nameLabelText = unit.name.tr()
            if(unit.health<100) nameLabelText+=" ("+unit.health+")"
            unitNameLabel.setText(nameLabelText)

            unitDescriptionTable.clear()
            unitDescriptionTable.defaults().pad(2f).padRight(5f)
            unitDescriptionTable.add("Movement".tr())
            unitDescriptionTable.add(unit.getMovementString()).row()

            if (!unit.type.isCivilian()) {
                unitDescriptionTable.add("Strength".tr())
                unitDescriptionTable.add(unit.baseUnit().strength.toString()).row()
            }

            if (unit.baseUnit().rangedStrength!=0) {
                unitDescriptionTable.add("Ranged strength".tr())
                unitDescriptionTable.add(unit.baseUnit().rangedStrength.toString()).row()
            }

            if (!unit.type.isCivilian()) {
                unitDescriptionTable.add("XP")
                unitDescriptionTable.add(unit.promotions.XP.toString()+"/"+unit.promotions.xpForNextPromotion()).row()
            }

            if(unit.isFortified() && unit.getFortificationTurns()>0) {
                unitDescriptionTable.add("Fortification")
                unitDescriptionTable.add(""+unit.getFortificationTurns() * 20 + "%")
            }
            unitDescriptionTable.pack()

            if(unit.promotions.promotions.size != promotionsTable.children.size) // The unit has been promoted! Reload promotions!
                selectedUnitHasChanged = true
        }
        else if (selectedCity != null) {
            separator.isVisible=true
            val city = selectedCity!!
            var nameLabelText = city.name.tr()
            if(city.health<city.getMaxHealth()) nameLabelText+=" ("+city.health+")"
            unitNameLabel.setText(nameLabelText)

            unitDescriptionTable.clear()
            unitDescriptionTable.defaults().pad(2f).padRight(5f)
            unitDescriptionTable.add("Strength".tr())
            unitDescriptionTable.add(CityCombatant(city).getCityStrength().toString()).row()
            unitDescriptionTable.add("Ranged Strength".tr())
            unitDescriptionTable.add(CityCombatant(city).getAttackingStrength().toString()).row()

            selectedUnitHasChanged = true
        }
        else {
            separator.isVisible=false
            unitNameLabel.setText("")
            unitDescriptionTable.clear()
            unitIconHolder.clear()
        }

        if(!selectedUnitHasChanged) return

        unitIconHolder.clear()
        promotionsTable.clear()
        unitDescriptionTable.clearListeners()

        if(selectedUnit!=null) {
            unitIconHolder.add(UnitGroup(selectedUnit!!,30f)).pad(5f)
            for(promotion in selectedUnit!!.promotions.promotions)
                promotionsTable.add(ImageGetter.getPromotionIcon(promotion)).size(20f)

            unitDescriptionTable.onClick { worldScreen.tileMapHolder.setCenterPosition(selectedUnit!!.getTile().position) }
        }

        pack()
        selectedUnitHasChanged=false
    }

    fun citySelected(cityInfo: CityInfo) : Boolean {
        if (cityInfo == selectedCity) return false
        lastSelectedCityButton = true
        selectedCity = cityInfo
        selectedUnit = null
        selectedUnitHasChanged = true
        return true
    }

    fun tileSelected(selectedTile: TileInfo) {
        if (lastSelectedCityButton) {
            lastSelectedCityButton = false
            return
        }

        val previouslySelectedUnit = selectedUnit
        if(currentlyExecutingAction=="moveTo"){
            if(selectedUnit!!.movementAlgs()
                    .getShortestPath(selectedTile).isEmpty())
                return // can't reach there with the selected unit, watcha want me to do?

            val reachedTile = selectedUnit!!.movementAlgs().headTowards(selectedTile)

            selectedUnit!!.action=null // Disable any prior action (automation, fortification...)
            if(reachedTile!=selectedTile) // Didn't get all the way there
                selectedUnit!!.action = "moveTo " + selectedTile.position.x.toInt() + "," + selectedTile.position.y.toInt()
            currentlyExecutingAction = null
        }

        else if(selectedTile.militaryUnit!=null && selectedTile.militaryUnit!!.civInfo == worldScreen.currentPlayerCiv
                && selectedUnit!=selectedTile.militaryUnit){
            selectedUnit = selectedTile.militaryUnit
            selectedCity = null
        }
        else if (selectedTile.civilianUnit!=null && selectedTile.civilianUnit!!.civInfo == worldScreen.currentPlayerCiv
                        && selectedUnit!=selectedTile.civilianUnit){
            selectedUnit = selectedTile.civilianUnit
            selectedCity = null
        }

        if(selectedUnit != previouslySelectedUnit)
            selectedUnitHasChanged = true
    }

}

