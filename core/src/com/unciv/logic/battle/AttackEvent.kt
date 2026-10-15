package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

enum class AttackResolution {Completed, Withdrawn }

/**
 * One unit attack and the endpoints each civilization observed when it happened.
 * Knowledge is captured during the attack and never expanded by later exploration.
 */
class AttackEvent() : IsPartOfGameInfoSerialization {
    var turn = 0
    var sourceTile = HexCoord.Zero
    var targetTile = HexCoord.Zero
    var civIdsKnowingAttackSource = HashSet<String>()
    var civIdsKnowingAttackTarget = HashSet<String>()
    var resolution: AttackResolution? = null 
    var attacker: AttackParticipant? = null
    /** The intended target and units affected by direct combat effects. */
    var targets = ArrayList<AttackParticipant>()

    constructor(attacker: MapUnitCombatant, targetTile: Tile) : this() {
        val attackingCiv = attacker.getCivInfo()
        val gameInfo = attackingCiv.gameInfo
        val sourceTile = attacker.getTile()
        turn = gameInfo.turns
        this@AttackEvent.sourceTile = sourceTile.position
        this@AttackEvent.targetTile = targetTile.position
        this.attacker = AttackParticipant(attacker)

        for (civ in gameInfo.civilizations) {
            // An attacker knows its own origin and intended target, even if the target is unseen.
            if (civ == attackingCiv) {
                civIdsKnowingAttackSource.add(civ.civID)
                civIdsKnowingAttackTarget.add(civ.civID)
                continue
            }
            if (attacker.isVisibleTo(civ))
                civIdsKnowingAttackSource.add(civ.civID)
            if (targetTile in civ.viewableTiles && targetTile.getUnits().any { it.isVisibleTo(civ) })
                civIdsKnowingAttackTarget.add(civ.civID)
        }
    }

    @Readonly
    fun clone(): AttackEvent {
        @LocalState val result = AttackEvent()
        result.turn = turn
        result.sourceTile = sourceTile
        result.targetTile = targetTile
        result.civIdsKnowingAttackSource = HashSet(civIdsKnowingAttackSource)
        result.civIdsKnowingAttackTarget = HashSet(civIdsKnowingAttackTarget)
        result.resolution = resolution
        result.attacker = attacker?.clone()
        result.targets = ArrayList(targets.map { it.clone() })
        return result
    }
}
