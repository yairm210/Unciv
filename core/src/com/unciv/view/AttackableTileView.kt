package com.unciv.view

import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import yairm210.purity.annotations.Readonly

/** View of an [AttackableTile] from the perspective of [viewer] via [gameView]. */
class AttackableTileView(private val attackableTile: AttackableTile, viewer: Civilization, spectatorMode: Boolean = false,
                         gameView: GameView) : GameBasedView<AttackableTile>(attackableTile, viewer, spectatorMode, gameView) {

    @Readonly fun getTileToAttackFrom(): TileView = gameView.getTile(attackableTile.tileToAttackFrom)
    @Readonly fun getTileToAttack(): TileView = gameView.getTile(attackableTile.tileToAttack)
    @Readonly fun getMovementLeftAfterMovingToAttackTile(): Float = attackableTile.movementLeftAfterMovingToAttackTile
    @Readonly fun getCombatant(): CombatantView? = when (val combatant = attackableTile.combatant) {
        null -> null
        is MapUnitCombatant -> gameView.getForeignMapUnitView(combatant.unit).asCombatant()
        is CityCombatant -> gameView.getForeignCityView(combatant.city).asCombatant()
        else -> null
    }
}
