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
    @Readonly fun canMoveTo(foreignCityView: ForeignCityView): Boolean = spy.canMoveTo(foreignCityView.getCity())

    // Actions
    fun tryMoveTo(foreignCityView: ForeignCityView?): Boolean {
        spy.moveTo(foreignCityView?.getCity())
        return true
    }
}
