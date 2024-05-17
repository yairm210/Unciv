package com.unciv.ui.components

import com.badlogic.gdx.math.Rectangle
import com.unciv.UncivGame
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.screens.cityscreen.CityMapHolder

/**
 *  Display fireworks using the Gdx ParticleEffect system, over a map view, centered on a specific tile.
 *  - Use the [create] factory for instantiation - it handles checking the continuousRendering setting and asset existence.
 *  - Repeats endlessly
 *  - Handles the zooming and panning of the map
 *  - Intentionally exceeds the bounds of the passed (TileGroup) actor bounds
 *  @param mapHolder the CityMapHolder (or WorldMapHolder) this should draw over
 *  @property setActorBounds Informs this where, relative to the TileGroupMap that is zoomed and panned through the ZoomableScrollPane, to draw - can be constant over lifetime
 */
class ParticleEffectMapFireworks(
    private val mapHolder: ZoomableScrollPane
) : ParticleEffectFireworks() {
    companion object {
        fun create(game: UncivGame, mapScrollPane: CityMapHolder): ParticleEffectMapFireworks? {
            if (!isEnabled(game, defaultAtlasName)) return null
            return ParticleEffectMapFireworks(mapScrollPane).apply { load() }
        }
    }

    private val actorBounds = Rectangle()
    private val tempViewport = Rectangle()

    fun setActorBounds(actorX: Float, actorY: Float, actorWidth: Float, actorHeight: Float) {
        actorBounds.x = actorX
        actorBounds.y = actorY
        actorBounds.width = actorWidth
        actorBounds.height = actorHeight
    }

    override fun getScale() = mapHolder.scaleX

    override fun getTargetBounds(bounds: Rectangle) {
        // Empiric math - any attempts to ask Gdx via localToStageCoordinates were way off
        val scale = mapHolder.scaleX // just assume scaleX==scaleY
        mapHolder.getViewport(tempViewport)
        bounds.x = (actorBounds.x - tempViewport.x) * scale
        bounds.y = (actorBounds.y - tempViewport.y) * scale
        bounds.width = actorBounds.width * scale
        bounds.height = actorBounds.height * scale
    }
}
