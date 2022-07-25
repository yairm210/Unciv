package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.UnitType

interface ICombatant {
    fun getName(): String
    fun getHealth(): Int
    fun getMaxHealth(): Int
    fun getUnitType(): UnitType
    fun getAttackingStrength(): Int
    fun getDefendingStrength(attackedByRanged: Boolean = false): Int
    fun takeDamage(damage: Int)
    fun isDefeated(): Boolean
    fun getCivInfo(): CivilizationInfo
    fun getTile(): TileInfo
    fun isInvisible(to: CivilizationInfo): Boolean
    fun canAttack(): Boolean
    /** Implements [UniqueParameterType.CombatantFilter][com.unciv.models.ruleset.unique.UniqueParameterType.CombatantFilter] */
    fun matchesCategory(category: String): Boolean
    fun getAttackSound(): UncivSound

    fun isMelee(): Boolean = !isRanged()
    fun isRanged(): Boolean {
        if (this is CityCombatant) return true
        return (this as MapUnitCombatant).unit.baseUnit.isRanged()
    }
    fun isAirUnit(): Boolean {
        if (this is CityCombatant) return false
        return (this as MapUnitCombatant).unit.baseUnit.isAirUnit()
    }
    fun isWaterUnit(): Boolean {
        if (this is CityCombatant) return false
        return (this as MapUnitCombatant).unit.baseUnit.isWaterUnit()
    }
    fun isLandUnit(): Boolean {
        if (this is CityCombatant) return false
        return (this as MapUnitCombatant).unit.baseUnit.isLandUnit()
    }
    fun isCity(): Boolean {
        return this is CityCombatant
    }
    fun isCivilian() = this is MapUnitCombatant && this.unit.isCivilian()
}
