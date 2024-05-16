package com.unciv.ui.components

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.unciv.ui.screens.basescreen.BaseScreen

class ParticleEffectActorFireworks(
    private val actor: Actor
) : ParticleEffectFireworks() {
    companion object {
        fun getTester(screen: BaseScreen): TestActor {
            val actor = TestActor()
            actor.setSize(screen.stage.width /2, screen.stage.height / 2)
            actor.setPosition(screen.stage.width /2, screen.stage.height / 2, Align.center)
            return actor
        }
    }

    class TestActor : Actor() {
        private val fireworks = ParticleEffectActorFireworks(this)
        fun render(delta: Float) = fireworks.render(delta)
        fun load() = fireworks.load()
    }

    private val tempVector = Vector2()

    override fun getStageBounds(bounds: Rectangle) {
        tempVector.set(actor.x, actor.y)
        actor.localToStageCoordinates(tempVector)
        bounds.setPosition(tempVector)
        tempVector.set(actor.width, actor.height)
        actor.localToStageCoordinates(tempVector)
        bounds.setSize(tempVector.x, tempVector.y)
    }
}
