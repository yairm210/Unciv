package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor

/**
 * A lambda testing for a given *associatedActor* whether the shortcuts in *keyDispatcher*
 * should be processed and whether the deep scan for child actors should continue.
 * *associatedActor* == `null` means *keyDispatcher* is *BaseScreen.globalShortcuts*
 */
typealias DispatcherVetoer = (associatedActor: Actor?, keyDispatcher: KeyShortcutDispatcher?) -> KeyShortcutDispatcherVeto.DispatcherVetoResult

object KeyShortcutDispatcherVeto {
    enum class DispatcherVetoResult { Accept, Skip, SkipWithChildren }

    internal val defaultDispatcherVetoer: DispatcherVetoer = { _, _ -> DispatcherVetoResult.Accept }

    /** When a Popup ([activePopup]) is active, this creates a [DispatcherVetoer] that disables all
     *  shortcuts on actors outside the popup and also the global shortcuts on the screen itself.
     */
    fun createPopupBasedDispatcherVetoer(activePopup: Actor): DispatcherVetoer? {
        return { associatedActor: Actor?, _: KeyShortcutDispatcher? ->
            when {
                associatedActor == null -> DispatcherVetoResult.Skip
                associatedActor.isDescendantOf(activePopup) -> DispatcherVetoResult.Accept
                else -> DispatcherVetoResult.SkipWithChildren
            }
        }
    }
}
