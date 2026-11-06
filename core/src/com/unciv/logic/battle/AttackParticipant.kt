package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import yairm210.purity.annotations.ModifiesInternalStateOnly
import yairm210.purity.annotations.Readonly

enum class AttackParticipantOutcome {
    Survived, Destroyed, Captured, Withdrew
}

/** A participant's identity at attack time, independent of later movement, renaming or capture. */
@ModifiesInternalStateOnly
data class AttackParticipant(
    val combatantID: String,
    val civID: String,
    val name: String,
    val position: HexCoord,
    val healthBefore: Int,
    val instanceName: String? = null,
    var healthAfter: Int = healthBefore,
    /**
     * Actual HP loss recorded by direct combat effects; indirect effect chains are excluded.
     * Destruction or capture without HP loss is represented by [outcome].
     */
    var damageReceived: Int = 0,
    var outcome: AttackParticipantOutcome = AttackParticipantOutcome.Survived,
    /**
     * Civilizations that identified this particular participant when the attack happened.
     * A combat report may disclose its unit type without identifying the unit, owner or origin.
     */
    val civIdsThatKnowMe: HashSet<String> = HashSet()
) : IsPartOfGameInfoSerialization {

    @Suppress("unused")
    constructor() : this(
        combatantID = "",
        civID = "",
        name = "",
        position = HexCoord.Zero,
        healthBefore = 0
    )

    constructor(combatant: ICombatant) : this(
        combatantID = combatant.getId(),
        civID = combatant.getCivInfo().civID,
        name = combatant.getName(),
        position = combatant.getTile().position,
        healthBefore = combatant.getHealth(),
        instanceName = combatant.getInstanceName()
    ) {
        val owner = combatant.getCivInfo()
        for (civ in owner.gameInfo.civilizations) {
            if (combatant.isVisibleTo(civ))
                civIdsThatKnowMe.add(civ.civID)
        }
    }

    @Readonly
    fun clone(): AttackParticipant {
        return copy(civIdsThatKnowMe = HashSet(civIdsThatKnowMe))
    }
}
