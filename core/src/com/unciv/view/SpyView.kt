package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.models.Spy
import yairm210.purity.annotations.Readonly

/** View of a [Spy] from the perspective of [viewer]. */
class SpyView(private val spy: Spy,
              viewer: Civilization,
              spectatorMode: Boolean = false,
              gameView: GameView) : GameBasedView<Spy>(spy, viewer, spectatorMode, gameView) {

    // Navigation
    @Deprecated("Scheduled for removal")
    @Readonly fun getSpy(): Spy = spy
    @Readonly fun getCityViewOrNull(): ForeignCityView? = spy.getCityOrNull()?.let { gameView.getForeignCityView(it) }

    // Data retrieval
    val name: String get() = spy.name
    @Readonly fun getEffectiveRank(): Int = spy.getEffectiveRank()
    // Spy.canMoveTo() treats the spy's current city as a valid "move" target (so re-selecting it
    // is a no-op elsewhere), but at the view level that would let the player click their own spy's
    // city and call tryMoveTo() on it, which resets its action to Moving and interrupts whatever
    // it was doing (surveillance, counter-intelligence, etc). Exclude it here.
    @Readonly fun canMoveTo(foreignCityView: ForeignCityView): Boolean =
        getCityViewOrNull() != foreignCityView && spy.canMoveTo(foreignCityView.getCity())

    // Actions
    fun tryMoveTo(foreignCityView: ForeignCityView?): Boolean {
        if (foreignCityView != null && !canMoveTo(foreignCityView)) return false
        spy.moveTo(foreignCityView?.getCity())
        return true
    }
}
