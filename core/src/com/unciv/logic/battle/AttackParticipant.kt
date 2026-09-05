package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

enum class AttackParticipantKind { Unit, City }

enum class AttackParticipantOutcome {
    Pending, Survived, Destroyed, Captured, Withdrew, DefensesReduced, Raided
}

/** A participant's identity at attack time, independent of later movement, renaming or capture. */
class AttackParticipant() : IsPartOfGameInfoSerialization {
    var kind = AttackParticipantKind.Unit
    var unitId: Int? = null
    var cityId: String? = null
    var civId = ""
    var name = ""
    var instanceName: String? = null
    var position = HexCoord.Zero
    var healthBefore = 0
    var healthAfter: Int? = null
    /** Actual HP damage; destruction or capture without HP loss is represented by [outcome]. */
    var damageReceived = 0
    var outcome = AttackParticipantOutcome.Pending
    /**
     * Civilizations that identified this particular participant when the attack happened.
     * A combat report may disclose its unit type without identifying the unit, owner or origin.
     */
    var knownBy = HashSet<String>()

    constructor(combatant: ICombatant) : this() {
        val owner = combatant.getCivInfo()
        civId = owner.civID
        name = combatant.getName()
        position = combatant.getTile().position
        healthBefore = combatant.getHealth()
        when (combatant) {
            is MapUnitCombatant -> {
                unitId = combatant.unit.id
                instanceName = combatant.unit.instanceName
            }
            is CityCombatant -> {
                kind = AttackParticipantKind.City
                cityId = combatant.city.id
            }
        }
        for (civ in owner.gameInfo.civilizations) {
            if (civ == owner || combatant.getTile() in civ.viewableTiles
                && (!combatant.isInvisible(civ) || combatant.getTile() in civ.viewableInvisibleUnitsTiles))
                knownBy.add(civ.civID)
        }
    }

    /** Use explicit outcomes for capture, withdrawal and raids; HP alone cannot describe them. */
    fun finish(combatant: ICombatant, result: AttackParticipantOutcome? = null) {
        healthAfter = combatant.getHealth()
        outcome = result ?: when (combatant) {
            is MapUnitCombatant -> if (combatant.unit !in combatant.unit.civ.units.getCivUnits())
                AttackParticipantOutcome.Destroyed else AttackParticipantOutcome.Survived
            is CityCombatant -> when {
                combatant.city !in combatant.city.civ.cities -> AttackParticipantOutcome.Destroyed
                combatant.isDefeated() -> AttackParticipantOutcome.DefensesReduced
                else -> AttackParticipantOutcome.Survived
            }
            else -> AttackParticipantOutcome.Survived
        }
    }

    @Readonly
    fun clone(): AttackParticipant {
        @LocalState val result = AttackParticipant()
        result.kind = kind
        result.unitId = unitId
        result.cityId = cityId
        result.civId = civId
        result.name = name
        result.instanceName = instanceName
        result.position = position
        result.healthBefore = healthBefore
        result.healthAfter = healthAfter
        result.damageReceived = damageReceived
        result.outcome = outcome
        result.knownBy = HashSet(knownBy)
        return result
    }
}
