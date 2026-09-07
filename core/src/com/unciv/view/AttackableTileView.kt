package com.unciv.view

import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.civilization.Civilization
import yairm210.purity.annotations.Readonly

/** View of an [AttackableTile] from the perspective of [viewer] via [gameView]. */
class AttackableTileView(private val attackableTile: AttackableTile, viewer: Civilization, spectatorMode: Boolean = false,
                         gameView: GameView) : GameBasedView<AttackableTile>(attackableTile, viewer, spectatorMode, gameView) {

    @Readonly fun getTileToAttackFrom(): TileView = gameView.getTile(attackableTile.tileToAttackFrom)
    @Readonly fun getTileToAttack(): TileView = gameView.getTile(attackableTile.tileToAttack)
    @Readonly fun getMovementLeftAfterMovingToAttackTile(): Float = attackableTile.movementLeftAfterMovingToAttackTile
    @Readonly fun getCombatant(): CombatantView? = attackableTile.combatant?.let { CombatantView(it, viewer, spectatorMode, gameView) }

    // TEMP - should be removed once migration ends
    @Readonly fun getAttackableTile(): AttackableTile = attackableTile
}
