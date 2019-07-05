package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.UnCivGame
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.tr
import com.unciv.ui.CivilopediaScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val prevIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,true)
    private val nextIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,false)
    private val unitIconHolder=Table()
    private val unitNameLabel = "".toLabel()
    private val promotionsTable = Table()
    private val unitDescriptionTable = Table(CameraStageBaseScreen.skin)
    var selectedUnit : MapUnit? = null
    var selectedCity : CityInfo? = null
    val deselectUnitButton = Table()
    val helpUnitButton = Table()

    // This is so that not on every update(), we will update the unit table.
    // Most of the time it's the same unit with the same stats so why waste precious time?
    var selectedUnitHasChanged = false
    val separator: Image

    init {
        pad(5f)
        touchable = Touchable.enabled
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        add(VerticalGroup().apply {
            pad(5f)

            deselectUnitButton.add(Label("X",CameraStageBaseScreen.skin).setFontColor(Color.WHITE)).pad(10f)
            deselectUnitButton.pack()
            deselectUnitButton.touchable = Touchable.enabled
            deselectUnitButton.onClick { selectedUnit=null; selectedCity=null; worldScreen.shouldUpdate=true }
            addActor(deselectUnitButton)

            helpUnitButton.add(Label("?",CameraStageBaseScreen.skin).setFontColor(Color.WHITE)).pad(10f)
            helpUnitButton.pack()
            helpUnitButton.touchable = Touchable.enabled
            helpUnitButton.onClick {
                val pedia = CivilopediaScreen()
                if (selectedUnit != null) {
                    pedia.select("Units", selectedUnit?.name)
                }
                UnCivGame.Current.screen = pedia
            }
            addActor(helpUnitButton)

        }).left()

        add(prevIdleUnitButton)

        add(Table().apply {
            add(Table().apply {
                add(unitIconHolder)
                add(unitNameLabel).pad(5f)

            }).colspan(2).fill().row()
            separator= addSeparator().actor!!
            add(promotionsTable).colspan(2).row()
            add(unitDescriptionTable)
            touchable = Touchable.enabled
            onClick {
                selectedUnit?.currentTile?.position?.let {
                    worldScreen.tileMapHolder.setCenterPosition(it, false, false)
                }
            }
        }).expand()

        add(nextIdleUnitButton)

    }

    fun update() {
        if(selectedUnit!=null) {
            if (selectedUnit!!.civInfo != worldScreen.currentPlayerCiv) { // The unit that was selected, was captured. It exists but is no longer ours.
                selectedUnit = null
                selectedCity = null
                selectedUnitHasChanged = true
            } else if (selectedUnit!! !in selectedUnit!!.getTile().getUnits()) { // The unit that was there no longer exists}
                selectedUnit = null
                selectedCity = null
                selectedUnitHasChanged = true
            }
        }

        if(prevIdleUnitButton.hasIdleUnits()) { // more efficient to do this check once for both
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
            if(nameLabelText!=unitNameLabel.text.toString()){
                unitNameLabel.setText(nameLabelText)
                selectedUnitHasChanged=true // We need to reload the health bar of the unit in the icon - happens e.g. when picking the Heal Instantly promotion
            }


            unitDescriptionTable.clear()
            unitDescriptionTable.defaults().pad(2f)
            unitDescriptionTable.add(ImageGetter.getStatIcon("Movement")).size(20f)
            unitDescriptionTable.add(unit.getMovementString()).padRight(10f)

            if (!unit.type.isCivilian()) {
                unitDescriptionTable.add(ImageGetter.getStatIcon("Strength")).size(20f)
                unitDescriptionTable.add(unit.baseUnit().strength.toString()).padRight(10f)
            }

            if (unit.baseUnit().rangedStrength!=0) {
                unitDescriptionTable.add(ImageGetter.getStatIcon("RangedStrength")).size(20f)
                unitDescriptionTable.add(unit.baseUnit().rangedStrength.toString()).padRight(10f)
            }

            if (!unit.type.isCivilian()) {
                unitDescriptionTable.add("XP")
                unitDescriptionTable.add(unit.promotions.XP.toString()+"/"+unit.promotions.xpForNextPromotion())
            }

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
            unitDescriptionTable.add("Bombard strength".tr())
            unitDescriptionTable.add(CityCombatant(city).getAttackingStrength().toString()).row()

            selectedUnitHasChanged = true
        }
        else {
            separator.isVisible=false
            unitNameLabel.setText("")
            unitDescriptionTable.clear()
            unitIconHolder.clear()
            promotionsTable.clear()
        }

        if(!selectedUnitHasChanged) return

        unitIconHolder.clear()
        promotionsTable.clear()
        unitDescriptionTable.clearListeners()

        if(selectedUnit!=null) {
            unitIconHolder.add(UnitGroup(selectedUnit!!,30f)).pad(5f)
            for(promotion in selectedUnit!!.promotions.promotions)
                promotionsTable.add(ImageGetter.getPromotionIcon(promotion)).size(20f)

        }

        pack()
        selectedUnitHasChanged=false
    }

    fun citySelected(cityInfo: CityInfo) : Boolean {
        if (cityInfo == selectedCity) return false
        selectedCity = cityInfo
        selectedUnit = null
        selectedUnitHasChanged = true
        worldScreen.shouldUpdate = true
        return true
    }

    fun tileSelected(selectedTile: TileInfo) {

        val previouslySelectedUnit = selectedUnit
        if(selectedTile.isCityCenter() && selectedTile.getOwner()==worldScreen.currentPlayerCiv){
            citySelected(selectedTile.getCity()!!)
        }
        else if(selectedTile.militaryUnit!=null && selectedTile.militaryUnit!!.civInfo == worldScreen.currentPlayerCiv
                && selectedUnit!=selectedTile.militaryUnit
                && (selectedTile.civilianUnit==null || selectedUnit!=selectedTile.civilianUnit)){
            selectedUnit = selectedTile.militaryUnit
            selectedCity = null
        }
        else if (selectedTile.civilianUnit!=null && selectedTile.civilianUnit!!.civInfo == worldScreen.currentPlayerCiv
                        && selectedUnit!=selectedTile.civilianUnit){
            selectedUnit = selectedTile.civilianUnit
            selectedCity = null
        } else if(selectedTile == previouslySelectedUnit?.currentTile) {
            // tapping the same tile again will deselect a unit.
            // important for single-tap-move to abort moving easily
            selectedUnit = null
        }

        if(selectedUnit != previouslySelectedUnit)
            selectedUnitHasChanged = true
    }

}

