package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener

class ActivationListener : ActorGestureListener(20f, 0.25f, 1.1f, Int.MAX_VALUE.toFloat()) {
    // defaults are: halfTapSquareSize = 20, tapCountInterval = 0.4f, longPressDuration = 1.1f, maxFlingDelay = Integer.MAX_VALUE

    override fun tap(event: InputEvent?, x: Float, y: Float, count: Int, button: Int) {
        val actor = event?.listenerActor ?: return
        val type = ActivationTypes.entries.firstOrNull {
            it.isGesture && it.tapCount == count && it.button == button
        } ?: return
        actor.activate(type)
    }

    override fun longPress(actor: Actor?, x: Float, y: Float): Boolean {
        if (actor == null) return false
        // See #10050 - when a tap discards its actor or ascendants, Gdx can't cancel the longpress timer
        if (actor.stage == null) return false
        return actor.activate(ActivationTypes.Longpress)
    }
}
