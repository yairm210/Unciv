package com.unciv.logic.battle

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import java.util.*
import kotlin.math.max

/**
 * Damage calculations according to civ v wiki and https://steamcommunity.com/sharedfiles/filedetails/?id=170194443
 */
class Battle(val gameInfo:GameInfo) {

    fun moveAndAttack(attacker: ICombatant, attackableTile: UnitAutomation.AttackableTile){
        if (attacker is MapUnitCombatant) {
            attacker.unit.movement.moveToTile(attackableTile.tileToAttackFrom)
            if (attacker.unit.hasUnique("Must set up to ranged attack") && attacker.unit.action != Constants.unitActionSetUp) {
                attacker.unit.action = Constants.unitActionSetUp
                attacker.unit.useMovementPoints(1f)
            }
        }
        attack(attacker,getMapCombatantOfTile(attackableTile.tileToAttack)!!)
    }

    fun attack(attacker: ICombatant, defender: ICombatant) {
        println(attacker.getCivInfo().civName+" "+attacker.getName()+" attacked "+defender.getCivInfo().civName+" "+defender.getName())
        val attackedTile = defender.getTile()

        if(attacker is MapUnitCombatant && attacker.getUnitType().isAirUnit()){
            tryInterceptAirAttack(attacker,defender)
            if(attacker.isDefeated()) return
        }

        var damageToDefender = BattleDamage().calculateDamageToDefender(attacker,defender)
        var damageToAttacker = BattleDamage().calculateDamageToAttacker(attacker,defender)

        if (attacker.getUnitType().isMissileUnit()) {
            nuclearBlast(attacker, defender)
        }
        else if(defender.getUnitType().isCivilian() && attacker.isMelee()){
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

    private fun postBattleAction(attacker: ICombatant, defender: ICombatant, attackedTile:TileInfo) {

        postBattleNotifications(attacker, defender, attackedTile)

        tryHealAfterAttacking(attacker, defender)

        postBattleNationUniques(defender, attackedTile, attacker)

        // This needs to come BEFORE the move-to-tile, because if we haven't conquered it we can't move there =)
        if (defender.isDefeated() && defender is CityCombatant && attacker.isMelee())
            conquerCity(defender.city, attacker)

        // we're a melee unit and we destroyed\captured an enemy unit
        postBattleMoveToAttackedTile(attacker, defender, attackedTile)

        reduceAttackerMovementPointsAndAttacks(attacker, defender)

        postBattleAddXp(attacker, defender)

        // Add culture when defeating a barbarian when Honor policy is adopted (can be either attacker or defender!)
        tryGetCultureFromHonor(attacker, defender)
        tryGetCultureFromHonor(defender, attacker)

        if (defender.isDefeated() && defender is MapUnitCombatant && !defender.getUnitType().isCivilian()
                && attacker.getCivInfo().policies.isAdopted("Honor Complete"))
            attacker.getCivInfo().gold += defender.unit.baseUnit.getProductionCost(attacker.getCivInfo()) / 10

        if (attacker is MapUnitCombatant && attacker.unit.action != null
                && attacker.unit.action!!.startsWith("moveTo"))
            attacker.unit.action = null

        if (attacker is MapUnitCombatant) {
            if (attacker.getUnitType().isMissileUnit()) {
                attacker.unit.destroy()
            } else if (attacker.unit.action != null
                    && attacker.unit.action!!.startsWith("moveTo")) {
                attacker.unit.action = null
            }
        }
    }

    private fun postBattleNotifications(attacker: ICombatant, defender: ICombatant, attackedTile: TileInfo) {
        if (attacker.getCivInfo() != defender.getCivInfo()) { // If what happened was that a civilian unit was captures, that's dealt with in the CaptureCilvilianUnit function
            val whatHappenedString =
                    if (attacker !is CityCombatant && attacker.isDefeated()) " was destroyed while attacking"
                    else " has " + (
                            if (defender.isDefeated())
                                if (defender.getUnitType() == UnitType.City && attacker.isMelee())
                                    "captured"
                                else "destroyed"
                            else "attacked")
            val attackerString =
                    if (attacker.getUnitType() == UnitType.City) "Enemy city [" + attacker.getName() + "]"
                    else "An enemy [" + attacker.getName() + "]"
            val defenderString =
                    if (defender.getUnitType() == UnitType.City)
                        if (defender.isDefeated() && attacker.isRanged()) " the defence of [" + defender.getName() + "]"
                        else " [" + defender.getName() + "]"
                    else " our [" + defender.getName() + "]"
            val notificationString = attackerString + whatHappenedString + defenderString
            defender.getCivInfo().addNotification(notificationString, attackedTile.position, Color.RED)
        }
    }

    private fun tryHealAfterAttacking(attacker: ICombatant, defender: ICombatant) {
        if (defender.isDefeated()
                && defender is MapUnitCombatant
                && attacker is MapUnitCombatant) {
            val regex = Regex("""Heals \[(\d*)\] damage if it kills a unit"""")
            for (unique in attacker.unit.getUniques()) {
                val match = regex.matchEntire(unique)
                if (match == null) continue
                val amountToHeal = match.groups[1]!!.value.toInt()
                attacker.unit.healBy(amountToHeal)
            }
        }
    }

    private fun postBattleNationUniques(defender: ICombatant, attackedTile: TileInfo, attacker: ICombatant) {
        // German unique - needs to be checked before we try to move to the enemy tile, since the encampment disappears after we move in
        if (defender.isDefeated() && defender.getCivInfo().isBarbarian()
                && attackedTile.improvement == Constants.barbarianEncampment
                && attacker.getCivInfo().nation.unique == "67% chance to earn 25 Gold and recruit a Barbarian unit from a conquered encampment, -25% land units maintenance."
                && Random().nextDouble() > 0.67) {
            attacker.getCivInfo().placeUnitNearTile(attackedTile.position, defender.getName())
            attacker.getCivInfo().gold += 25
            attacker.getCivInfo().addNotification("A barbarian [${defender.getName()}] has joined us!", attackedTile.position, Color.RED)
        }

        // Similarly, Ottoman unique
        if (defender.isDefeated() && defender.getUnitType().isWaterUnit() && attacker.isMelee() && attacker.getUnitType().isWaterUnit()
                && attacker.getCivInfo().nation.unique == "Pay only one third the usual cost for naval unit maintenance. Melee naval units have a 1/3 chance to capture defeated naval units."
                && Random().nextDouble() > 0.33) {
            attacker.getCivInfo().placeUnitNearTile(attackedTile.position, defender.getName())
        }
    }

    private fun postBattleMoveToAttackedTile(attacker: ICombatant, defender: ICombatant, attackedTile: TileInfo) {
        if (attacker.isMelee()
                && (defender.isDefeated() || defender.getCivInfo() == attacker.getCivInfo())
                // This is so that if we attack e.g. a barbarian in enemy territory that we can't enter, we won't enter it
                && (attacker as MapUnitCombatant).unit.movement.canMoveTo(attackedTile)) {
            // we destroyed an enemy military unit and there was a civilian unit in the same tile as well
            if (attackedTile.civilianUnit != null && attackedTile.civilianUnit!!.civInfo != attacker.getCivInfo())
                captureCivilianUnit(attacker, MapUnitCombatant(attackedTile.civilianUnit!!))
            attacker.unit.movement.moveToTile(attackedTile)
        }
    }

    private fun postBattleAddXp(attacker: ICombatant, defender: ICombatant) {
        if (attacker.isMelee()) {
            if (!defender.getUnitType().isCivilian()) // unit was not captured but actually attacked
            {
                addXp(attacker, 5, defender)
                addXp(defender, 4, attacker)
            }
        } else { // ranged attack
            addXp(attacker, 2, defender)
            addXp(defender, 2, attacker)
        }
    }

    private fun reduceAttackerMovementPointsAndAttacks(attacker: ICombatant, defender: ICombatant) {
        if (attacker is MapUnitCombatant) {
            val unit = attacker.unit
            if (unit.hasUnique("Can move after attacking")
                    || (unit.hasUnique("1 additional attack per turn") && unit.attacksThisTurn == 0)) {
                // if it was a melee attack and we won, then the unit ALREADY got movement points deducted,
                // for the movement to the enemy's tile!
                // and if it's an air unit, it only has 1 movement anyway, so...
                if (!attacker.getUnitType().isAirUnit() && !(attacker.getUnitType().isMelee() && defender.isDefeated()))
                    unit.useMovementPoints(1f)
            } else unit.currentMovement = 0f
            unit.attacksThisTurn += 1
            if (unit.isFortified() || unit.action == Constants.unitActionSleep)
                attacker.unit.action = null // but not, for instance, if it's Set Up - then it should definitely keep the action!
        } else if (attacker is CityCombatant) {
            attacker.city.attackedThisTurn = true
        }
    }

    private fun tryGetCultureFromHonor(civUnit:ICombatant, barbarianUnit:ICombatant){
        if(barbarianUnit.isDefeated() && barbarianUnit is MapUnitCombatant
                && barbarianUnit.getCivInfo().isBarbarian()
                && civUnit.getCivInfo().policies.isAdopted("Honor"))
            civUnit.getCivInfo().policies.storedCulture +=
                    max(barbarianUnit.unit.baseUnit.strength,barbarianUnit.unit.baseUnit.rangedStrength)
    }

    // XP!
    private fun addXp(thisCombatant:ICombatant, amount:Int, otherCombatant:ICombatant){
        if(thisCombatant !is MapUnitCombatant) return
        if(thisCombatant.unit.promotions.totalXpProduced() >= 30 && otherCombatant.getCivInfo().isBarbarian())
            return
        var amountToAdd = amount
        if(thisCombatant.getCivInfo().policies.isAdopted("Military Tradition")) amountToAdd = (amountToAdd * 1.5f).toInt()
        thisCombatant.unit.promotions.XP += amountToAdd

        if(thisCombatant.getCivInfo().nation.unique
                == "Great general provides double combat bonus, and spawns 50% faster")
            amountToAdd = (amountToAdd * 1.5f).toInt()
        if(thisCombatant.unit.hasUnique("Combat very likely to create Great Generals"))
            amountToAdd *= 2

        thisCombatant.getCivInfo().greatPeople.greatGeneralPoints += amountToAdd
    }

    private fun conquerCity(city: CityInfo, attacker: ICombatant) {
        val attackerCiv = attacker.getCivInfo()

        attackerCiv.addNotification("We have conquered the city of [${city.name}]!", city.location, Color.RED)

        city.getCenterTile().apply {
            if(militaryUnit!=null) militaryUnit!!.destroy()
            if(civilianUnit!=null) captureCivilianUnit(attacker, MapUnitCombatant(civilianUnit!!))
            for(airUnit in airUnits.toList()) airUnit.destroy()
        }
        city.hasJustBeenConquered = true

        if (attackerCiv.isPlayerCivilization())
            attackerCiv.popupAlerts.add(PopupAlert(AlertType.CityConquered, city.name))
        else {
            city.puppetCity(attackerCiv)
            if (city.population.population < 4) {
                city.annexCity()
                city.isBeingRazed = true
            }
        }
    }

    fun getMapCombatantOfTile(tile:TileInfo): ICombatant? {
        if(tile.isCityCenter()) return CityCombatant(tile.getCity()!!)
        if(tile.militaryUnit!=null) return MapUnitCombatant(tile.militaryUnit!!)
        if(tile.civilianUnit!=null) return MapUnitCombatant(tile.civilianUnit!!)
        return null
    }

    private fun captureCivilianUnit(attacker: ICombatant, defender: ICombatant){
        // barbarians don't capture civilians
        if(attacker.getCivInfo().isBarbarian()){
            defender.takeDamage(100)
            return
        }

        if (defender.getCivInfo().isDefeated()) {//Last settler captured
            defender.getCivInfo().destroy()
            attacker.getCivInfo().popupAlerts.add(PopupAlert(AlertType.Defeated,defender.getCivInfo().civName))
        }
        
        val capturedUnit = (defender as MapUnitCombatant).unit
        capturedUnit.civInfo.addNotification("An enemy ["+attacker.getName()+"] has captured our ["+defender.getName()+"]",
                defender.getTile().position, Color.RED)

        // Apparently in Civ V, captured settlers are converted to workers.
        if(capturedUnit.name==Constants.settler){
            val tile = capturedUnit.getTile()
            capturedUnit.destroy()
            attacker.getCivInfo().placeUnitNearTile(tile.position, Constants.worker)
        }
        else {
            capturedUnit.civInfo.removeUnit(capturedUnit)
            capturedUnit.assignOwner(attacker.getCivInfo())
        }
        capturedUnit.updateVisibleTiles()
    }

    private fun nuclearBlast(attacker: ICombatant, defender: ICombatant) {
        for (tile in defender.getTile().getTilesInDistance(2)) {
            if (tile.isCityCenter()) { //duantao: To Do
                val city = tile.getCity()!!
                city.health = 1
                if (city.population.population <= 5) {
                    city.destroyCity()
                } else {
                    city.population.population -= 5
                    city.population.unassignExtraPopulation()
                    continue
                }
            }

            if (tile.militaryUnit != null) tile.militaryUnit!!.destroy()
            if (tile.civilianUnit != null) tile.civilianUnit!!.destroy()
            tile.improvement = null
            tile.improvementInProgress = null
            tile.turnsToImprovement = 0
            tile.roadStatus = RoadStatus.None
            if (tile.isLand) tile.terrainFeature = "Fallout"
        }
    }

    private fun tryInterceptAirAttack(attacker:MapUnitCombatant, defender: ICombatant) {
        val attackedTile = defender.getTile()
        for (interceptor in defender.getCivInfo().getCivUnits().filter { it.canIntercept(attackedTile) }) {
            if (Random().nextFloat() > 100f / interceptor.interceptChance()) continue

            var damage = BattleDamage().calculateDamageToDefender(MapUnitCombatant(interceptor), attacker)
            damage += damage * interceptor.interceptDamagePercentBonus() / 100
            if (attacker.unit.hasUnique("Reduces damage taken from interception by 50%")) damage /= 2

            attacker.takeDamage(damage)
            interceptor.attacksThisTurn++

            val attackerName = attacker.getName()
            val interceptorName = interceptor.name

            if (attacker.isDefeated()) {
                attacker.getCivInfo()
                        .addNotification("Our [$attackerName] was destroyed by an intercepting [$interceptorName]",
                        Color.RED)
                defender.getCivInfo()
                        .addNotification("Our [$interceptorName] intercepted and destroyed an enemy [$attackerName]",
                        interceptor.currentTile.position, Color.RED)
            } else {
                attacker.getCivInfo()
                        .addNotification("Our [$attackerName] was attacked by an intercepting [$interceptorName]",
                        Color.RED)
                defender.getCivInfo()
                        .addNotification("Our [$interceptorName] intercepted and attacked an enemy [$attackerName]",
                        interceptor.currentTile.position, Color.RED)
            }
            return
        }
    }
}
