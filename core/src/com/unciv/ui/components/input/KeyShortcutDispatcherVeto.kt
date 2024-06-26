package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * A lambda testing for a given *associatedActor* whether the shortcuts
 * should be processed and whether the deep scan for child actors should continue.
 * *associatedActor* == `null` means *keyDispatcher* is *BaseScreen.globalShortcuts*
 */
typealias DispatcherVetoer = (associatedActor: Actor?) -> KeyShortcutDispatcherVeto.DispatcherVetoResult

object KeyShortcutDispatcherVeto {
    enum class DispatcherVetoResult { Accept, Skip, SkipWithChildren }

    internal val defaultDispatcherVetoer: DispatcherVetoer = { _ -> DispatcherVetoResult.Accept }

    /** When a Popup ([activePopup]) is active, this creates a [DispatcherVetoer] that disables all
     *  shortcuts on actors outside the popup and also the global shortcuts on the screen itself.
     */
    fun createPopupBasedDispatcherVetoer(activePopup: Actor): DispatcherVetoer = { associatedActor: Actor? ->
        when {
            associatedActor == null -> DispatcherVetoResult.Skip
            associatedActor.isDescendantOf(activePopup) -> DispatcherVetoResult.Accept
            else -> DispatcherVetoResult.SkipWithChildren
        }
    }

    /** Return this from [BaseScreen.getShortcutDispatcherVetoer] for Screens containing a [TileGroupMap] */
    fun createTileGroupMapDispatcherVetoer(): DispatcherVetoer {
        return { associatedActor: Actor? ->
            if (associatedActor is TileGroupMap<*>) DispatcherVetoResult.SkipWithChildren
            else DispatcherVetoResult.Accept
        }
    }

}
