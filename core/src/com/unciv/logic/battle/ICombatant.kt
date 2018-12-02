package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType

interface ICombatant{
    fun getName(): String
    fun getHealth():Int
    fun getUnitType(): UnitType
    fun getAttackingStrength(): Int
    fun getDefendingStrength(): Int
    fun takeDamage(damage:Int)
    fun isDefeated():Boolean
    fun getCivilization(): CivilizationInfo
    fun getTile(): TileInfo

    fun isMelee(): Boolean {
        return this.getUnitType().isMelee()
    }
    fun isRanged(): Boolean {
        return this.getUnitType().isRanged()
    }
}