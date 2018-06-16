package com.unciv.logic.battle

import com.unciv.models.gamebasics.unit.UnitType


class BattleDamage{

    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant): HashMap<String, Float> {
        val modifiers = HashMap<String, Float>()
        if (combatant is MapUnitCombatant) {
            val uniques = combatant.unit.getBaseUnit().uniques
            if (uniques != null) {
                // This beut allows  us to have generic unit uniques: "Bonus vs City 75%", "Penatly vs Mounted 25%" etc.
                for (unique in uniques) {
                    val regexResult = Regex("""(Bonus|Penalty) vs (\S*) (\d*)%""").matchEntire(unique)
                    if (regexResult == null) continue
                    val vsType = UnitType.valueOf(regexResult.groups[2]!!.value)
                    val modificationAmount = regexResult.groups[3]!!.value.toFloat() / 100  // if it says 15%, that's 0.15f in modification
                    if (enemy.getUnitType() == vsType) {
                        if (regexResult.groups[1]!!.value == "Bonus")
                            modifiers["Bonus vs $vsType"] = modificationAmount
                        else modifiers["Penalty vs $vsType"] = -modificationAmount
                    }
                }
            }

            if(enemy.getCivilization().isBarbarianCivilization())
                modifiers["vs Barbarians"] = 0.33f

            if(combatant.getCivilization().happiness<0)
                modifiers["Unhappiness"] = 0.02f * combatant.getCivilization().happiness  //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
        }

        return modifiers
    }

    fun getAttackModifiers(attacker: ICombatant, defender: ICombatant): HashMap<String, Float> {
        val modifiers = getGeneralModifiers(attacker, defender)
        if (attacker.isMelee()) {
            val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                it.militaryUnit != null
                        && it.militaryUnit!!.owner == attacker.getCivilization().civName
                        && MapUnitCombatant(it.militaryUnit!!).isMelee()
            }
            if (numberOfAttackersSurroundingDefender > 1)
                modifiers["Flanking"] = 0.1f * (numberOfAttackersSurroundingDefender-1) //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
        }

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: ICombatant): HashMap<String, Float> {
        val modifiers = getGeneralModifiers(defender, attacker)
        if (!(defender is MapUnitCombatant && defender.unit.hasUnique("No defensive terrain bonus"))) {
            val tileDefenceBonus = defender.getTile().getDefensiveBonus()
            if (tileDefenceBonus > 0) modifiers["Terrain"] = tileDefenceBonus
        }
        if(defender is MapUnitCombatant && defender.unit.isFortified())
            modifiers["Fortification"]=0.2f*defender.unit.getFortificationTurns()
        return modifiers
    }

    private fun modifiersToMultiplicationBonus(modifiers: HashMap<String, Float>): Float {
        // modifiers are like 0.1 for a 10% bonus, -0.1 for a 10% loss
        var modifier = 1f
        for (m in modifiers.values) modifier *= (1 + m)
        return modifier
    }

    private fun getHealthDependantDamageRatio(combatant: ICombatant): Float {
        if (combatant.getUnitType() == UnitType.City) return 1f
        return 1/2f + combatant.getHealth()/200f // Each point of health reduces damage dealt by 0.5%
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
        if(attacker.isRanged()) return 0
        if(defender.getUnitType()== UnitType.Civilian) return 0
        val ratio = getDefendingStrength(attacker,defender) / getAttackingStrength(attacker,defender)
        return (ratio * 30 * getHealthDependantDamageRatio(defender)).toInt()
    }

    fun calculateDamageToDefender(attacker: ICombatant, defender: ICombatant): Int {
        val ratio = getAttackingStrength(attacker,defender) / getDefendingStrength(attacker,defender)
        return (ratio * 30 * getHealthDependantDamageRatio(attacker)).toInt()
    }
}