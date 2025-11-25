package com.unciv.logic.battle

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.UnitType
import yairm210.purity.annotations.Readonly

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

    override fun getAttackingStrength(defender: ICombatant?): Int {
        val state = GameContext(this, defender, this.getTile(), CombatAction.Attack)
        val extraStrength = unit.getMatchingUniques(UniqueType.StrengthAmount, state).sumOf { it.params[0].toInt() }
        return if (isRanged()) unit.baseUnit.rangedStrength + extraStrength
        else unit.baseUnit.strength + extraStrength
    }

    override fun getDefendingStrength(attacker: ICombatant?): Int {
        val attackedByRanged = attacker?.isRanged() == true
        val state = GameContext(this, attacker, this.getTile(), CombatAction.Defend)
        val extraStrength = unit.getMatchingUniques(UniqueType.StrengthAmount, state).sumOf { it.params[0].toInt() }
        return if (unit.isEmbarked() && !isCivilian())
            unit.civ.getEra().embarkDefense
        else if (isRanged() && attackedByRanged)
            unit.baseUnit.rangedStrength + extraStrength
        else unit.baseUnit.strength + extraStrength
    }

    override fun getUnitType(): UnitType {
        return unit.type
    }

    override fun toString(): String {
        return unit.name+" of "+unit.civ.civName
    }

    @Readonly 
    fun getMatchingUniques(uniqueType: UniqueType, gameContext: GameContext, checkCivUniques: Boolean): Sequence<Unique> =
        unit.getMatchingUniques(uniqueType, gameContext, checkCivUniques)

    @Readonly
    fun hasUnique(uniqueType: UniqueType, conditionalState: GameContext? = null): Boolean =
        if (conditionalState == null) unit.hasUnique(uniqueType)
        else unit.hasUnique(uniqueType, conditionalState)

}
