package com.unciv.view

import com.unciv.logic.civilization.Civilization
import yairm210.purity.annotations.Readonly

/** A View is the API a player has, to the game; It defines allowed behavior for a player.
 * It allows players to access data and functions they have permission to access.
 * All UI code should use ONLY views, and all "automate as civ" code should do the same,
 * if we want to retain "legal access to known data" for AI automation (this may be a non-goal. TBD)
 * 
 * Notes about View design:
 * - Any *ruleset objects* are fair game - the entire ruleset should be available, AND they should all be readonly anyway (right? right?! oh dear)
 * - Any *game state objects* (Civilization, City, Tile, Unit, etc) should be wrapped in a View, and only exposed to the player via that View
 *    - This applies to function inputs and outputs!
 * - Any data accessed in the UI from the base object directly should be converted to a @Readonly function (not optional!) to get that data
 * - Data should use readonly interfaces when possible - e.g. List<thing> instead of ArrayList<thing>, Map<X, Int> instead of Counter<X>, etc
 * - Any state-changing function in the UI should be converted to a boolean-returning "try apply state change" function
 *    - In the future these will also check preconditions of applying this state change, not for now
 * - Retain minimal API - anything the UI can derive from existing calls should not be part of the view
 * 
 * See also https://github.com/yairm210/Unciv/issues/15280 and https://medium.com/@yairm210/game-interfaces-data-access-and-action-validity-1760834be165
 * */
open class View<T>(protected val wrapped: T,
                   /** The civ we're viewing as.
                    * Spectators can either be viewing as themselves - full view permissions - or as another civ,
                    * in which case they will "see" only what that civ sees */
                   protected open val viewer: Civilization?,
                   /** Indicates whether we are really a spectator, "looking in" to the view of another civ
                    * In this case we should not be able to execute any state-chaning action */
                   protected val spectatorMode: Boolean = false) {
    /** Lets any [View] read the wrapped object of any other [View], without exposing [wrapped] itself outside the hierarchy. */
    @Readonly protected fun <U> View<U>.unwrap(): U = wrapped

    override fun equals(other: Any?): Boolean = other is View<*> && wrapped == other.wrapped
    override fun hashCode(): Int = wrapped.hashCode()
}
