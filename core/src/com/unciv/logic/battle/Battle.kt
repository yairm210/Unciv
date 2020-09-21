package com.unciv.logic.battle

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.AttackableTile
import com.unciv.models.ruleset.Unique
import com.unciv.models.ruleset.unit.UnitType
import java.util.*
import kotlin.math.max

/**
 * Damage calculations according to civ v wiki and https://steamcommunity.com/sharedfiles/filedetails/?id=170194443
 */
object Battle {

    fun moveAndAttack(attacker: ICombatant, attackableTile: AttackableTile){
        if (attacker is MapUnitCombatant) {
            attacker.unit.movement.moveToTile(attackableTile.tileToAttackFrom)
            if (attacker.unit.hasUnique("Must set up to ranged attack") && attacker.unit.action != Constants.unitActionSetUp) {
                attacker.unit.action = Constants.unitActionSetUp
                attacker.unit.useMovementPoints(1f)
            }
        }

        if (attacker.getUnitType() == UnitType.Missile) {
            return nuke(attacker, attackableTile.tileToAttack)
        }
        attack(attacker, getMapCombatantOfTile(attackableTile.tileToAttack)!!)
    }

    fun attack(attacker: ICombatant, defender: ICombatant) {
        if (UncivGame.Current.alertBattle) {
            println(attacker.getCivInfo().civName+" "+attacker.getName()+" attacked "+
                    defender.getCivInfo().civName+" "+defender.getName())
        }
        val attackedTile = defender.getTile()

        if(attacker is MapUnitCombatant && attacker.getUnitType().isAirUnit()) {
            tryInterceptAirAttack(attacker, defender)
            if (attacker.isDefeated()) return
        }

        // Withdraw from melee ability
        if (attacker is MapUnitCombatant && attacker.isMelee() && defender is MapUnitCombatant ) {
            val withdraw = defender.unit.getMatchingUniques("May withdraw before melee ([]%)").firstOrNull()
            if (withdraw != null && doWithdrawFromMeleeAbility(attacker, defender, withdraw)) return
        }

        val isAlreadyDefeatedCity = defender is CityCombatant && defender.isDefeated()

        takeDamage(attacker, defender)

        postBattleNotifications(attacker, defender, attackedTile)

        postBattleNationUniques(defender, attackedTile, attacker)

        // This needs to come BEFORE the move-to-tile, because if we haven't conquered it we can't move there =)
        if (defender.isDefeated() && defender is CityCombatant && attacker.isMelee())
            conquerCity(defender.city, attacker)

        // we're a melee unit and we destroyed\captured an enemy unit
        postBattleMoveToAttackedTile(attacker, defender, attackedTile)

        reduceAttackerMovementPointsAndAttacks(attacker, defender)

        if(!isAlreadyDefeatedCity) postBattleAddXp(attacker, defender)

        // Add culture when defeating a barbarian when Honor policy is adopted, gold from enemy killed when honor is complete
        // or any enemy military unit with Sacrificial captives unique (can be either attacker or defender!)
        if (defender.isDefeated() && defender is MapUnitCombatant && !defender.getUnitType().isCivilian()) {
            tryGetCultureFromKilling(attacker, defender)
            tryGetGoldFromKilling(attacker, defender)
            tryHealAfterKilling(attacker, defender)
        } else if (attacker.isDefeated() && attacker is MapUnitCombatant && !attacker.getUnitType().isCivilian()) {
            tryGetCultureFromKilling(defender, attacker)
            tryGetGoldFromKilling(defender, attacker)
            tryHealAfterKilling(defender, attacker)
        }

        if (attacker is MapUnitCombatant) {
            if (attacker.getUnitType() == UnitType.Missile)
                attacker.unit.destroy()
            else if (attacker.unit.isMoving())
                attacker.unit.action = null
        }
    }

    private fun takeDamage(attacker: ICombatant, defender: ICombatant) {
        var damageToDefender = BattleDamage.calculateDamageToDefender(attacker, attacker.getTile(), defender)
        var damageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, attacker.getTile(), defender)

