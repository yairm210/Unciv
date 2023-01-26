package com.unciv.ui.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings

open class TileLayer(val tileGroup: TileGroup, size: Float) : Group() {

    init {
        isTransform = false
        @Suppress("LeakingThis")
        setSize(size, size)
    }

    fun tile(): Tile = tileGroup.tile
    fun strings(): TileSetStrings = tileGroup.tileSetStrings

    fun Image.setHexagonSize(scale: Float? = null): Image {
        this.setSize(tileGroup.hexagonImageWidth, this.height*tileGroup.hexagonImageWidth/this.width)
        this.setOrigin(tileGroup.hexagonImageOrigin.first, tileGroup.hexagonImageOrigin.second)
        this.x = tileGroup.hexagonImagePosition.first
        this.y = tileGroup.hexagonImagePosition.second
        this.setScale(scale ?: TileSetCache.getCurrent().config.tileScale)
        return this
    }

    fun isViewable(viewingCiv: Civilization) = tileGroup.isViewable(viewingCiv)

}
