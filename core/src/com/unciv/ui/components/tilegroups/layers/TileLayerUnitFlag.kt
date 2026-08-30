package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.view.CivView
import com.unciv.view.ForeignMapUnitView
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** The unit flag is the symbol that appears behind the map unit - circle regularly, shield when defending, etc */
class TileLayerUnitFlag(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private var civilianUnitIcon: UnitIconGroup? = null
    private var militaryUnitIcon: UnitIconGroup? = null

    private fun clearSlots() {
        civilianUnitIcon?.let { removeOwnedActor(it) }
        militaryUnitIcon?.let { removeOwnedActor(it) }
    }

    private fun setIconPosition(slot: Int, icon: UnitIconGroup) {
        // Centre horizontally; offset vertically per slot (slot 0 = bottom, slot 1 = top)
        icon.x = tileX + (size - icon.width) / 2
        icon.y = tileY + (size - icon.height) / 2 + if (slot == 1) 20f else -20f
    }

    private fun newUnitIcon(slot: Int, unit: ForeignMapUnitView?, isViewable: Boolean, viewingCiv: CivView?): UnitIconGroup? {

        var newIcon: UnitIconGroup? = null

        if (unit != null && isViewable) {
            val rawUnit = unit.getUnit()
            newIcon = UnitIconGroup(rawUnit, 30f)
            setIconPosition(slot, newIcon)
            addOwnedActor(newIcon)

            // Display air unit table for carriers/transports
            if (rawUnit.getTile().airUnits.any { rawUnit.isTransportTypeOf(it) } && !rawUnit.getTile().isCityCenter()) {
                val table = getAirUnitTable(rawUnit)
                newIcon.addActor(table)
                table.toBack()
                table.y = newIcon.height/2 - table.height/2
                table.x = newIcon.width - table.width*0.45f
            }

            // Fade out action indicator for own non-idle units
            if (rawUnit.civ === viewingCiv?.getCiv() && !rawUnit.isIdle() && UncivGame.Current.settings.unitIconOpacity == 1f)
                newIcon.actionGroup?.color?.a = 0.5f

            // Fade out flag for own out-of-moves units
            if (rawUnit.civ === viewingCiv?.getCiv() && !rawUnit.hasMovement())
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

    private fun fillSlots(viewingCiv: CivView?) {
        val isViewable = viewingCiv == null || tileGroup.isForceVisible || isViewable(viewingCiv)

        val isCivilianShown = isViewable
        val isMilitaryShown = isViewable

        civilianUnitIcon = newUnitIcon(0, tileGroup.tileView.civilianUnit, isCivilianShown, viewingCiv)
        militaryUnitIcon = newUnitIcon(1, tileGroup.tileView.militaryUnit, isMilitaryShown, viewingCiv)
    }

    override fun doUpdate(viewingCiv: CivView?) {
        clearSlots()
        fillSlots(viewingCiv)

        if (viewingCiv != null) {
            val shouldBeHighlighted = tileGroup.tileView.getVisibleUnits().any { it.civ().isAtWarWith(viewingCiv) }
                    && isViewable(viewingCiv)
            if (shouldBeHighlighted)
                highlightRed()
        }

    }

    fun reset() {
        clearSlots()
        civilianUnitIcon = null
        militaryUnitIcon = null
    }

    override fun determineVisibility() {
        isVisible = civilianUnitIcon != null || militaryUnitIcon != null
    }
}
