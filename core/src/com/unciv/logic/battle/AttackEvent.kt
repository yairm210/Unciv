package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

enum class AttackKind { Combat, Nuclear, AirSweep }
enum class AttackResolution { Pending, Completed, Withdrawn, Intercepted }

/**
 * One attack and the endpoints each civilization observed when it happened.
 * Knowledge is captured during the attack and never expanded by later exploration.
 * A nuclear blast also reveals its center to affected civilizations when it detonates.
 */
class AttackEvent() : IsPartOfGameInfoSerialization {
    var turn = 0
    var source = HexCoord.Zero
    var target = HexCoord.Zero
    var attackingCivId = ""
    var knowsSource = HashSet<String>()
    var knowsTarget = HashSet<String>()
    /** Territory owners at detonation, retained even if the blast destroys their cities. */
    var nuclearTerritoryCivIds = HashSet<String>()
    var kind = AttackKind.Combat
    var resolution = AttackResolution.Pending
    /** Actual retreat location and the civilizations that could observe the withdrawing unit there. */
    var withdrawalDestination: HexCoord? = null
    var withdrawalKnownBy = HashSet<String>()
    /** Improvement removed from the original target tile by this attack. */
    var destroyedImprovement: String? = null
    var attacker: AttackParticipant? = null
    /** Retaliation in the direct exchange, excluding effects on an unseen attacker afterward. */
    var defenderRetaliationDamage = 0
    var attackerDefeatedByDefender = false
    /** Primary target and other affected units/cities, including captures and collateral casualties. */
    var targets = ArrayList<AttackParticipant>()
    /** Interceptors engage [attacker]; their bases never become additional attack endpoints. */
    var interceptions = ArrayList<AttackInterception>()

    constructor(attacker: ICombatant, targetTile: Tile) : this() {
        val attackingCiv = attacker.getCivInfo()
        val gameInfo = attackingCiv.gameInfo
        val sourceTile = attacker.getTile()
        turn = gameInfo.turns
        source = sourceTile.position
        target = targetTile.position
        attackingCivId = attackingCiv.civID
        this.attacker = AttackParticipant(attacker)

        for (civ in gameInfo.civilizations) {
            // An attacker knows its own origin and intended target, including a nuke aimed into fog.
            if (civ == attackingCiv) {
                knowsSource.add(civ.civID)
                knowsTarget.add(civ.civID)
                continue
            }
            if (attacker.isVisibleTo(civ))
                knowsSource.add(civ.civID)
            if (targetTile in civ.viewableTiles)
                knowsTarget.add(civ.civID)
        }
    }

    @Readonly
    fun clone(): AttackEvent {
        @LocalState val result = AttackEvent()
        result.turn = turn
        result.source = source
        result.target = target
        result.attackingCivId = attackingCivId
        result.knowsSource = HashSet(knowsSource)
        result.knowsTarget = HashSet(knowsTarget)
        result.nuclearTerritoryCivIds = HashSet(nuclearTerritoryCivIds)
        result.kind = kind
        result.resolution = resolution
        result.withdrawalDestination = withdrawalDestination
        result.withdrawalKnownBy = HashSet(withdrawalKnownBy)
        result.destroyedImprovement = destroyedImprovement
        result.attacker = attacker?.clone()
        result.defenderRetaliationDamage = defenderRetaliationDamage
        result.attackerDefeatedByDefender = attackerDefeatedByDefender
        result.targets = ArrayList(targets.map { it.clone() })
        result.interceptions = ArrayList(interceptions.map { it.clone() })
        return result
    }
}
