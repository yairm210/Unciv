package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

enum class AttackResolution {Completed, Withdrawn }

/**
 * One unit attack and the endpoints each civilization observed when it happened.
 * Knowledge is captured during the attack and never expanded by later exploration.
 */
data class AttackEvent(
    val turn: Int,
    val sourceTile: HexCoord,
    val targetTile: HexCoord,
    val attacker: AttackParticipant,
    val civIdsKnowingAttackSource: HashSet<String> = HashSet(),
    val civIdsKnowingAttackTarget: HashSet<String> = HashSet(),
    val resolution: AttackResolution = AttackResolution.Completed,
    /** The intended target and units affected by direct combat effects. */
    val targets: ArrayList<AttackParticipant> = ArrayList()
) : IsPartOfGameInfoSerialization {

    @Suppress("unused") // Used by json deserialization
    constructor() : this(
        turn = 0,
        sourceTile = HexCoord.Zero,
        targetTile = HexCoord.Zero,
        attacker = AttackParticipant()
    )

    constructor(attacker: ICombatant, targetTile: Tile) : this(
        turn = attacker.getCivInfo().gameInfo.turns,
        sourceTile = attacker.getTile().position,
        targetTile = targetTile.position,
        attacker = AttackParticipant(attacker)
    ) {
        val attackingCiv = attacker.getCivInfo()
        val gameInfo = attackingCiv.gameInfo

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
        return copy(
            attacker = attacker.clone(),
            civIdsKnowingAttackSource = HashSet(civIdsKnowingAttackSource),
            civIdsKnowingAttackTarget = HashSet(civIdsKnowingAttackTarget),
            targets = ArrayList(targets.map { it.clone() })
        )
    }
}
