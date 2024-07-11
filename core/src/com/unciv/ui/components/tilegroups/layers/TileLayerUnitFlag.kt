package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.UnitGroup
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** The unit flag is the synbol that appears behind the map unit - circle regularly, shield when defending, etc */
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
            newIcon = UnitGroup(unit, 30f)
            addActor(newIcon)
            setIconPosition(slot, newIcon)

            // Display air unit table for carriers/transports
            if (unit.getTile().airUnits.any { unit.isTransportTypeOf(it) } && !unit.getTile().isCityCenter()) {
                val table = getAirUnitTable(unit)
                newIcon.addActor(table)
                table.toBack()
                table.y = newIcon.height/2 - table.height/2
                table.x = newIcon.width - table.width*0.45f
            }

            // Fade out action indicator for own non-idle units
            if (unit.civ == viewingCiv && !unit.isIdle() && UncivGame.Current.settings.unitIconOpacity == 1f)
                newIcon.actionGroup?.color?.a = 0.5f

            // Fade out flag for own out-of-moves units
            if (unit.civ == viewingCiv && unit.currentMovement == 0f)
                newIcon.color.a = 0.5f * UncivGame.Current.settings.unitIconOpacity

        }

        return newIcon
    }

    private fun getAirUnitTable(unit: MapUnit): Table {

        val iconColor = unit.civ.nation.getOuterColor()
        val bgColor = unit.civ.nation.getInnerColor()

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
        table.add(unit.getTile().airUnits.size.tr().toLabel(iconColor, 10, alignment = Align.center))

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

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        clearSlots()
        fillSlots(viewingCiv)

        if (viewingCiv != null) {
            val unitsInTile = tile().getUnits()
            val shouldBeHighlighted = unitsInTile.any()
                    && unitsInTile.first().civ.isAtWarWith(viewingCiv)
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