        if (defender.getUnitType().isCivilian() && attacker.isMelee()) {
            captureCivilianUnit(attacker, defender)
        } else if (attacker.isRanged()) {
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

    private fun tryHealAfterKilling(attacker: ICombatant, defender: ICombatant) {
        if (attacker is MapUnitCombatant)
            for (unique in attacker.unit.getMatchingUniques("Heals [] damage if it kills a unit")) {
                val amountToHeal = unique.params[0].toInt()
                attacker.unit.healBy(amountToHeal)
            }
    }

    private fun postBattleNationUniques(defender: ICombatant, attackedTile: TileInfo, attacker: ICombatant) {
        // German unique - needs to be checked before we try to move to the enemy tile, since the encampment disappears after we move in
        if (defender.isDefeated() && defender.getCivInfo().isBarbarian()
                && attackedTile.improvement == Constants.barbarianEncampment
                && attacker.getCivInfo().hasUnique("67% chance to earn 25 Gold and recruit a Barbarian unit from a conquered encampment")
                && Random().nextDouble() > 0.67) {
            attacker.getCivInfo().placeUnitNearTile(attackedTile.position, defender.getName())
            attacker.getCivInfo().gold += 25
            attacker.getCivInfo().addNotification("A barbarian [${defender.getName()}] has joined us!", attackedTile.position, Color.RED)
        }

        // Similarly, Ottoman unique
        if (defender.isDefeated() && defender.getUnitType().isWaterUnit() && defender.getCivInfo().isBarbarian()
                && attacker.isMelee() && attacker.getUnitType().isWaterUnit()
                && attacker.getCivInfo().hasUnique("50% chance of capturing defeated Barbarian naval units and earning 25 Gold")
                && Random().nextDouble() > 0.5) {
            attacker.getCivInfo().placeUnitNearTile(attackedTile.position, defender.getName())
            attacker.getCivInfo().gold += 25
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
            if (unit.isFortified() || unit.isSleeping())
                attacker.unit.action = null // but not, for instance, if it's Set Up - then it should definitely keep the action!
        } else if (attacker is CityCombatant) {
            attacker.city.attackedThisTurn = true
        }
    }

    private fun tryGetCultureFromKilling(civUnit:ICombatant, defeatedUnit:MapUnitCombatant){
        //Aztecs get melee strength of the unit killed in culture and honor opener does the same thing.
        //They stack. So you get culture equal to 200% of the dead unit's strength.
        val civInfo = civUnit.getCivInfo()
        if (defeatedUnit.getCivInfo().isBarbarian() && civInfo.hasUnique("Gain Culture when you kill a barbarian unit"))
            civInfo.policies.addCulture(defeatedUnit.unit.baseUnit.strength)
        if (civInfo.hasUnique("Gains culture from each enemy unit killed"))
            civInfo.policies.addCulture(defeatedUnit.unit.baseUnit.strength)
    }

    private fun tryGetGoldFromKilling(civUnit:ICombatant, defeatedUnit:MapUnitCombatant) {
        if (civUnit.getCivInfo().hasUnique("Gain gold for each unit killed"))
            civUnit.getCivInfo().gold += defeatedUnit.unit.baseUnit.getProductionCost(defeatedUnit.getCivInfo()) / 10
    }

    // XP!
    private fun addXp(thisCombatant:ICombatant, amount:Int, otherCombatant:ICombatant){
        if(thisCombatant !is MapUnitCombatant) return
        if(thisCombatant.unit.promotions.totalXpProduced() >= 30 && otherCombatant.getCivInfo().isBarbarian())
            return

        var XPModifier = 1f
        if (thisCombatant.getCivInfo().hasUnique("Military units gain 50% more Experience from combat")) XPModifier += 0.5f
        if (thisCombatant.unit.hasUnique("50% Bonus XP gain")) XPModifier += 0.5f // As of 3.10.10 This is to be deprecated and converted to "[50]% Bonus XP gain" - keeping it here to that mods with this can still work for now

        for (unique in thisCombatant.unit.getMatchingUniques("[]% Bonus XP gain"))
            XPModifier +=  unique.params[0].toFloat() / 100

        val XPGained = (amount * XPModifier).toInt()
        thisCombatant.unit.promotions.XP += XPGained


        if(thisCombatant.getCivInfo().isMajorCiv()) {
            var greatGeneralPointsModifier = 1f
            for (unique in thisCombatant.unit.getMatchingUniques("[] is earned []% faster"))
                if (unique.params[0] == Constants.greatGeneral)
                    greatGeneralPointsModifier += unique.params[1].toFloat() / 100

            if (thisCombatant.unit.hasUnique("Combat very likely to create Great Generals")) // As of 3.10.10 This is to be deprecated and converted to "[Great General] is earned []% faster" - keeping it here to that mods with this can still work for now
                greatGeneralPointsModifier += 1f

            val greatGeneralPointsGained = (XPGained * greatGeneralPointsModifier).toInt()
            thisCombatant.getCivInfo().greatPeople.greatGeneralPoints += greatGeneralPointsGained
        }
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

        if (!attackerCiv.isMajorCiv()){
            city.destroyCity()
            return
        }

        if (attackerCiv.isPlayerCivilization()) {
            attackerCiv.popupAlerts.add(PopupAlert(AlertType.CityConquered, city.id))
            UncivGame.Current.settings.addCompletedTutorialTask("Conquer a city")
        }
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

        // need to save this because if the unit is captured its owner wil be overwritten
        val defenderCiv = defender.getCivInfo()

        val capturedUnit = (defender as MapUnitCombatant).unit
        capturedUnit.civInfo.addNotification("An enemy ["+attacker.getName()+"] has captured our ["+defender.getName()+"]",
                defender.getTile().position, Color.RED)

        // Apparently in Civ V, captured settlers are converted to workers.
        if(capturedUnit.name==Constants.settler) {
            val tile = capturedUnit.getTile()
            capturedUnit.destroy()
            attacker.getCivInfo().placeUnitNearTile(tile.position, Constants.worker)
        }
        else {
            capturedUnit.civInfo.removeUnit(capturedUnit)
            capturedUnit.assignOwner(attacker.getCivInfo())
        }

        destroyIfDefeated(defenderCiv, attacker.getCivInfo())
        capturedUnit.updateVisibleTiles()
    }

    fun destroyIfDefeated(attackedCiv:CivilizationInfo, attacker: CivilizationInfo){
        if (attackedCiv.isDefeated()) {
            attackedCiv.destroy()
            attacker.popupAlerts.add(PopupAlert(AlertType.Defeated, attackedCiv.civName))
        }
    }

    const val NUKE_RADIUS = 2

    fun nuke(attacker: ICombatant, targetTile: TileInfo) {
        val attackingCiv = attacker.getCivInfo()
        for (tile in targetTile.getTilesInDistance(NUKE_RADIUS)) {
            val city = tile.getCity()
            if (city != null && city.location == tile.position) {
                city.health = 1
                if (city.population.population <= 5 && !city.isOriginalCapital) {
                    city.destroyCity()
                } else {
                    city.population.population = max(city.population.population-5, 1)
                    city.population.unassignExtraPopulation()
                    continue
                }
                destroyIfDefeated(city.civInfo,attackingCiv)
            }

            fun declareWar(civSuffered: CivilizationInfo) {
                if (civSuffered != attackingCiv
                        && civSuffered.knows(attackingCiv)
                        && civSuffered.getDiplomacyManager(attackingCiv).canDeclareWar()) {
                    civSuffered.getDiplomacyManager(attackingCiv).declareWar()
                }
            }

            for(unit in tile.getUnits()){
                unit.destroy()
                postBattleNotifications(attacker, MapUnitCombatant(unit), unit.currentTile)
                declareWar(unit.civInfo)
                destroyIfDefeated(unit.civInfo, attackingCiv)
            }

            // this tile belongs to some civilization who is not happy of nuking it
            if (city != null)
                declareWar(city.civInfo)

            tile.improvement = null
            tile.improvementInProgress = null
            tile.turnsToImprovement = 0
            tile.roadStatus = RoadStatus.None
            if (tile.isLand && !tile.isImpassible()) tile.terrainFeature = "Fallout"
        }

        for(civ in attacker.getCivInfo().getKnownCivs()){
            civ.getDiplomacyManager(attackingCiv)
                    .setModifier(DiplomaticModifiers.UsedNuclearWeapons,-50f)
        }

        // Instead of postBattleAction() just destroy the missile, all other functions are not relevant
        (attacker as MapUnitCombatant).unit.destroy()
    }

    private fun tryInterceptAirAttack(attacker:MapUnitCombatant, defender: ICombatant) {
        val attackedTile = defender.getTile()
        for (interceptor in defender.getCivInfo().getCivUnits().filter { it.canIntercept(attackedTile) }) {
            if (Random().nextFloat() > 100f / interceptor.interceptChance()) continue

            var damage = BattleDamage.calculateDamageToDefender(MapUnitCombatant(interceptor), null, attacker)
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

    private fun doWithdrawFromMeleeAbility(attacker: ICombatant, defender: ICombatant, withdrawUnique: Unique): Boolean {
        // Some notes...
        // unit.getUniques() is a union of baseunit uniques and promotion effects.
        // according to some strategy guide the slinger's withdraw ability is inherited on upgrade,
        // according to the Ironclad entry of the wiki the Caravel's is lost on upgrade.
        // therefore: Implement the flag as unique for the Caravel and Destroyer, as promotion for the Slinger.
        // I want base chance for Slingers to be 133% (so they still get 76% against the Brute)
        // but I want base chance for navals to be 50% (assuming their attacker will often be the same baseunit)
        // the diverging base chance is coded into the effect string as (133%) for now, with 50% as default
        if (attacker !is MapUnitCombatant) return false         // allow simple access to unit property
        if (defender !is MapUnitCombatant) return false
        if (defender.unit.isEmbarked()) return false
        // Calculate success chance: Base chance from json, then ratios of *base* strength and mobility
        // Promotions have no effect as per what I could find in available documentation
        val attackBaseUnit = attacker.unit.baseUnit
        val defendBaseUnit = defender.unit.baseUnit
        val baseChance = withdrawUnique.params[0].toFloat()
        val percentChance = (baseChance
                        * defendBaseUnit.strength / attackBaseUnit.strength
                        * defendBaseUnit.movement / attackBaseUnit.movement).toInt()
        // Roll the dice - note the effect of the surroundings, namely how much room there is to evade to,
        // isn't yet factored in. But it should, and that's factored in by allowing the dice to choose
        // any geometrically fitting tile first and *then* fail when checking the tile for viability.
        val dice = Random().nextInt(100)
        if (dice > percentChance) return false
        // Calculate candidate tiles, geometry only
        val fromTile = defender.getTile()
        val attTile = attacker.getTile()
        //assert(fromTile in attTile.neighbors)                 // function should never be called with attacker not adjacent to defender
        // the following yields almost always exactly three tiles in a half-moon shape (exception: edge of map)
        val candidateTiles = fromTile.neighbors.filterNot { it == attTile || it in attTile.neighbors }
        if (candidateTiles.none()) return false              // impossible on our map shapes? No - corner of a rectangular map
        val toTile = candidateTiles.toList().random()
        // Now make sure the move is allowed - if not, sorry, bad luck
        if (!defender.unit.movement.canMoveTo(toTile)) {        // forbid impassable or blocked
            val blocker = toTile.militaryUnit
            if (blocker != null) {
                val notificationString = "[" + defendBaseUnit.name + "] could not withdraw from a [" +
                        attackBaseUnit.name + "] - blocked."
                defender.getCivInfo().addNotification(notificationString, toTile.position, Color.RED)
                attacker.getCivInfo().addNotification(notificationString, toTile.position, Color.GREEN)
            }
            return false
        }
        if (defendBaseUnit.unitType.isLandUnit() && !toTile.isLand) return false        // forbid retreat from land to sea - embarked already excluded
        if (toTile.isCityCenter()) return false                                         // forbid retreat into city
        // Withdraw success: Do it - move defender to toTile for no cost
        // NOT defender.unit.movement.moveToTile(toTile) - we want a free teleport
        // no need for any stats recalculation as neither fromTile nor toTile can be a city
        defender.unit.removeFromTile()
        defender.unit.putInTile(toTile)
        // and count 1 attack for attacker but leave it in place
        reduceAttackerMovementPointsAndAttacks(attacker,defender)
        val notificationString = "[" + defendBaseUnit.name + "] withdrew from a [" + attackBaseUnit.name + "]"
        defender.getCivInfo().addNotification(notificationString, toTile.position, Color.GREEN)
        attacker.getCivInfo().addNotification(notificationString, toTile.position, Color.RED)
        return true
    }

}
