package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor

/**
 * Simple subclass of [KeyShortcutDispatcher] for which all shortcut actions default to
 * [activating][Actor.activate] the actor. However, other actions are possible too.
 */
class ActorKeyShortcutDispatcher internal constructor(val actor: Actor) : KeyShortcutDispatcher() {
    val action: ActivationAction = { actor.activate(ActivationTypes.Keystroke) }
    fun add(shortcut: KeyShortcut?) = add(shortcut, action)
    fun add(binding: KeyboardBinding, priority: Int = 1) = add(binding, priority, action)
    fun add(key: KeyCharAndCode?) = add(key, action)
    fun add(char: Char?) = add(char, action)
    fun add(keyCode: Int?) = add(keyCode, action)

    override fun isActive(): Boolean = actor.isActive()
}
