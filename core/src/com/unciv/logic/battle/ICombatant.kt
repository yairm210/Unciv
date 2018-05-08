package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType

interface ICombatant{
    fun getName(): String
    fun getHealth():Int
    fun getUnitType(): UnitType
    fun getAttackingStrength(defender: ICombatant): Int
    fun getDefendingStrength(attacker: ICombatant): Int
    fun takeDamage(damage:Int)
    fun isDefeated():Boolean
    fun getCivilization(): CivilizationInfo
    fun getTile(): TileInfo

    fun isMelee(): Boolean {
        return this.getUnitType() in listOf(UnitType.Melee,UnitType.Mounted)
    }
    fun isRanged(): Boolean {
        return this.getUnitType() in listOf(UnitType.Archery,UnitType.Siege)
    }
}