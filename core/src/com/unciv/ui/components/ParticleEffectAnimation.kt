package com.unciv.ui.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.ParticleEmitter
import com.badlogic.gdx.graphics.g2d.ParticleEmitter.RangedNumericValue
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.images.ImageGetter

/** Hosts one template ParticleEffect and any number of clones, and each can travel linearly over part of its lifetime.
 *  @property load Must be called from a subclass at least once - see detailed Kdoc
 *  @property configure Optionally modifies all clones - see detailed Kdoc
 *  @property getTargetBounds Where to draw on the stage - see detailed Kdoc
 *  @property getScale Optionally scales all effects - see detailed Kdoc
 *  @property render Needs to be called from the hosting Screen in its render override
 *  @property dispose Required - this is just the Gdx Disposable interface, no automatic call
 */
abstract class ParticleEffectAnimation : Disposable {
    @Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
    companion object {
        const val defaultAtlasName = "ConstructionIcons"

        fun isEnabled(game: UncivGame, atlasName: String = defaultAtlasName) =
            GameSettings.Animations.WLTKFireworks in game.settings.enabledAnimations &&
            ImageGetter.getSpecificAtlas(atlasName) != null

        private val halfPoint = Vector2(0.5f, 0.5f)
    }

    private val templateEffect = ParticleEffect()
    /** Unit: ms */
    private var maxDuration: Float = 0f
    // val pool: ParticleEffectPool - can't, since we're filtering emitters by removing them, we need a fresh clone for every repetition. A pool won't give fresh clones...
    private var nextIndex = 0

    /**
     *  Represents one currently running effect
     *  - Points are relative to the bounds provided by [getTargetBounds], and would normally stay in the (0..1) range. They default to (0.5,0.5).
     *  - Time units are seconds (while Gdx [ParticleEffect] uses milliseconds), matching [render] delta.
     *  @property index A sequential counter, just in case a [configure] or [onComplete] needs some handle to differentiate parallel or repeated effects. Starts at 0.
     *  @property effect One clone of the template effect [ParticleEffectAnimation] loads (use e.g. [removeEmitters] to modify)
     *  @property startPoint If the effect should travel over time, this is the start point.
     *  @property endPoint If the effect should travel over time, this is the end point.
     *  @property delay This is the initial delay before travel starts. Defaults to 0, when >0 all emitters in the effect get this added to their own delay value.
     *  @property travelTime This is the travel duration - after this is up, the effect stays at [endPoint]. Defaults to the maximum (duration+delay) of all emitters.
     *  @property interpolation Applied to travel time percentage
     *  @property accumulatedTime For [render] to accumulate time in, as effect has none, and individual emitters do not accumulate in a readable way over the entire effect duration.
     */
    protected data class ParticleEffectData(
        val index: Int,
        val effect: ParticleEffect,
        var startPoint: Vector2 = halfPoint,
        var endPoint: Vector2 = halfPoint,
        var delay: Float = 0f,
        var travelTime: Float,
        var interpolation: Interpolation = Interpolation.linear
    ) {
        private var accumulatedTime = 0f
        private var percent = 0f
        fun update(delta: Float) {
            accumulatedTime += delta
            val rawPercent = (accumulatedTime - delay) / travelTime
            percent = interpolation.apply(rawPercent.coerceIn(0f, 1f))
        }
        fun currentX() = startPoint.x + percent * (endPoint.x - startPoint.x)
        fun currentY() = startPoint.y + percent * (endPoint.y - startPoint.y)
    }

    private val activeEffectData = arrayListOf<ParticleEffectData>()
    //private val effectsBatch: Batch = SpriteBatch()
    private val targetBounds = Rectangle()
    private var lastScale = 1f

    /** Fetch where to draw in stage coordinates
     *  - Implementation should ***set*** the fields of the provided rectangle instance.
     *  - Note the bounds do not limit the effects themselves, but determine effect travel over time.
     *  - Actual size of the effects are determined by the effect definition and [getScale].
     *  - For stationary effects, width=0 and height=0 are perfectly acceptable.
     */
    abstract fun getTargetBounds(bounds: Rectangle)

