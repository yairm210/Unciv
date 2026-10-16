package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import yairm210.purity.annotations.Readonly

/** View of a [DiplomacyManager] from the perspective of [viewer] via [gameView]. */
class DiplomacyManagerView(private val diplomacyManager: DiplomacyManager, viewer: Civilization, spectatorMode: Boolean = false,
                           gameView: GameView) : GameBasedView<DiplomacyManager>(diplomacyManager, viewer, spectatorMode, gameView) {

    @Readonly fun canDeclareWar(): Boolean = diplomacyManager.canDeclareWar()
}
