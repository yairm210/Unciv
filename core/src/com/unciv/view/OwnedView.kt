package com.unciv.view

import com.unciv.logic.civilization.Civilization
import yairm210.purity.annotations.Readonly

/** Base class for [GameBasedView]s that represent something owned by a specific civ (a city, a unit, a spy, etc). */
abstract class OwnedView<T>(wrapped: T, viewer: Civilization, spectatorMode: Boolean = false, gameView: GameView) :
    GameBasedView<T>(wrapped, viewer, spectatorMode, gameView) {
    @Readonly internal abstract fun owner(): Civilization

    @Readonly fun isOwnedBy(foreignCivView: ForeignCivView): Boolean = owner() === foreignCivView.unwrap()
    @Readonly fun isOwnedByViewer(): Boolean = owner() === viewer
}
