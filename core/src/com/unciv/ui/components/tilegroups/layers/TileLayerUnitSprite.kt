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

class UnitSpriteSlot {
    val spriteGroup = NonTransformGroup()
    var currentImageLocation = ""
}

class TileLayerUnitSprite(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (civilianSlot == null && militarySlot == null) return
        super.draw(batch, parentAlpha)
    }
    
    // Slots are only filled if units exist, and images for those units exist
    private var civilianSlot: UnitSpriteSlot? = null
    private var militarySlot: UnitSpriteSlot? = null


    fun getSpriteSlot(unit:MapUnit) = if (unit.isCivilian()) civilianSlot else militarySlot

    private fun showMilitaryUnit(viewingCiv: Civilization) = tileGroup.isForceVisible
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileGroup.tile)
            || !tileGroup.tile.hasEnemyInvisibleUnit(viewingCiv)

    private fun updateSlot(currentSlot: UnitSpriteSlot?, unit: MapUnit?, isShown: Boolean): UnitSpriteSlot? {

        var location = ""
        var nationName = ""

        if (unit != null && isShown && UncivGame.Current.settings.showPixelUnits) {
            location = strings().getUnitImageLocation(unit)
            nationName = "${unit.civ.civName}-"
        }

        if ((currentSlot?.currentImageLocation ?: "") == "$nationName$location") return currentSlot // No-op
        if (location == "" || !ImageGetter.imageExists(location)) return null // No such image
        
        val slot = currentSlot ?: UnitSpriteSlot()
            .apply { this@TileLayerUnitSprite.addActor(spriteGroup) }
        slot.currentImageLocation = "$nationName$location"
        slot.spriteGroup.clear()

        val nation = unit!!.civ.nation
        val pixelUnitImages = ImageGetter.getLayeredImageColored(
            location,
            null,
            nation.getInnerColor(),
            nation.getOuterColor()
        )
        for (pixelUnitImage in pixelUnitImages) {
            slot.spriteGroup.addActor(pixelUnitImage)
            pixelUnitImage.setHexagonSize()// Treat this as A TILE, which gets overlayed on the base tile.
        }
        return slot
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

        civilianSlot = updateSlot(civilianSlot, tileGroup.tile.civilianUnit, isShown = isCivilianSlotShown)
        militarySlot = updateSlot(militarySlot, tileGroup.tile.militaryUnit, isShown = isMilitarySlotShown)
    }

    override fun determineVisibility() {
        isVisible = civilianSlot != null || militarySlot != null
    }

    fun reset() {
        civilianSlot?.spriteGroup?.remove()
        militarySlot?.spriteGroup?.remove()
        civilianSlot = null
        militarySlot = null
    }
}
