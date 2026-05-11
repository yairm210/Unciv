package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.Disableable

class SuppressiveActivationListener : ActivationListener() {

    override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
        if (event == null) return
        val isInteractable = ActivationTypes.entries.any {
            it.isGesture && it.button == button && canBeActivated(event.listenerActor, it)
        }
        if (!isInteractable) return
        event.stop()
    }

    private fun canBeActivated(actor: Actor, type: ActivationTypes): Boolean {
        val attachments = ActorAttachments.getOrNull(actor) ?: return false
        val actionsMap = attachments.activationActions ?: return false
        if ((actor as? Disableable)?.isDisabled == true) return false
        val actions = actionsMap[type] ?: return false
        return !actions.isEmpty()
    }
    
}
