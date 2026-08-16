package com.unciv.view

import com.unciv.logic.civilization.Civilization
import yairm210.purity.annotations.Readonly

/** A View is the API a player has, to the game; It defines allowed behavior for a player.
 * It allows players to access data and functions they have permission to access.
 * All UI code should use ONLY views, and all "automate as civ" code should do the same,
 * if we want to retain "legal access to known data" for AI automation (this may be a non-goal. TBD) */
open class View<T>(protected val wrapped: T, protected open val viewer: Civilization?, protected val spectatorMode: Boolean = false) {
    /** Lets any [View] read the wrapped object of any other [View], without exposing [wrapped] itself outside the hierarchy. */
    @Readonly protected fun <U> View<U>.unwrap(): U = wrapped
}
