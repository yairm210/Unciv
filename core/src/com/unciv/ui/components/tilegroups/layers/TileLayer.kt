package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.view.CivView
import com.unciv.logic.map.tile.Tile
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings

abstract class TileLayer(val tileGroup: TileGroup, val size: Float) {

    val strings: TileSetStrings = tileGroup.tileSetStrings

    /** Absolute X of the tile origin in the parent TileMapLayer. 0 until attachTo() is called. */
    internal var tileX: Float = 0f
    /** Absolute Y of the tile origin in the parent TileMapLayer. 0 until attachTo() is called. */
    internal var tileY: Float = 0f
    internal var parentMapLayer: TileMapLayer<*>? = null

    /** All Actor children currently owned by this tile-slot. 
     * Lazily initialized — most layers never have actors added during TileGroup construction */
    private var _ownedActors: ArrayList<Actor>? = null
    internal val ownedActors: ArrayList<Actor>
        get() = _ownedActors ?: ArrayList<Actor>().also { _ownedActors = it }

    var isVisible: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            forEachOwnedActor { it.isVisible = value }
        }

    // ── scene-graph helpers ──────────────────────────────────────────────────

    /** Iterates owned actors without triggering lazy initialization when the list is empty. */
    internal fun forEachOwnedActor(action: (Actor) -> Unit) {
        _ownedActors?.forEach(action)
    }

    protected fun addOwnedActor(actor: Actor) {
        ownedActors.add(actor)
        // If the layer is already registered in a TileMapLayer, forward there.
        // Otherwise, add directly to the TileGroup so standalone displays work
        // (map-editor icon previews, Civilopedia entries, etc.).
        if (parentMapLayer != null) parentMapLayer!!.addActor(actor)
        else tileGroup.addActor(actor)
    }

    protected fun removeOwnedActor(actor: Actor) {
        if (_ownedActors == null) return
        if (!_ownedActors!!.remove(actor)) return
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
        this.setOrigin(tileGroup.hexagonImageOriginX, tileGroup.hexagonImageOriginY)
        val baseX = if (tileLocal) 0f else tileX
        val baseY = if (tileLocal) 0f else tileY
        this.x = baseX + tileGroup.hexagonImagePosition.first
        this.y = baseY + tileGroup.hexagonImagePosition.second
        this.setScale(scale ?: TileSetCache.getCurrent().config.tileScale)
        return this
    }

    fun isViewable(viewingCiv: CivView?) = viewingCiv == null || tileGroup.isViewable(viewingCiv)

    /** Hash of everything [doUpdate] reads. If it is unchanged since the last update, [doUpdate] is skipped.
     *  `null` means "unknown" - always update. Must cover ALL inputs of [doUpdate], or the layer will go stale. */
    protected open fun getUpdateKey(viewingCiv: CivView?): Long? = null
    private var lastUpdateKey: Long? = null

    /** Forces the next [update] to run [doUpdate], e.g. after something mutated our actors outside of [doUpdate] */
    fun invalidate() { lastUpdateKey = null }

    fun update(viewingCiv: CivView?) {
        val key = getUpdateKey(viewingCiv)
        if (key == null || key != lastUpdateKey) {
            doUpdate(viewingCiv)
            lastUpdateKey = key
        }
        determineVisibility()
    }

    protected fun Long.mix(value: Int): Long = this * 31 + value
    protected fun Long.mix(value: Boolean): Long = mix(if (value) 1 else 0)
    protected fun Long.mix(value: Any?): Long = mix(value?.hashCode() ?: 0)

    protected open fun determineVisibility() {
        isVisible = _ownedActors?.isNotEmpty() == true
    }

    protected abstract fun doUpdate(viewingCiv: CivView?)

    /** Called by TileMapLayer.add() — offsets pre-buffered images from local → absolute coords. */
    internal fun attachTo(mapLayer: TileMapLayer<*>, x: Float, y: Float) {
        tileX = x
        tileY = y
        parentMapLayer = mapLayer
        forEachOwnedActor { actor ->
            actor.x += x
            actor.y += y
            mapLayer.addActor(actor)
        }
    }
}
