package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor

/**
 * Simple subclass of [KeyShortcutDispatcher] for which all shortcut actions default to
 * [activating][Actor.activate] the actor. However, other actions are possible too.
 */
class ActorKeyShortcutDispatcher internal constructor(val actor: Actor): KeyShortcutDispatcher() {
    fun add(shortcut: KeyShortcut?) = add(shortcut) { actor.activate() }
    fun add(binding: KeyboardBinding, priority: Int = 1) = add(binding, priority) { actor.activate() }
    fun add(key: KeyCharAndCode?) = add(key) { actor.activate() }
    fun add(char: Char?) = add(char) { actor.activate() }
    fun add(keyCode: Int?) = add(keyCode) { actor.activate() }

    override fun isActive(): Boolean = actor.isActive()
}
