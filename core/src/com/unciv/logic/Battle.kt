package com.unciv.logic

import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.UnitType

/**
 * Created by LENOVO on 3/26/2018.
 */

class Battle(){
    fun calculateDamage(attacker:MapUnit, defender:MapUnit): Int {
        val attackerStrength =
                if (attacker.getBaseUnit().unitType ==UnitType.Ranged)
                    attacker.getBaseUnit().rangedStrength
                else attacker.getBaseUnit().strength
        return (attackerStrength*attacker.health*50) / (defender.getBaseUnit().strength*defender.health)
    }
}