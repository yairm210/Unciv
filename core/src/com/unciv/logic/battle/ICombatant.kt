package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.UnitType

interface ICombatant{
    fun getName(): String
    fun getHealth():Int
    fun getMaxHealth():Int
    fun getUnitType(): UnitType
    fun getAttackingStrength(): Int
    fun getDefendingStrength(): Int
    fun takeDamage(damage:Int)
    fun isDefeated():Boolean
    fun getCivInfo(): CivilizationInfo
    fun getTile(): TileInfo
    fun isInvisible(): Boolean
    fun canAttack(): Boolean
    fun matchesCategory(category:String): Boolean
    fun getAttackSound(): UncivSound

    fun isMelee(): Boolean = !isRanged()
    fun isRanged(): Boolean {
        if (this is CityCombatant) return true
        return (this as MapUnitCombatant).unit.baseUnit.isRanged()
    }
    fun isCivilian() = this is MapUnitCombatant && this.unit.isCivilian()
}