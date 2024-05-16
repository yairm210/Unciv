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
import com.unciv.ui.images.ImageGetter

abstract class ParticleEffectAnimation : Disposable {
    @Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
    companion object {
        const val defaultAtlasName = "Effects"
        fun isEnabled(game: UncivGame, atlasName: String = defaultAtlasName) =
            game.settings.continuousRendering && ImageGetter.getSpecificAtlas(atlasName) != null
    }

    protected data class ParticleEffectInfo(val effect: ParticleEffect, val duration: Float, val endPoint: Vector2)

    private val effectData = arrayListOf<ParticleEffectInfo>()
    private val effectsBatch: Batch = SpriteBatch()
    private val targetBounds = Rectangle()

    abstract fun getStageBounds(bounds: Rectangle)
    abstract fun getEndPoint(index: Int): Vector2
    protected open fun ParticleEffectInfo.onEffectComplete() = false
    open fun load() {}

    protected fun load(effectsFile: String, atlasName: String, count: Int, maxStartDelta: Float) {
        effectData.clear()
        val atlas = ImageGetter.getSpecificAtlas(atlasName)!!
        for (i in 0 until count) {
            val effect = ParticleEffect()
            effect.load(Gdx.files.internal(effectsFile), atlas)
            effect.setEmittersCleanUpBlendFunction(false)  // Treat it as Unknown whether the effect file changes blend state -> do it ourselves
            effect.start()
            val duration = effect.emitters.maxOf { it.duration }
            effect.update(duration * maxStartDelta * i / count)
            effectData += ParticleEffectInfo(effect, duration, getEndPoint(i))
        }
    }

    override fun dispose() {
        val effects = effectData.toList()
        effectData.clear()
        for (effect in effects) effect.effect.dispose()
    }

    fun render(delta: Float) {
        getStageBounds(targetBounds)

        effectsBatch.begin()
        for (effectInfo in effectData) {
            val (effect, duration, endPoint) = effectInfo
            effect.update(delta)
            val percent = effect.emitters.first().durationTimer / duration
            val x = targetBounds.x + percent * endPoint.x * targetBounds.width
            val y = targetBounds.y + percent * endPoint.y * targetBounds.height
            effect.setPosition(x, y)
            effect.draw(effectsBatch)
            if (effect.isComplete && effectInfo.onEffectComplete()) {
                effect.reset()
            }
        }
        effectsBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        effectsBatch.end()
    }
}
