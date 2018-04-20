package com.unciv.logic.battle

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType

class MapUnitCombatant(val unit: MapUnit) : ICombatant {
    override fun getHealth(): Int = unit.health
    override fun getCivilization(): CivilizationInfo = unit.civInfo
    override fun getTile(): TileInfo = unit.getTile()
    override fun getName(): String = unit.name
    override fun isDefeated(): Boolean = unit.health <= 0

    override fun takeDamage(damage: Int) {
        unit.health -= damage
        if(isDefeated()) unit.getTile().unit=null
    }

    override fun getAttackingStrength(defender: ICombatant): Int {
        val attackerStrength =
                if (unit.getBaseUnit().unitType == UnitType.Ranged)
                    unit.getBaseUnit().rangedStrength
                else unit.getBaseUnit().strength
        return attackerStrength*unit.health
    }

    override fun getDefendingStrength(attacker: ICombatant): Int {
        // too: if ranged units get ranged attacked, they use their ranged str to defend!
        return unit.getBaseUnit().strength*unit.health
    }

    override fun getCombatantType(): CombatantType {
        when(unit.getBaseUnit().unitType){
            UnitType.Melee -> return CombatantType.Melee
            UnitType.Ranged -> return CombatantType.Ranged
            UnitType.Civilian -> return CombatantType.Civilian
            else -> throw Exception("Should never get here!")
        }
    }
}