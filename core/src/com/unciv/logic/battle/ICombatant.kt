package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo

interface ICombatant{
    fun getName(): String
    fun getHealth():Int
    fun getCombatantType(): CombatantType
    fun getAttackingStrength(defender: ICombatant): Int
    fun getDefendingStrength(attacker: ICombatant): Int
    fun takeDamage(damage:Int)
    fun isDefeated():Boolean
    fun getCivilization(): CivilizationInfo
    fun getTile(): TileInfo
}