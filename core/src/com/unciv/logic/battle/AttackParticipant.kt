package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

enum class AttackParticipantOutcome {
    Pending, Survived, Destroyed, Captured, Withdrew
}

/** A participant's identity at attack time, independent of later movement, renaming or capture. */
class AttackParticipant() : IsPartOfGameInfoSerialization {
    var unitId: Int? = null
    var civId = ""
    var name = ""
    var instanceName: String? = null
    var position = HexCoord.Zero
    var healthBefore = 0
    var healthAfter: Int? = null
    /**
     * Actual HP loss recorded by direct combat effects; indirect effect chains are excluded.
     * Destruction or capture without HP loss is represented by [outcome].
     */
    var damageReceived = 0
    var outcome = AttackParticipantOutcome.Pending
    /**
     * Civilizations that identified this particular participant when the attack happened.
     * A combat report may disclose its unit type without identifying the unit, owner or origin.
     */
    var knownBy = HashSet<String>()

    constructor(combatant: MapUnitCombatant) : this() {
        val owner = combatant.getCivInfo()
        civId = owner.civID
        name = combatant.getName()
        position = combatant.getTile().position
        healthBefore = combatant.getHealth()
        unitId = combatant.unit.id
        instanceName = combatant.unit.instanceName
        for (civ in owner.gameInfo.civilizations) {
            if (combatant.isVisibleTo(civ))
                knownBy.add(civ.civID)
        }
    }

    @Readonly
    fun clone(): AttackParticipant {
        @LocalState val result = AttackParticipant()
        result.unitId = unitId
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
