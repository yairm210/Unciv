package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings

abstract class TileLayer(val tileGroup: TileGroup, size: Float) : Group() {

    init {
        touchable = Touchable.disabled
        isTransform = false
        @Suppress("LeakingThis")
        setSize(size, size)
    }
 
    // these should not change
    val tile: Tile = tileGroup.tile
    val strings: TileSetStrings = tileGroup.tileSetStrings

    fun Image.setHexagonSize(scale: Float? = null): Image {
        this.setSize(tileGroup.hexagonImageWidth, this.height*tileGroup.hexagonImageWidth/this.width)
        this.setOrigin(tileGroup.hexagonImageOrigin.first, tileGroup.hexagonImageOrigin.second)
        this.x = tileGroup.hexagonImagePosition.first
        this.y = tileGroup.hexagonImagePosition.second
        this.setScale(scale ?: TileSetCache.getCurrent().config.tileScale)
        return this
    }

    fun isViewable(viewingCiv: Civilization) = tileGroup.isViewable(viewingCiv)

    fun update(
            viewingCiv: Civilization?,
            localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)) {
        doUpdate(viewingCiv, localUniqueCache)
        determineVisibility()
    }

    protected open fun determineVisibility() {
        isVisible = hasChildren()
    }

    protected abstract fun doUpdate(
        viewingCiv: Civilization?,
        localUniqueCache: LocalUniqueCache = LocalUniqueCache(false))

}
