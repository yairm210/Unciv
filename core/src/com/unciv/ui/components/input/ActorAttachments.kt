package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Disableable
import com.unciv.models.UncivSound

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

    private lateinit var activationActions: ActivationActionMap
    private var activationListener: ActivationListener? = null

    /**
     *  Keyboard dispatcher for the [actor] this is attached to.
     *
     *  Note the routing goes [KeyShortcutListener] -> [ActorKeyShortcutDispatcher] -> [ActivationActionMap],
     *  meaning that shortcuts registered here _with_ explicit action parameter are independent of
     *  other [activationActions] and do not reach [ActivationActionMap], only those added _without_
     *  explicit action are routed through and get [ActivationTypes] equivalence to tap/click.
     *
     *  This also means the former are silent while the latter do the Click sound by default.
     */
    val keyShortcuts = ActorKeyShortcutDispatcher(actor)

    fun activate(type: ActivationTypes): Boolean {
        if (!this::activationActions.isInitialized) return false
        if ((actor as? Disableable)?.isDisabled == true) return false // Skip if disabled
        return activationActions.activate(type)
    }

    fun addActivationAction(
        type: ActivationTypes,
        sound: UncivSound = UncivSound.Click,
        noEquivalence: Boolean = false,
        action: ActivationAction
    ) {
        if (!this::activationActions.isInitialized)
            activationActions = ActivationActionMap()

        else if (activationListener != null && activationListener !in actor.listeners) {
            // We think our listener should be active but it isn't - Actor.clearListeners() was called.
            // Decision: To keep existing code (which could have to call clearActivationActions otherwise),
            // we start over clearing any registered actions using that listener.
            actor.addListener(activationListener)
            activationActions.clearGestures()
        }

        activationActions.add(type, sound, noEquivalence, action)

        if (!type.isGesture || activationListener != null) return
        activationListener = ActivationListener()
        actor.addListener(activationListener)
    }

    fun clearActivationActions(type: ActivationTypes, noEquivalence: Boolean = true) {
        if (!this::activationActions.isInitialized) return
        activationActions.clear(type, noEquivalence)
        if (activationListener == null || activationActions.isNotEmpty()) return
        actor.removeListener(activationListener)
        activationListener = null
    }
}
