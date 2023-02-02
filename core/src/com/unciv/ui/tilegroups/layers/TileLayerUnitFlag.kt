package com.unciv.ui.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UnitGroup
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerX
import com.unciv.ui.utils.extensions.toLabel

class TileLayerUnitFlag(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private var slot1Icon: UnitGroup? = null
    private var slot2Icon: UnitGroup? = null

    init {
        touchable = Touchable.disabled
    }

    override fun act(delta: Float) { // No 'snapshotting' since we trust it will remain the same
        for (child in children)
            child.act(delta)
    }

    private fun clearSlots() {
        slot1Icon?.remove()
        slot2Icon?.remove()
    }

    private fun showMilitaryUnit(viewingCiv: Civilization) = tileGroup.isForceVisible
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileGroup.tile)
            || !tileGroup.tile.hasEnemyInvisibleUnit(viewingCiv)

    private fun setIconPosition(slot: Int, icon: UnitGroup) {
        if (slot == 0) {
            icon.center(this)
            icon.y += -20f
        } else if (slot == 1) {
            icon.center(this)
            icon.y += 20f
        }
    }

    private fun newUnitIcon(slot: Int, unit: MapUnit?, isViewable: Boolean, viewingCiv: Civilization?): UnitGroup? {

        var newIcon: UnitGroup? = null

        if (unit != null && isViewable) {
            newIcon = UnitGroup(unit, 25f)
            addActor(newIcon)
            setIconPosition(slot, newIcon)

            // Display air unit table for carriers/transports
            if (unit.getTile().airUnits.any { unit.isTransportTypeOf(it) } && !unit.getTile().isCityCenter()) {
                val table = getAirUnitTable(unit)
                addActor(table)
                table.toBack()
                table.y = newIcon.y + newIcon.height/2 - table.height/2
                table.x = newIcon.x + newIcon.width - table.width/2
            }

            // Fade out action indicator for own non-idle units
            if (unit.civInfo == viewingCiv && !unit.isIdle())
                newIcon.actionGroup?.color?.a = 0.5f * UncivGame.Current.settings.unitIconOpacity

            // Fade out flag for own out-of-moves units
            if (unit.civInfo == viewingCiv && unit.currentMovement == 0f)
                newIcon.color.a = 0.5f

        }

        return newIcon
    }

    private fun getAirUnitTable(unit: MapUnit): Table {

        val iconColor = unit.civInfo.nation.getOuterColor()
        val bgColor = unit.civInfo.nation.getInnerColor()

        val airUnitTable = Table()
        airUnitTable.background = BaseScreen.skinStrings.getUiBackground(
            path="WorldScreen/AirUnitTable",
            "", bgColor
        )
        airUnitTable.pad(0f).defaults().pad(0f)
        airUnitTable.setSize(28f, 12f)

        val table = Table()

        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = iconColor
        table.add(aircraftImage).size(8f)
        table.add(unit.getTile().airUnits.size.toString().toLabel(iconColor, 10, alignment = Align.center))

        airUnitTable.add(table).expand().center().right()

        return airUnitTable
    }

    fun selectFlag(unit: MapUnit) {
        getFlag(unit)?.selectUnit()
    }

    fun getFlag(unit: MapUnit) : UnitGroup? {
        if (slot1Icon?.unit == unit)
            return slot1Icon
        else if (slot2Icon?.unit == unit)
            return slot2Icon
        return null
    }

    private fun highlightRed() {
        slot1Icon?.highlightRed()
        slot2Icon?.highlightRed()
    }

    private fun fillSlots(viewingCiv: Civilization?) {
        val isForceVisible = viewingCiv == null || tileGroup.isForceVisible

        val isViewable = isForceVisible || isViewable(viewingCiv!!)
        val isVisibleMilitary = isForceVisible || showMilitaryUnit(viewingCiv!!)

        val isSlot1Shown = isViewable
        val isSlot2Shown = isViewable && isVisibleMilitary

        slot1Icon = newUnitIcon(0, tileGroup.tile.civilianUnit, isSlot1Shown, viewingCiv)
        slot2Icon = newUnitIcon(1, tileGroup.tile.militaryUnit, isSlot2Shown, viewingCiv)

    }

    override fun doUpdate(viewingCiv: Civilization?) {
        clearSlots()
        fillSlots(viewingCiv)

        if (viewingCiv != null) {
            val unitsInTile = tile().getUnits()
            val shouldBeHighlighted = unitsInTile.any()
                    && unitsInTile.first().civInfo.isAtWarWith(viewingCiv)
                    && isViewable(viewingCiv)
                    && showMilitaryUnit(viewingCiv)
            if (shouldBeHighlighted)
                highlightRed()
        }

    }

    fun reset() {
        clearSlots()
    }

}
