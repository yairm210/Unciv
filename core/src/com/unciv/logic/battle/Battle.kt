package com.unciv.logic.battle

import com.badlogic.gdx.graphics.Color
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.max

/**
 * Damage calculations according to civ v wiki and https://steamcommunity.com/sharedfiles/filedetails/?id=170194443
 */
class Battle(val gameInfo:GameInfo=UnCivGame.Current.gameInfo) {

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

    fun attack(attacker: ICombatant, defender: ICombatant) {
        val attackedTile = defender.getTile()

        var damageToDefender = calculateDamageToDefender(attacker,defender)
        var damageToAttacker = calculateDamageToAttacker(attacker,defender)

        if(defender.getUnitType() == UnitType.Civilian && attacker.isMelee()){
            captureCivilianUnit(attacker,defender)
        }
        else if (attacker.isRanged()) {
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

    private fun postBattleAction(attacker: ICombatant, defender: ICombatant, attackedTile:TileInfo){

        if (defender.getCivilization().isPlayerCivilization()) {
            val whatHappenedString =
                    if (attacker.isDefeated()) " was destroyed while attacking"
                    else " has " + (if (defender.isDefeated()) "destroyed" else "attacked")
            val defenderString =
                    if (defender.getUnitType() == UnitType.City) " "+defender.getName()
                    else " our " + defender.getName()
            val notificationString = "An enemy " + attacker.getName() + whatHappenedString + defenderString
            gameInfo.getPlayerCivilization().addNotification(notificationString, attackedTile.position, Color.RED)
        }


        if(defender.isDefeated()
                && defender.getUnitType() == UnitType.City
                && attacker.isMelee()){
            conquerCity((defender as CityCombatant).city, attacker)
        }

        else if (attacker.isMelee() && (defender.isDefeated() || defender.getCivilization()==attacker.getCivilization() )) {
            if(attackedTile.civilianUnit!=null)
                captureCivilianUnit(attacker,MapUnitCombatant(attackedTile.civilianUnit!!))
            (attacker as MapUnitCombatant).unit.moveToTile(attackedTile)
        }

        if(attacker is MapUnitCombatant) {
            if (attacker.unit.hasUnique("Can move after attacking")){
                if(!attacker.getUnitType().isMelee() || !defender.isDefeated()) // if it was a melee attack and we won, then the unit ALREADY got movement points deducted, for the movement to the enemie's tile!
                    attacker.unit.currentMovement = max(0f, attacker.unit.currentMovement - 1)
            }
            else attacker.unit.currentMovement = 0f
            attacker.unit.attacksThisTurn+=1
            attacker.unit.action=null // for instance, if it was fortified
        }
    }

    private fun conquerCity(city: CityInfo, attacker: ICombatant) {
        val enemyCiv = city.civInfo
        attacker.getCivilization().addNotification("We have conquered the city of ${city.name}!",city.location, Color.RED)
        enemyCiv.cities.remove(city)
        attacker.getCivilization().cities.add(city)
        city.civInfo = attacker.getCivilization()
        city.health = city.getMaxHealth() / 2 // I think that cities recover to half health when conquered?
        city.getCenterTile().apply {
            militaryUnit = null
            if(civilianUnit!=null) captureCivilianUnit(attacker,MapUnitCombatant(civilianUnit!!))
        }

        city.expansion.cultureStored = 0
        city.expansion.reset()

        // now that the tiles have changed, we need to reassign population
        city.workedTiles.filterNot { city.tiles.contains(it) }
                .forEach { city.workedTiles.remove(it); city.population.autoAssignPopulation() }

        if(city.cityConstructions.isBuilt("Palace")){
            city.cityConstructions.builtBuildings.remove("Palace")
            if(enemyCiv.cities.isEmpty()) {
                gameInfo.getPlayerCivilization()
                        .addNotification("The civilization of ${enemyCiv.civName} has been destroyed!", null, Color.RED)
            }
            else{
                enemyCiv.cities.first().cityConstructions.builtBuildings.add("Palace") // relocate palace
            }
        }
        (attacker as MapUnitCombatant).unit.moveToTile(city.getCenterTile())
        city.civInfo.gameInfo.updateTilesToCities()
    }

    fun getMapCombatantOfTile(tile:TileInfo): ICombatant? {
        if(tile.isCityCenter()) return CityCombatant(tile.getCity()!!)
        if(tile.militaryUnit!=null) return MapUnitCombatant(tile.militaryUnit!!)
        if(tile.civilianUnit!=null) return MapUnitCombatant(tile.civilianUnit!!)
        return null
    }

    fun captureCivilianUnit(attacker: ICombatant, defender: ICombatant){
        if(attacker.getCivilization().isBarbarianCivilization()) defender.takeDamage(100) // barbarians don't capture civilians!
        val capturedUnit = (defender as MapUnitCombatant).unit
        capturedUnit.civInfo = attacker.getCivilization()
        capturedUnit.owner = capturedUnit.civInfo.civName
    }
}