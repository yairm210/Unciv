package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.tilegroups.TileGroup

private class UnitArtSlot : Group() {
    var imageLocation = ""
}

class TileLayerUnitArt(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null

    private var slot1: UnitArtSlot = UnitArtSlot()
    private var slot2: UnitArtSlot = UnitArtSlot()

    init {
        addActor(slot1)
        addActor(slot2)
    }

    private fun showMilitaryUnit(viewingCiv: Civilization) = tileGroup.isForceVisible
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileGroup.tile)
            || !tileGroup.tile.hasEnemyInvisibleUnit(viewingCiv)

    private fun updateSlot(slot: UnitArtSlot, unit: MapUnit?, isShown: Boolean) {

        var location = ""
        var nationName = ""

        if (unit != null && isShown && UncivGame.Current.settings.showPixelUnits) {
            location = strings().getUnitImageLocation(unit)
            nationName = "${unit.civ.civName}-"
        }

        if (slot.imageLocation != "$nationName$location") {
            slot.imageLocation = "$nationName$location"
            slot.clear()

            if (location != "" && ImageGetter.imageExists(location)) {
                val nation = unit!!.civ.nation
                val pixelUnitImages = ImageGetter.getLayeredImageColored(
                    location,
                    null,
                    nation.getInnerColor(),
                    nation.getOuterColor()
                )
                for (pixelUnitImage in pixelUnitImages) {
                    slot.addActor(pixelUnitImage)
                    pixelUnitImage.setHexagonSize()// Treat this as A TILE, which gets overlayed on the base tile.
                }
            }
        }
    }

    fun dim() {
        color.a = 0.5f
    }

    override fun doUpdate(viewingCiv: Civilization?) {

        val slot1Unit = tileGroup.tile.civilianUnit
        val slot2Unit = tileGroup.tile.militaryUnit

        val isPixelUnitsEnabled = UncivGame.Current.settings.showPixelUnits
        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        val isVisibleMilitary = viewingCiv == null || showMilitaryUnit(viewingCiv)

        val isSlot1Shown = isPixelUnitsEnabled && isViewable
        val isSlot2Shown = isPixelUnitsEnabled && isViewable && isVisibleMilitary

        updateSlot(slot1, slot1Unit, isShown = isSlot1Shown)
        updateSlot(slot2, slot2Unit, isShown = isSlot2Shown)
    }

    override fun determineVisibility() {
        isVisible = slot1.hasChildren() || slot2.hasChildren()
    }

    fun reset() {
        slot1.clear()
        slot2.clear()

        slot1.imageLocation = ""
        slot2.imageLocation = ""
    }
}
