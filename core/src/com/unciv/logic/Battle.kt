package com.unciv.logic

import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.UnitType
import java.util.*

class Battle{
    fun calculateDamage(attacker:MapUnit, defender:MapUnit): Int {

        // TODO:
        // Terrain and city defence bonuses
        

        val attackerStrength =
                if (attacker.getBaseUnit().unitType ==UnitType.Ranged)
                    attacker.getBaseUnit().rangedStrength
                else attacker.getBaseUnit().strength
        return (attackerStrength*attacker.health*50) / (defender.getBaseUnit().strength*defender.health)
    }

    fun attack(attacker: MapUnit, defender: MapUnit){

        var damageToDefender = calculateDamage(attacker,defender)
        var damageToAttacker = calculateDamage(defender,attacker)

        if(attacker.getBaseUnit().unitType == UnitType.Ranged){
            defender.health -= damageToDefender  // straight up
        }
        else {
            //melee attack is complicated, because either side may defeat the other midway
            //so...for each round, we randomize who gets the attack in. Seems to be a good way to work for now.

            attacker.headTowards(defender.getTile().position)
            while(damageToDefender+damageToAttacker>0) {
                if (Random().nextInt(damageToDefender + damageToAttacker) < damageToDefender) {
                    damageToDefender--
                    defender.health--
                    if(defender.health==0) break
                }
                else{
                    damageToAttacker--
                    attacker.health--
                    if(attacker.health==0) break
                }
            }
        }

        // After dust as settled

        val defenderDestroyed = defender.health <= 0
        val attackerDestroyed = attacker.health <= 0

        if(defender.civInfo.isPlayerCivilization()) {
            val whatHappenedString =
                if (attackerDestroyed) " was destroyed while attacking"
                else  " has " + (if (defenderDestroyed) "destroyed" else "attacked")
            val notificationString = "An enemy " + attacker.name + whatHappenedString + " our " + defender.name
            defender.civInfo.gameInfo.addNotification(notificationString, defender.getTile().position)
        }

        if(defenderDestroyed) {
            val defenderTile = defender.getTile()
            defenderTile.unit = null // Ded
            if (attacker.getBaseUnit().unitType != UnitType.Ranged)
                attacker.moveToTile(defenderTile)
        }
        attacker.currentMovement=0f


        if (attackerDestroyed) attacker.getTile().unit = null
    }

}