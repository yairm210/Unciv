package com.unciv.logic

import com.unciv.logic.map.MapUnit

/**
 * Created by LENOVO on 3/26/2018.
 */

class Battle(){
    fun calculateDamage(attacker:MapUnit, defender:MapUnit): Int {
        return (attacker.strength*attacker.health*5) / (defender.strength*defender.health)
    }
}