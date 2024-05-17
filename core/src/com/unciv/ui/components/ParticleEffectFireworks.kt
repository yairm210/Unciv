package com.unciv.ui.components

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import kotlin.random.Random

abstract class ParticleEffectFireworks : ParticleEffectAnimation() {
    @Suppress("ConstPropertyName")
    companion object {
        const val effectsFile = "effects/fireworks.p"

        private val initialEndPoints = arrayOf(
            Vector2(0.5f, 1.5f),
            Vector2(0.2f, 1f),
            Vector2(0.8f, 1f),
        )
        private val startPoint = Vector2(0.5f, 0f)

        // These define the range of random endPoints and thus how far out of the offered Actor bounds the rockets can fly
        private const val amplitudeX = 1f
        private const val offsetX = 0f
        private const val amplitudeY = 1f
        private const val offsetY = 0.5f
        // Duration of tracer effect taken from the definition file
        private const val travelTime = 0.6f
        // Delay between initial effects
        private const val initialDelay = travelTime
        // Max delay for next effect
        private const val amplitudeDelay = 0.25f
        // These two determine how often onComplete will ask for an extra restart or omit one
        private const val minEffects = 3
        private const val maxEffects = 5
    }

    fun load() {
        load(effectsFile, defaultAtlasName, initialEndPoints.size)
    }

    override fun ParticleEffectData.configure() {
        startPoint = Companion.startPoint
        endPoint = if (index in initialEndPoints.indices) initialEndPoints[index]
            else Vector2(offsetX + amplitudeX * Random.nextFloat(), offsetY + amplitudeY * Random.nextFloat())
        delay = if (index in initialEndPoints.indices) index * initialDelay
            else Random.nextFloat() * amplitudeDelay
        travelTime = Companion.travelTime
        interpolation = Interpolation.fastSlow

        // The file definition has a whole bunch of "explosions" - a "rainbow" and six "shower-color" ones.
        // Show either "rainbow" alone or a random selection of "shower" emitters.
        // It also has some "dazzler" emitters that shouldn't be included in most runs.
        val type = Random.nextInt(-1, 5)
        if (type < 0) {
            // Leave only rainbow emitter
            effect.removeEmitters { it.startsWith("shower") }
        } else {
            // remove rainbow emitter and [type] "shower-color" emitters
            val names = effect.emitters.asSequence()
                .map { it.name }.filter { it.startsWith("shower") }
                .shuffled().take(type)
                .toSet() + "rainbow"
            effect.removeEmitters { it in names }
        }
        if (Random.nextInt(4) > 0)
            effect.removeEmitters { it.startsWith("dazzler") }
    }

    override fun onComplete(effectData: ParticleEffectData): Int {
        if (Random.nextInt(4) > 0) return 1
        if (activeCount() <= minEffects) return Random.nextInt(1, 3)
        if (activeCount() >= maxEffects) return Random.nextInt(0, 2)
        return Random.nextInt(0, 3)
    }
}
