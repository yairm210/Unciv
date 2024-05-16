package com.unciv.ui.components

import com.badlogic.gdx.math.Vector2
import kotlin.random.Random

abstract class ParticleEffectFireworks : ParticleEffectAnimation() {
    @Suppress("ConstPropertyName")
    companion object {
        const val effectsFile = "effects/fireworks.p"

        private val endPoints = arrayOf(
            Vector2(0f, 1.6f),
            Vector2(-0.5f, 1.1f),
            Vector2(0.5f, 1.1f),
        )
    }

    override fun load() {
        load(effectsFile, defaultAtlasName, 3, 0.5f)
    }

    override fun getEndPoint(index: Int) = endPoints[index % endPoints.size]

    override fun ParticleEffectInfo.onEffectComplete(): Boolean {
        endPoint.set(Random.nextFloat() * 0.7f - 0.35f, 0.75f + Random.nextFloat() * 1.2f)
        return true
    }
}
