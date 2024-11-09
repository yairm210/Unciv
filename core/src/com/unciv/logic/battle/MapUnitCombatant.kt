package com.unciv.logic.battle

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.UnitType

class MapUnitCombatant(val unit: MapUnit) : ICombatant {
    override fun getHealth(): Int = unit.health
    override fun getMaxHealth() = 100
    override fun getCivInfo(): Civilization = unit.civ
    override fun getTile(): Tile = unit.getTile()
    override fun getName(): String = unit.name
    override fun isDefeated(): Boolean = unit.health <= 0
    override fun isInvisible(to: Civilization): Boolean = unit.isInvisible(to)
    override fun canAttack(): Boolean = unit.canAttack()
    override fun matchesFilter(filter: String, multiFilter: Boolean) = unit.matchesFilter(filter, multiFilter)
    override fun getAttackSound() = unit.baseUnit.attackSound.let {
        if (it == null) UncivSound.Click else UncivSound(it)
    }

    override fun takeDamage(damage: Int) = unit.takeDamage(damage)

    override fun getAttackingStrength(): Int {
        return if (isRanged()) unit.baseUnit.rangedStrength
        else unit.baseUnit.strength
    }

    override fun getDefendingStrength(attackedByRanged: Boolean): Int {
        return if (unit.isEmbarked() && !isCivilian())
            unit.civ.getEra().embarkDefense
        else if (isRanged() && attackedByRanged)
            unit.baseUnit.rangedStrength
        else unit.baseUnit.strength
    }

    override fun getUnitType(): UnitType {
        return unit.type
    }

    override fun toString(): String {
        return unit.name+" of "+unit.civ.civName
    }

    fun getMatchingUniques(uniqueType: UniqueType, conditionalState: StateForConditionals, checkCivUniques: Boolean): Sequence<Unique> =
        unit.getMatchingUniques(uniqueType, conditionalState, checkCivUniques)

    fun hasUnique(uniqueType: UniqueType, conditionalState: StateForConditionals? = null): Boolean =
        if (conditionalState == null) unit.hasUnique(uniqueType)
        else unit.hasUnique(uniqueType, conditionalState)

}
