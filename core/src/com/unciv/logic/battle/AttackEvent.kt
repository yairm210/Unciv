package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

enum class AttackKind { Combat, Nuclear }
enum class AttackResolution { Pending, Completed, Withdrawn, Intercepted }

/**
 * One attack and the endpoints each civilization observed when it happened.
 * Knowledge is captured before combat changes units or visibility, and never expanded later.
 */
class AttackEvent() : IsPartOfGameInfoSerialization {
    var turn = 0
    var source = HexCoord.Zero
    var target = HexCoord.Zero
    var attackingCivId = ""
    var knowsSource = HashSet<String>()
    var knowsTarget = HashSet<String>()
    var kind = AttackKind.Combat
    var resolution = AttackResolution.Pending
    var attacker: AttackParticipant? = null
    /** Includes every affected city and unit for a nuclear attack; an empty tile has no participant. */
    var targets = ArrayList<AttackParticipant>()

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
            if (sourceTile in civ.viewableTiles
                && (!attacker.isInvisible(civ) || sourceTile in civ.viewableInvisibleUnitsTiles))
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
        result.kind = kind
        result.resolution = resolution
        result.attacker = attacker?.clone()
        result.targets = ArrayList(targets.map { it.clone() })
        return result
    }
}
