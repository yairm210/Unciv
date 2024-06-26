package com.unciv.ui.components.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.unciv.ui.components.extensions.isControlKeyPressed
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto.DispatcherVetoResult


/** @see installShortcutDispatcher */
 class KeyShortcutListener(
    private val actors: Sequence<Actor>,
    private val additionalShortcuts: KeyShortcutDispatcher? = null,
    private val dispatcherVetoerCreator: () -> DispatcherVetoer?
) : InputListener() {

    override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
        val key = when {
            event == null ->
                KeyCharAndCode.UNKNOWN
            Gdx.input.isControlKeyPressed() ->
                KeyCharAndCode.ctrlFromCode(event.keyCode)
            else ->
                KeyCharAndCode(event.keyCode)
        }
        if (key == KeyCharAndCode.UNKNOWN) return false

        val dispatcherVetoer = dispatcherVetoerCreator()
            ?: KeyShortcutDispatcherVeto.defaultDispatcherVetoer
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

        return false
    }

    private fun activate(key: KeyCharAndCode, dispatcherVetoer: DispatcherVetoer): Boolean {
        val shortcutResolver = KeyShortcutDispatcher.Resolver(key)
        val pendingActors = ArrayDeque(actors.toList())

        if (additionalShortcuts != null && dispatcherVetoer(null) == DispatcherVetoResult.Accept)
            shortcutResolver.updateFor(additionalShortcuts)

        while (true) {
            val actor = pendingActors.removeFirstOrNull()
                ?: break
            val shortcuts = ActorAttachments.getOrNull(actor)?.keyShortcuts
            val vetoResult = dispatcherVetoer(actor)

            if (shortcuts != null && vetoResult == DispatcherVetoResult.Accept)
                shortcutResolver.updateFor(shortcuts)
            if (actor is Group && vetoResult != DispatcherVetoResult.SkipWithChildren)
                pendingActors.addAll(actor.children)
        }

        for (action in shortcutResolver.triggeredActions)
            action()
        return shortcutResolver.triggeredActions.isNotEmpty()
    }
}
