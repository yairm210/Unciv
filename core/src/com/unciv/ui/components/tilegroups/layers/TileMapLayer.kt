package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable

/** Container holding all [TileLayer] instances of one subtype across the whole map,
 *  forming a single rendering/interaction layer in [com.unciv.ui.components.tilegroups.TileGroupMap].
 *
 *  Keeping each layer type in its own Group lets [com.unciv.ui.components.tilegroups.TileGroupMap]
 *  short-circuit [hit] and [act] at the layer boundary instead of iterating every tile actor:
 *  non-interactive layers have [touchable] = [Touchable.disabled] so [hit] returns null without
 *  recursing into N children; [act] is suppressed unless [actable] is true. */
class TileMapLayer<T : TileLayer>(
    initialCapacity: Int,
    private val actable: Boolean = false,
    touchable: Boolean = false,
) : Group() {

    val tileLayers = ArrayList<T>(initialCapacity)

    init {
        isTransform = false
        this.touchable = if (touchable) Touchable.childrenOnly else Touchable.disabled
        children.ensureCapacity(initialCapacity * 2) // rough estimate: ~2 images/tile on average
    }

    /** Register a tile-layer and flush its buffered images into this Group. */
    fun add(layer: T, tileX: Float, tileY: Float) {
        tileLayers.add(layer)
        layer.attachTo(this, tileX, tileY)
    }

    override fun act(delta: Float) {
        if (actable) super.act(delta)
    }
}
