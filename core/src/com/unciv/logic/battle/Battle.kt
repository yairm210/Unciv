package com.unciv.logic.battle

import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
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
            if(numberOfAttackersSurroundingDefender >1) modifiers["Flanking"] = 0.15f
        }

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: ICombatant): HashMap<String, Float> {
        val modifiers = HashMap<String,Float>()
        val tileDefenceBonus = defender.getTile().getDefensiveBonus()
        if(tileDefenceBonus > 0) modifiers["Terrain"] = tileDefenceBonus
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
        if(attacker.getCombatantType() == CombatantType.Ranged) return 0
        return (getDefendingStrength(attacker,defender) * 50 / getAttackingStrength(attacker,defender)).toInt()
    }

    fun calculateDamageToDefender(attacker: ICombatant, defender: ICombatant): Int {
        return (getAttackingStrength(attacker, defender)*50/ getDefendingStrength(attacker,defender)).toInt()
    }

    fun attack(attacker: ICombatant, defender: ICombatant) {
        val attackedTile = defender.getTile()

        var damageToDefender = calculateDamageToDefender(attacker,defender)
        var damageToAttacker = calculateDamageToAttacker(attacker,defender)

        if(defender.getCombatantType() == CombatantType.Civilian){
            defender.takeDamage(100) // kill
        }
        else if (attacker.getCombatantType() == CombatantType.Ranged) {
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

        postBattleAction(attacker,defender,attackedTile)

    }

    fun postBattleAction(attacker: ICombatant, defender: ICombatant, attackedTile:TileInfo){

        if (defender.getCivilization().isPlayerCivilization()) {
            val whatHappenedString =
                    if (attacker.isDefeated()) " was destroyed while attacking"
                    else " has " + (if (defender.isDefeated()) "destroyed" else "attacked")
            val defenderString =
                    if (defender.getCombatantType() == CombatantType.City) defender.getName()
                    else " our " + defender.getName()
            val notificationString = "An enemy " + attacker.getName() + whatHappenedString + defenderString
            gameInfo.getPlayerCivilization().addNotification(notificationString, attackedTile.position)
        }


        if(defender.isDefeated()
                && defender.getCombatantType() == CombatantType.City
                && attacker.getCombatantType() == CombatantType.Melee){
            conquerCity((defender as CityCombatant).city, attacker)
        }

        if (defender.isDefeated() && attacker.getCombatantType() == CombatantType.Melee)
            (attacker as MapUnitCombatant).unit.moveToTile(attackedTile)

        if(attacker is MapUnitCombatant) attacker.unit.currentMovement = 0f
    }

    private fun conquerCity(city: CityInfo, attacker: ICombatant) {
        val enemyCiv = city.civInfo
        attacker.getCivilization().addNotification("We have conquered the city of ${city.name}!",city.location)
        enemyCiv.cities.remove(city)
        attacker.getCivilization().cities.add(city)
        city.civInfo = attacker.getCivilization()
        city.health = city.getMaxHealth() / 2 // I think that cities recover to half health?
        city.getCenterTile().unit = null
        city.expansion.cultureStored = 0;
        city.expansion.reset()

        // now that the tiles have changed, we need to reassign population
        city.workedTiles.filterNot { city.tiles.contains(it) }
                .forEach { city.workedTiles.remove(it); city.population.autoAssignPopulation() }

        if(city.cityConstructions.isBuilt("Palace")){
            city.cityConstructions.builtBuildings.remove("Palace")
            if(enemyCiv.cities.isEmpty()) {
                gameInfo.getPlayerCivilization()
                        .addNotification("The civilization of ${enemyCiv.civName} has been destroyed!", null)
            }
            else{
                enemyCiv.cities.first().cityConstructions.builtBuildings.add("Palace") // relocate palace
            }
        }
        (attacker as MapUnitCombatant).unit.moveToTile(city.getCenterTile())
        city.civInfo.gameInfo.updateTilesToCities()
    }

}