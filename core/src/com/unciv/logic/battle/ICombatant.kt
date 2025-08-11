package com.unciv.logic.battle

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.UnitType
import yairm210.purity.annotations.Readonly

interface ICombatant {
    @Readonly fun getName(): String
    @Readonly fun getHealth(): Int
    @Readonly fun getMaxHealth(): Int
    @Readonly fun getUnitType(): UnitType
    @Readonly fun getAttackingStrength(): Int
    @Readonly fun getDefendingStrength(attackedByRanged: Boolean = false): Int
    fun takeDamage(damage: Int)
    @Readonly fun isDefeated(): Boolean
    @Readonly fun getCivInfo(): Civilization
    @Readonly fun getTile(): Tile
    @Readonly fun isInvisible(to: Civilization): Boolean
    @Readonly fun canAttack(): Boolean
    /** Implements [UniqueParameterType.CombatantFilter][com.unciv.models.ruleset.unique.UniqueParameterType.CombatantFilter] */
    @Readonly fun matchesFilter(filter: String, multiFilter: Boolean = true): Boolean
    fun getAttackSound(): UncivSound

    @Readonly fun isMelee(): Boolean = !isRanged()
    @Readonly 
    fun isRanged(): Boolean {
        if (this is CityCombatant) return true
        return (this as MapUnitCombatant).unit.baseUnit.isRanged()
    }
    @Readonly
    fun isAirUnit(): Boolean {
        if (this is CityCombatant) return false
        return (this as MapUnitCombatant).unit.baseUnit.isAirUnit()
    }
    @Readonly
    fun isWaterUnit(): Boolean {
        if (this is CityCombatant) return false
        return (this as MapUnitCombatant).unit.baseUnit.isWaterUnit
    }
    @Readonly
    fun isLandUnit(): Boolean {
        if (this is CityCombatant) return false
        return (this as MapUnitCombatant).unit.baseUnit.isLandUnit
    }
    @Readonly fun isCity(): Boolean = this is CityCombatant
    @Readonly fun isCivilian() = this is MapUnitCombatant && this.unit.isCivilian()
}
