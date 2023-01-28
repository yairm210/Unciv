package com.unciv.ui.tilegroups.layers

import com.badlogic.gdx.graphics.Color
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
import com.unciv.ui.utils.extensions.addToCenter
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerX
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.setSize
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.utils.Log

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

    private fun newUnitIcon(unit: MapUnit?, isViewable: Boolean, yFromCenter: Float, viewingCiv: Civilization?): UnitGroup? {

        var newIcon: UnitGroup? = null

        if (unit != null && isViewable) {
            newIcon = UnitGroup(unit, 25f)
            addActor(newIcon)
            newIcon.center(tileGroup)
            newIcon.y += yFromCenter

            // Display air unit table for carriers/transports
            if (unit.getTile().airUnits.any { unit.isTransportTypeOf(it) } && !unit.getTile().isCityCenter())
                newIcon.addActor(getAirUnitTable(unit))

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
        val holder = Table()

        val iconColor = unit.civInfo.nation.getInnerColor()
        val bgColor = unit.civInfo.nation.getOuterColor()

        val airUnitTable = Table()
        airUnitTable.defaults().pad(3f)
        airUnitTable.background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/AirUnitTable",
            tintColor = unit.civInfo.nation.getOuterColor()
        )

        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = iconColor
        airUnitTable.add(aircraftImage).size(10f)
        airUnitTable.add(unit.getTile().airUnits.size.toString().toLabel(iconColor, 10))

        val surroundedWithCircle = airUnitTable.surroundWithCircle(20f,false, bgColor)
        surroundedWithCircle.circle.width *= 1.5f
        surroundedWithCircle.circle.centerX(surroundedWithCircle)

        holder.add(surroundedWithCircle).row()
        holder.setOrigin(Align.center)
        holder.center(tileGroup)

        return holder
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

        slot1Icon = newUnitIcon(tileGroup.tile.civilianUnit, isSlot1Shown, -20f, viewingCiv)
        slot2Icon = newUnitIcon(tileGroup.tile.militaryUnit, isSlot2Shown, 20f, viewingCiv)

    }

    fun update(viewingCiv: Civilization?) {
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
