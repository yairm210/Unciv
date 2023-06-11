package com.unciv.ui.components.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.Disableable
import com.unciv.models.UncivSound
import com.unciv.ui.audio.SoundPlayer
import com.unciv.utils.Concurrency


fun Actor.addActivationAction(action: (() -> Unit)?) {
    if (action != null)
        ActorAttachments.get(this).addActivationAction(action)
}

fun Actor.removeActivationAction(action: (() -> Unit)?) {
    if (action != null)
        ActorAttachments.getOrNull(this)?.removeActivationAction(action)
}

fun Actor.isActive(): Boolean = isVisible && ((this as? Disableable)?.isDisabled != true)

fun Actor.activate() {
    if (isActive())
        ActorAttachments.getOrNull(this)?.activate()
}

val Actor.keyShortcutsOrNull
    get() = ActorAttachments.getOrNull(this)?.keyShortcuts
val Actor.keyShortcuts
    get() = ActorAttachments.get(this).keyShortcuts

fun Actor.onActivation(sound: UncivSound = UncivSound.Click, action: () -> Unit): Actor {
    addActivationAction {
        Concurrency.run("Sound") { SoundPlayer.play(sound) }
        action()
    }
    return this
}

fun Actor.onActivation(action: () -> Unit): Actor = onActivation(UncivSound.Click, action)


enum class DispatcherVetoResult { Accept, Skip, SkipWithChildren }
typealias DispatcherVetoer = (associatedActor: Actor?, keyDispatcher: KeyShortcutDispatcher?) -> DispatcherVetoResult

/**
 * Install shortcut dispatcher for this stage. It activates all actions associated with the
 * pressed key in [additionalShortcuts] (if specified) and all actors in the stage. It is
 * possible to temporarily disable or veto some shortcut dispatchers by passing an appropriate
 * [dispatcherVetoerCreator] function. This function may return a [DispatcherVetoer], which
 * will then be used to evaluate all shortcut sources in the stage. This two-step vetoing
 * mechanism allows the callback ([dispatcherVetoerCreator]) perform expensive preparations
 * only one per keypress (doing them in the returned [DispatcherVetoer] would instead be once
 * per keypress/actor combination).
 */
fun Stage.installShortcutDispatcher(additionalShortcuts: KeyShortcutDispatcher? = null, dispatcherVetoerCreator: (() -> DispatcherVetoer?)? = null) {
    addListener(object: InputListener() {
        override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
            val key = when {
                event == null ->
                    KeyCharAndCode.UNKNOWN
                Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) ->
                    KeyCharAndCode.ctrlFromCode(event.keyCode)
                else ->
                    KeyCharAndCode(event.keyCode)
            }

            if (key != KeyCharAndCode.UNKNOWN) {
                var dispatcherVetoer = when { dispatcherVetoerCreator != null -> dispatcherVetoerCreator() else -> null }
                if (dispatcherVetoer == null) dispatcherVetoer = { _, _ -> DispatcherVetoResult.Accept }

                if (activate(key, dispatcherVetoer))
                    return true
                // Make both Enter keys equivalent.
                if ((key == KeyCharAndCode.NUMPAD_ENTER && activate(KeyCharAndCode.RETURN, dispatcherVetoer))
                    || (key == KeyCharAndCode.RETURN && activate(KeyCharAndCode.NUMPAD_ENTER, dispatcherVetoer)))
                    return true
                // Likewise always match Back to ESC.
                if ((key == KeyCharAndCode.ESC && activate(KeyCharAndCode.BACK, dispatcherVetoer))
                    || (key == KeyCharAndCode.BACK && activate(KeyCharAndCode.ESC, dispatcherVetoer)))
                    return true
            }

            return false
        }

        private fun activate(key: KeyCharAndCode, dispatcherVetoer: DispatcherVetoer): Boolean {
            val shortcutResolver = KeyShortcutDispatcher.Resolver(key)
            val pendingActors = ArrayDeque<Actor>(actors.toList())

            if (additionalShortcuts != null && dispatcherVetoer(null, additionalShortcuts) == DispatcherVetoResult.Accept)
                shortcutResolver.updateFor(additionalShortcuts)

            while (pendingActors.any()) {
                val actor = pendingActors.removeFirst()
                val shortcuts = actor.keyShortcutsOrNull
                val vetoResult = dispatcherVetoer(actor, shortcuts)

                if (shortcuts != null && vetoResult == DispatcherVetoResult.Accept)
                    shortcutResolver.updateFor(shortcuts)
                if (actor is Group && vetoResult != DispatcherVetoResult.SkipWithChildren)
                    pendingActors.addAll(actor.children)
            }

            for (action in shortcutResolver.triggeredActions)
                action()
            return shortcutResolver.triggeredActions.any()
        }
    })
}
