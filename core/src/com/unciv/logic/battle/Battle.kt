package com.unciv.logic.battle

import com.unciv.logic.GameInfo
import com.unciv.logic.map.UnitType
import java.util.*
import kotlin.collections.HashMap

class Battle(val gameInfo:GameInfo) {

    fun getAttackModifiers(attacker: ICombatant, defender: ICombatant): HashMap<String, Float> {
        val modifiers = HashMap<String,Float>()
        if(attacker.getCombatantType()==CombatantType.Melee) {
            val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                it.unit != null
                        && it.unit!!.owner == attacker.getCivilization().civName
                        && it.unit!!.getBaseUnit().unitType == UnitType.Melee
            }
            if(numberOfAttackersSurroundingDefender >1) modifiers.put("Flanking",0.15f)
        }

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: ICombatant): HashMap<String, Float> {
        val modifiers = HashMap<String,Float>()
        val tileDefenceBonus = defender.getTile().getDefensiveBonus()
        if(tileDefenceBonus > 0) modifiers.put("Terrain",tileDefenceBonus)
        return modifiers
    }

    fun modifiersToMultiplicationBonus(modifiers:HashMap<String,Float> ):Float{
        // modifiers are like 0.1 for a 10% bonus, -0.1 for a 10% loss
        var modifier = 1f
        for(m in modifiers.values) modifier *= (1+m)
        return modifier
    }

    /**
     * Includes attack modifiers
     */
    fun getAttackingStrength(attacker: ICombatant, defender: ICombatant): Float {
        val attackModifier = modifiersToMultiplicationBonus(getAttackModifiers(attacker,defender))
        return attacker.getAttackingStrength(defender) * attackModifier
    }


    /**
     * Includes defence modifiers
     */
    fun getDefendingStrength(attacker: ICombatant, defender: ICombatant): Float {
        val defenceModifier = modifiersToMultiplicationBonus(getDefenceModifiers(attacker,defender))
        return defender.getDefendingStrength(attacker) * defenceModifier
    }

    fun calculateDamageToAttacker(attacker: ICombatant, defender: ICombatant): Int {
        return (getDefendingStrength(attacker,defender) * 50 / getAttackingStrength(attacker,defender)).toInt()
    }

    fun calculateDamageToDefender(attacker: ICombatant, defender: ICombatant): Int {
        return (getAttackingStrength(attacker, defender)*50/ getDefendingStrength(attacker,defender)).toInt()
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