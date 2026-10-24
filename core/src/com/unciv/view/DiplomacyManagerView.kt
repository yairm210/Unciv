package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import yairm210.purity.annotations.Readonly

/** View of a [DiplomacyManager] from the perspective of [viewer] via [gameView]. */
class DiplomacyManagerView(private val diplomacyManager: DiplomacyManager, viewer: Civilization, spectatorMode: Boolean = false,
                           gameView: GameView) : GameBasedView<DiplomacyManager>(diplomacyManager, viewer, spectatorMode, gameView) {

    @Readonly fun canDeclareWar(): Boolean = diplomacyManager.canDeclareWar()
    @Readonly fun hasFlag(flag: DiplomacyFlags): Boolean = diplomacyManager.hasFlag(flag)
    @Readonly fun hasModifier(modifier: DiplomaticModifiers): Boolean = diplomacyManager.hasModifier(modifier)
    @Readonly fun getFlag(flag: DiplomacyFlags): Int = diplomacyManager.getFlag(flag)
    @Readonly fun relationshipLevel(): RelationshipLevel = diplomacyManager.relationshipLevel()
}