    /** Return how the effects should be scaled relative to their definition (which already uses world coordinates).
     *  - Changing this while the effects are rendering is relatively expensive
     */
    protected open fun getScale() = 1f

    /** Allows the subclass to change an effect about to be started.
     *  - You *can* modify the effect directly, e.g. to [remove emitters][removeEmitters]
     *  - You can alter startPoint and endPoint by assigning new Vector2 instances - do not mutate the default
     *  - You can alter delay and travelTime
     *  @see ParticleEffectData
     */
    protected open fun ParticleEffectData.configure() {}

    /** Called whenever an effect says it's complete.
     *  @param effectData The info on the just-completed effect - most clients won't need this
     *  @return whether and how many effects should ***restart*** once completed (actually creates new clones of the template effect and also calls [configure])
     */
    protected open fun onComplete(effectData: ParticleEffectData) = 0

    /** @return number of currently running effect clones */
    protected fun activeCount() = activeEffectData.size

    /** Loads the effect definition, creates a pool from it and starts [count] instances potentially modified by [configure]. */
    protected fun load(effectsFile: String, atlasName: String, count: Int) {
        activeEffectData.clear()
        val atlas = ImageGetter.getSpecificAtlas(atlasName)!!
        templateEffect.load(Gdx.files.internal(effectsFile), atlas)
        templateEffect.setEmittersCleanUpBlendFunction(false)  // Treat it as Unknown whether the effect file changes blend state -> do it ourselves
        maxDuration = templateEffect.emitters.maxOf { it.getDuration().lowMax + (if (it.delay.isActive) it.delay.lowMax else 0f) }

        repeat(count, ::newEffect)
    }

    @Suppress("UNUSED_PARAMETER") // Signature to match `repeat` argument
    private fun newEffect(dummy: Int) {
        val effect = ParticleEffect(templateEffect)
        val data = ParticleEffectData(nextIndex, effect, travelTime = maxDuration / 1000)
        nextIndex++
        data.configure()
        if (data.delay > 0f) {
            for (emitter in effect.emitters)
                emitter.delay.add(data.delay * 1000)
        }
        if (lastScale != 1f)
            effect.scaleEffect(lastScale)
        effect.start()
        activeEffectData += data
    }

    private fun RangedNumericValue.add(delta: Float) {
        if (isActive) {
            lowMin += delta
            lowMax += delta
        } else {
            isActive = true
            lowMin = delta
            lowMax = delta
        }
    }

    override fun dispose() {
        val effects = activeEffectData.toList()
        activeEffectData.clear()
        for (effect in effects) effect.effect.dispose()
        templateEffect.dispose()
    }

    fun render(stage: Stage?, delta: Float) {
        if (maxDuration == 0f) return
        val effectsBatch = stage?.batch ?: return
        effectsBatch.projectionMatrix = stage.viewport.camera.combined

        getTargetBounds(targetBounds)
        val newScale = getScale()
        if (newScale != lastScale) {
            val scaleChange = newScale / lastScale
            lastScale = newScale
            for ((_, effect) in activeEffectData) {
                effect.scaleEffect(scaleChange)
            }
        }

        var repeatCount = 0

        effectsBatch.begin()
        val iterator = activeEffectData.iterator()
        while (iterator.hasNext()) {
            val effectData = iterator.next()
            val effect = effectData.effect
            effectData.update(delta)
            val x = targetBounds.x + targetBounds.width * effectData.currentX()
            val y = targetBounds.y + targetBounds.height * effectData.currentY()
            effect.setPosition(x, y)
            effect.draw(effectsBatch, delta)
            if (effect.isComplete) {
                repeatCount += onComplete(effectData)
                effect.dispose()
                iterator.remove()
            }
        }
        effectsBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        effectsBatch.end()

        repeat(repeatCount, ::newEffect)
    }

    protected fun ParticleEffect.removeEmitters(predicate: (String)->Boolean) {
        val matches = Array<ParticleEmitter>()
        for (emitter in emitters)
            if (predicate(emitter.name)) matches.add(emitter)
        emitters.removeAll(matches, true) // This is a Gdx method, not kotlin MutableCollection.removeAll
    }
}
