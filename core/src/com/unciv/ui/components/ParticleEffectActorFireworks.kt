package com.unciv.ui.components

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.unciv.ui.images.ImageGetter

class ParticleEffectActorFireworks(
    private val actor: Actor
) : ParticleEffectFireworks() {
    class TestActor(stage: Stage) : Group() {
        private val fireworks = ParticleEffectActorFireworks(this)
        private val launcher = ImageGetter.getImage("UnitIcons/SS Booster")
        init {
            setSize(stage.width /2, stage.height / 2)
            setPosition(stage.width /2, stage.height / 2, Align.center)
            launcher.setBounds(stage.width / 4 - 12.5f, 0f, 25f, 25f)
            addActor(launcher)
        }
        fun render(delta: Float) = fireworks.render(stage, delta)
        fun load() = fireworks.load()
    }

    private val tempVector = Vector2()

    override fun getTargetBounds(bounds: Rectangle) {
        tempVector.set(0f, 0f)
        actor.localToStageCoordinates(tempVector)
        bounds.setPosition(tempVector)
        tempVector.set(actor.width, actor.height)
        actor.localToStageCoordinates(tempVector)
        bounds.setSize(tempVector.x - bounds.x, tempVector.y - bounds.y)
    }
}
