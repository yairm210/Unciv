package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter

class UnitSpriteSlot : NonTransformGroup() {
    var imageLocation = ""
}

class TileLayerUnitSprite(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (civilianSlot.imageLocation.isEmpty() && militarySlot.imageLocation.isEmpty()) return
        super.draw(batch, parentAlpha)
    }

    private var civilianSlot: UnitSpriteSlot = UnitSpriteSlot()
    private var militarySlot: UnitSpriteSlot = UnitSpriteSlot()

    init {
        addActor(civilianSlot)
        addActor(militarySlot)
    }

    fun getSpriteSlot(unit:MapUnit) = if (unit.isCivilian()) civilianSlot else militarySlot

    private fun showMilitaryUnit(viewingCiv: Civilization) = tileGroup.isForceVisible
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileGroup.tile)
            || !tileGroup.tile.hasEnemyInvisibleUnit(viewingCiv)

    private fun updateSlot(slot: UnitSpriteSlot, unit: MapUnit?, isShown: Boolean) {

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

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {

        val isPixelUnitsEnabled = UncivGame.Current.settings.showPixelUnits
        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        val isVisibleMilitary = viewingCiv == null || showMilitaryUnit(viewingCiv)

        val isCivilianSlotShown = isPixelUnitsEnabled && isViewable
        val isMilitarySlotShown = isPixelUnitsEnabled && isViewable && isVisibleMilitary

        updateSlot(civilianSlot, tileGroup.tile.civilianUnit, isShown = isCivilianSlotShown)
        updateSlot(militarySlot, tileGroup.tile.militaryUnit, isShown = isMilitarySlotShown)
    }

    override fun determineVisibility() {
        isVisible = civilianSlot.hasChildren() || militarySlot.hasChildren()
    }

    fun reset() {
        civilianSlot.clear()
        militarySlot.clear()

        civilianSlot.imageLocation = ""
        militarySlot.imageLocation = ""
    }
}
