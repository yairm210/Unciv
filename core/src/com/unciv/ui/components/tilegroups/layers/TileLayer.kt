package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings

abstract class TileLayer(val tileGroup: TileGroup, val size: Float) {

    val tile: Tile = tileGroup.tile
    val strings: TileSetStrings = tileGroup.tileSetStrings

    /** Absolute X of the tile origin in the parent TileMapLayer. 0 until attachTo() is called. */
    internal var tileX: Float = 0f
    /** Absolute Y of the tile origin in the parent TileMapLayer. 0 until attachTo() is called. */
    internal var tileY: Float = 0f
    internal var parentMapLayer: TileMapLayer<*>? = null

    /** All Actor children currently owned by this tile-slot. */
    internal val ownedActors = ArrayList<Actor>(0)

    var isVisible: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            ownedActors.forEach { it.isVisible = value }
        }

    // ── scene-graph helpers ──────────────────────────────────────────────────

    protected fun addOwnedActor(actor: Actor) {
        ownedActors.add(actor)
        // If the layer is already registered in a TileMapLayer, forward there.
        // Otherwise, add directly to the TileGroup so standalone displays work
        // (map-editor icon previews, Civilopedia entries, etc.).
        if (parentMapLayer != null) parentMapLayer!!.addActor(actor)
        else tileGroup.addActor(actor)
    }

    protected fun removeOwnedActor(actor: Actor) {
        ownedActors.remove(actor)
        // parentMapLayer handles removal when attached; actor.remove() handles the
        // standalone case where the actor lives directly under tileGroup.
        if (parentMapLayer != null) parentMapLayer!!.removeActor(actor)
        else actor.remove()
    }


    // ── positioning helpers ──────────────────────────────────────────────────

    /**
     * Sets hexagon image size/origin/position/scale.
     *
     * By default the position is **absolute** (tileX + local offset) so the image can sit
     * directly in a TileMapLayer.  Pass `tileLocal = true` when the image lives inside a
     * sub-Group that is already positioned at the tile origin — in that case the image only
     * needs the local hex offset (no tileX/tileY addition).
     */
    fun Image.setHexagonSize(scale: Float? = null, tileLocal: Boolean = false): Image {
        this.setSize(tileGroup.hexagonImageWidth, this.height * tileGroup.hexagonImageWidth / this.width)
        this.setOrigin(tileGroup.hexagonImageOrigin.first, tileGroup.hexagonImageOrigin.second)
        val baseX = if (tileLocal) 0f else tileX
        val baseY = if (tileLocal) 0f else tileY
        this.x = baseX + tileGroup.hexagonImagePosition.first
        this.y = baseY + tileGroup.hexagonImagePosition.second
        this.setScale(scale ?: TileSetCache.getCurrent().config.tileScale)
        return this
    }

    fun isViewable(viewingCiv: Civilization) = tileGroup.isViewable(viewingCiv)

    fun update(viewingCiv: Civilization?) {
        doUpdate(viewingCiv)
        determineVisibility()
    }

    protected open fun determineVisibility() {
        isVisible = ownedActors.isNotEmpty()
    }

    protected abstract fun doUpdate(viewingCiv: Civilization?)

    /** Called by TileMapLayer.add() — offsets pre-buffered images from local → absolute coords. */
    internal fun attachTo(mapLayer: TileMapLayer<*>, x: Float, y: Float) {
        tileX = x
        tileY = y
        parentMapLayer = mapLayer
        for (actor in ownedActors) {
            actor.x += x
            actor.y += y
            mapLayer.addActor(actor)
        }
    }
}
