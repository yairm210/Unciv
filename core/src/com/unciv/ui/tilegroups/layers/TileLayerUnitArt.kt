package com.unciv.ui.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.tilegroups.TileGroup

class TileLayerUnitArt(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null

    private var locations = Array(2){""}

    init {
        addActor(Group())
        addActor(Group())
    }

    private fun showMilitaryUnit(viewingCiv: Civilization) = tileGroup.isForceVisible
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileGroup.tile)
            || !tileGroup.tile.hasEnemyInvisibleUnit(viewingCiv)

    private fun updateSlot(index: Int, unit: MapUnit?, isShown: Boolean) {

        var location = ""
        var nationName = ""

        if (unit != null && isShown && UncivGame.Current.settings.showPixelUnits) {
            location = strings().getUnitImageLocation(unit)
            nationName = "${unit.civInfo.civName}-"
        }

        if (locations[index] != "$nationName$location") {
            locations[index] = "$nationName$location"
            val group: Group = getChild(index) as Group
            group.clear()

            if (location != "" && ImageGetter.imageExists(location)) {
                val nation = unit!!.civInfo.nation
                val pixelUnitImages = ImageGetter.getLayeredImageColored(
                    location,
                    null,
                    nation.getInnerColor(),
                    nation.getOuterColor()
                )
                for (pixelUnitImage in pixelUnitImages) {
                    group.addActor(pixelUnitImage)
                    pixelUnitImage.setHexagonSize()// Treat this as A TILE, which gets overlayed on the base tile.
                }
            }
        }
    }

    fun dim() {
        color.a = 0.5f
    }

    fun update(viewingCiv: Civilization?) {

        val slot1Unit = tileGroup.tile.civilianUnit
        val slot2Unit = tileGroup.tile.militaryUnit

        val isPixelUnitsEnabled = UncivGame.Current.settings.showPixelUnits
        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        val isVisibleMilitary = viewingCiv == null || showMilitaryUnit(viewingCiv)

        val isSlot1Shown = isPixelUnitsEnabled && isViewable
        val isSlot2Shown = isPixelUnitsEnabled && isViewable && isVisibleMilitary

        updateSlot(0, slot1Unit, isShown = isSlot1Shown)
        updateSlot(1, slot2Unit, isShown = isSlot2Shown)
    }

    fun reset() {
        updateSlot(0, null, false)
        updateSlot(1, null, false)
    }
}
