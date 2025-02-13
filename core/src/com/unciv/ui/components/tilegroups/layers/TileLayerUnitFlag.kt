package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** The unit flag is the synbol that appears behind the map unit - circle regularly, shield when defending, etc */
class TileLayerUnitFlag(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private var noIcons = true
    private var civilianUnitIcon: UnitIconGroup? = null
    private var militaryUnitIcon: UnitIconGroup? = null

    init {
        touchable = Touchable.disabled
    }

    override fun act(delta: Float) { // No 'snapshotting' since we trust it will remain the same
        if (noIcons)
            return
        for (child in children)
            child.act(delta)
    }

    // For perf profiling
    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (noIcons) return
        super.draw(batch, parentAlpha)
    }

    private fun clearSlots() {
        civilianUnitIcon?.remove()
        militaryUnitIcon?.remove()
    }

    private fun showMilitaryUnit(viewingCiv: Civilization) = tileGroup.isForceVisible
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileGroup.tile)
            || !tileGroup.tile.hasEnemyInvisibleUnit(viewingCiv)

    private fun setIconPosition(slot: Int, icon: UnitIconGroup) {
        if (slot == 0) {
            icon.center(this)
            icon.y += -20f
        } else if (slot == 1) {
            icon.center(this)
            icon.y += 20f
        }
    }

    private fun newUnitIcon(slot: Int, unit: MapUnit?, isViewable: Boolean, viewingCiv: Civilization?): UnitIconGroup? {

        var newIcon: UnitIconGroup? = null

        if (unit != null && isViewable) {
            newIcon = UnitIconGroup(unit, 30f)
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
            if (unit.civ == viewingCiv && !unit.hasMovement())
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
        getIcon(unit)?.selectUnit()
    }

    fun getIcon(unit: MapUnit) : UnitIconGroup? {
        if (civilianUnitIcon?.unit == unit)
            return civilianUnitIcon
        else if (militaryUnitIcon?.unit == unit)
            return militaryUnitIcon
        return null
    }

    private fun highlightRed() {
        civilianUnitIcon?.highlightRed()
        militaryUnitIcon?.highlightRed()
    }

    private fun fillSlots(viewingCiv: Civilization?) {
        val isForceVisible = viewingCiv == null || tileGroup.isForceVisible

        val isViewable = isForceVisible || isViewable(viewingCiv!!)
        val isVisibleMilitary = isForceVisible || showMilitaryUnit(viewingCiv!!)

        val isCivilianShown = isViewable
        val isMilitaryShown = isViewable && isVisibleMilitary

        civilianUnitIcon = newUnitIcon(0, tileGroup.tile.civilianUnit, isCivilianShown, viewingCiv)
        militaryUnitIcon = newUnitIcon(1, tileGroup.tile.militaryUnit, isMilitaryShown, viewingCiv)
        noIcons = civilianUnitIcon == null && militaryUnitIcon == null
    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        clearSlots()
        fillSlots(viewingCiv)

        if (viewingCiv != null) {
            val unitsInTile = tile.getUnits()
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
        noIcons = true
    }

}
