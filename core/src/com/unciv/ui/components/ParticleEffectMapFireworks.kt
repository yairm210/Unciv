package com.unciv.ui.components

import com.badlogic.gdx.math.Rectangle
import com.unciv.UncivGame
import com.unciv.ui.components.tilegroups.CityTileGroup
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.screens.cityscreen.CityMapHolder

// todo sound
// todo moddability (refactor media search first)

/**
 *  Display fireworks using the Gdx ParticleEffect system, over a map view, centered on a specific tile.
 *  - Use the [create] factory for instantiation - it handles checking the continuousRendering setting and asset existence.
 *  - Repeats endlessly
 *  - Handles the zooming and panning of the map
 *  - Intentionally exceeds the bounds of the passed (TileGroup) actor bounds, but not by much
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

    // The factors below are just fine-tuning the looks, and avoid lengthy particle effect file edits
    fun setActorBounds(tileGroup: CityTileGroup) {
        tileGroup.run { actorBounds.set(x + (width - hexagonImageWidth) / 2, y + height / 4, hexagonImageWidth, height * 1.667f) }
    }

    override fun getScale() = mapHolder.scaleX * 0.667f

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
