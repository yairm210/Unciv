package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

internal class ActorAttachments private constructor(actor: Actor) {
    companion object {
        fun getOrNull(actor: Actor): ActorAttachments? {
            return actor.userObject as ActorAttachments?
        }

        fun get(actor: Actor): ActorAttachments {
            if (actor.userObject == null)
                actor.userObject = ActorAttachments(actor)
            return getOrNull(actor)!!
        }
    }

    val actor
        // Since 'keyShortcuts' has it anyway.
        get() = keyShortcuts.actor

    private lateinit var activationActions: MutableList<() -> Unit>
    private var clickActivationListener: ClickListener? = null

    val keyShortcuts = ActorKeyShortcutDispatcher(actor)

    fun activate() {
        if (this::activationActions.isInitialized) {
            for (action in activationActions)
                action()
        }
    }

    fun addActivationAction(action: () -> Unit) {
        if (!this::activationActions.isInitialized) activationActions = mutableListOf()
        activationActions.add(action)

        if (clickActivationListener == null) {
            clickActivationListener = object: ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    actor.activate()
                }
            }
            actor.addListener(clickActivationListener)
        }
    }

    fun removeActivationAction(action: () -> Unit) {
        if (!this::activationActions.isInitialized) return
        activationActions.remove(action)
        if (activationActions.none() && clickActivationListener != null) {
            actor.removeListener(clickActivationListener)
            clickActivationListener = null
        }
    }
}
