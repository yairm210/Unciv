package com.unciv.logic.battle

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
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
    fun getCivInfo(): Civilization
    fun getTile(): Tile
    fun isInvisible(to: Civilization): Boolean
    fun canAttack(): Boolean
    /** Implements [UniqueParameterType.CombatantFilter][com.unciv.models.ruleset.unique.UniqueParameterType.CombatantFilter] */
    fun matchesFilter(filter: String, multiFilter: Boolean = true): Boolean
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
        return (this as MapUnitCombatant).unit.baseUnit.isWaterUnit
    }
    fun isLandUnit(): Boolean {
        if (this is CityCombatant) return false
        return (this as MapUnitCombatant).unit.baseUnit.isLandUnit
    }
    fun isCity(): Boolean {
        return this is CityCombatant
    }
    fun isCivilian() = this is MapUnitCombatant && this.unit.isCivilian()
}
