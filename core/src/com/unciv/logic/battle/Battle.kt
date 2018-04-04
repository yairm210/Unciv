package com.unciv.logic.battle

import com.unciv.logic.GameInfo
import java.util.*

class Battle(val gameInfo:GameInfo) {

    fun calculateDamageToAttacker(attacker: ICombatant, defender: ICombatant): Int {
        return defender.getDefendingStrength(attacker) * 50 / attacker.getAttackingStrength(defender)
    }

    fun calculateDamageToDefender(attacker: ICombatant, defender: ICombatant): Int {
        return attacker.getAttackingStrength(defender)*50/defender.getDefendingStrength(attacker)
    }

    fun attack(attacker: ICombatant, defender: ICombatant) {
        val attackedTile = defender.getTile()

        var damageToDefender = calculateDamageToDefender(attacker,defender)
        var damageToAttacker = calculateDamageToAttacker(attacker,defender)

        if (attacker.getCombatantType() == CombatantType.Ranged) {
            defender.takeDamage(damageToDefender) // straight up
        } else {
            //melee attack is complicated, because either side may defeat the other midway
            //so...for each round, we randomize who gets the attack in. Seems to be a good way to work for now.


            while (damageToDefender + damageToAttacker > 0) {
                if (Random().nextInt(damageToDefender + damageToAttacker) < damageToDefender) {
                    damageToDefender--
                    defender.takeDamage(1)
                    if (defender.isDefeated()) break
                } else {
                    damageToAttacker--
                    attacker.takeDamage(1)
                    if (attacker.isDefeated()) break
                }
            }
        }

        // After dust as settled

        if (defender.getCivilization().isPlayerCivilization()) {
            val whatHappenedString =
                    if (attacker.isDefeated()) " was destroyed while attacking"
                    else " has " + (if (defender.isDefeated()) "destroyed" else "attacked")
            val defenderString =
                    if (defender.getCombatantType() == CombatantType.City) defender.getName()
                    else " our " + defender.getName()
            val notificationString = "An enemy " + attacker.getName() + whatHappenedString + defenderString
            gameInfo.addNotification(notificationString, attackedTile.position)
        }

        if (defender.isDefeated() && attacker.getCombatantType() == CombatantType.Melee)
            (attacker as MapUnitCombatant).unit.moveToTile(attackedTile)

        if(attacker is MapUnitCombatant) attacker.unit.currentMovement = 0f
    }

}