package com.unciv.ui.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.cityscreen.CityMapHolder
import kotlin.random.Random

//todo Find correct placement formula
//todo Kdoc
//todo random Colors - I see only blue and red?
//todo The spark shower seems to happen where the rocket started, not where it ends
//todo spark shower timing - before or after "explosion"?

class ParticleEffectFireworks(
    private val mapHolder: ZoomableScrollPane
) : Disposable {
    private data class ParticleEffectData(val effect: ParticleEffect, val duration: Float, val endPoint: Vector2)

    private val fireworks = arrayListOf<ParticleEffectData>()
    private lateinit var effectsBatch: Batch
    private val actorBounds = Rectangle()
    private val tempViewport = Rectangle()

    fun update(virtualWidth: Float, virtualHeight: Float, actorX: Float, actorY: Float, actorWidth: Float, actorHeight: Float) {
        fireworks.clear()
        val atlas = ImageGetter.getSpecificAtlas("Effects")!!
        if (!::effectsBatch.isInitialized) effectsBatch = SpriteBatch()

        val endPoints = arrayOf(
            Vector2(0f, 1.6f),
            Vector2(-0.5f, 1.1f),
            Vector2(0.5f, 1.1f),
        )
        for (i in 0..2) {
            val effect = ParticleEffect()
            effect.load(Gdx.files.internal("effects/fireworks.p"), atlas)
            effect.emitters.forEach {
                it.setPosition(virtualWidth / 2 + i * virtualWidth / 4, virtualHeight)
            }
            effect.setEmittersCleanUpBlendFunction(false)  // Treat it as Unknown whether the effect file changes blend state -> do it ourselves
            effect.start()
            val duration = effect.emitters.maxOf { it.duration }
            effect.update(duration * i / 4)
            fireworks.add(ParticleEffectData(effect, duration, endPoints[i]))
        }
        actorBounds.x = actorX
        actorBounds.y = actorY
        actorBounds.width = actorWidth
        actorBounds.height = actorHeight
    }

    override fun dispose() {
        val effects = fireworks.toList()
        fireworks.clear()
        for (effect in effects) effect.effect.dispose()
    }

    fun render(delta: Float) {
        // Empiric math - any attempts to ask Gdx via localToStageCoordinates were way off
        val scale = mapHolder.scaleX // just assume scaleX==scaleY
        mapHolder.getViewport(tempViewport)
        val stageX = (actorBounds.x - tempViewport.x) * scale
        val stageY = (actorBounds.y - tempViewport.y) * scale
        val stageWidth = actorBounds.width * scale
        val stageHeight = actorBounds.height * scale

        effectsBatch.begin()
        for ((effect, duration, endPoint) in fireworks) {
            effect.update(delta)
            val percent = effect.emitters.first().durationTimer / duration
            val x = stageX + percent * endPoint.x * stageWidth
            val y = stageY + percent * endPoint.y * stageHeight
            effect.setPosition(x, y)
            effect.draw(effectsBatch)
            if (effect.isComplete) {
                effect.reset()
                endPoint.set(Random.nextFloat() * 0.7f - 0.35f, 0.75f + Random.nextFloat() * 1.2f)
            }
        }
        effectsBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        effectsBatch.end()
    }

    companion object {
        fun create(game: UncivGame, mapScrollPane: CityMapHolder): ParticleEffectFireworks? {
            if (!game.settings.continuousRendering || ImageGetter.getSpecificAtlas("Effects") == null) return null
            return ParticleEffectFireworks(mapScrollPane)
        }
    }
}
