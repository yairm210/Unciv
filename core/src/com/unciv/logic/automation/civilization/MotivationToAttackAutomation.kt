package com.unciv.logic.automation.civilization

import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.BFS
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.screens.victoryscreen.RankingType
import kotlin.math.max
import kotlin.math.min

object MotivationToAttackAutomation {

    /** Returns a float indicating the desirability of war with otherCiv. As it stands, considers mostly power
     * disparity than anything else. Returns -999 if war is unviable, may return higher negative values
     * if viable but hard to win. Use this form if you dont need to know if there is a land path or if the city is reachable
     * */
    fun motivationToDeclareWar(civInfo: Civilization, otherCiv: Civilization): Float {
        //In practice, this method asks "How likely is it to win this war?"
        //more so than "Should i fight this war?"
        //We should eventually make it consider if the war is worth fighting
        val closestCities = NextTurnAutomation.getClosestCities(civInfo, otherCiv) ?: return 0f

        val ourCity = closestCities.city1
        val theirCity = closestCities.city2
        if (hasNoUnitsThatCanAttackCityWithoutDying(civInfo, theirCity))
            return -999f

        var motivation = powerAdvantageScore(civInfo,otherCiv,1f)

        if(motivation <= 0f) return  -999f

        val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)

        motivation -= (closestCities.aerialDistance - 4) * 3

        motivation += min(20f,max((40 - diplomacyManager.opinionOfOtherCiv())/4,-20f))

        if (otherCiv.isCityState()){
            motivation -= 15
            if (otherCiv.getAllyCiv() == civInfo.civName)
            {
                motivation -= 15
            }
        } else if (theirCity.getTiles().none { tile -> tile.neighbors.any { it.getOwner() == theirCity.civ && it.getCity() != theirCity } })
        {
            //Isolated city
            motivation += 15
        }

        //This part does take "Should i fight?" into consideration, but it is not significative enough
        //to dissuade the AI except in very particular circumstances
        motivation -= relationshipBonuses(diplomacyManager) * 0.5f
        motivation += getAlliedWarMotivation(civInfo,otherCiv)
        return  motivation
    }

    /** Returns a float indicating the desirability of war with otherCiv. Considers mostly power
     * disparity than anything else. Returns -999 if war is unviable,
     * may return higher negative values if viable but hard to win.
     * May short-circuit and return a value lower than atLeast*/
    fun motivationToDeclareWar(civInfo: Civilization, otherCiv: Civilization, atLeast:Float):Float{
        var motivation = motivationToDeclareWar(civInfo,otherCiv)

        // Short-circuit to avoid expensive BFS

        if (motivation < atLeast) return motivation

        val closestCities = NextTurnAutomation.getClosestCities(civInfo, otherCiv)!!
        val ourCity = closestCities.city1
        val theirCity = closestCities.city2


        val landPathBFS = BFS(ourCity.getCenterTile()) {
            it.isLand && isTileCanMoveThrough(it, civInfo, otherCiv)
        }

        landPathBFS.stepUntilDestination(theirCity.getCenterTile())
        if (!landPathBFS.hasReachedTile(theirCity.getCenterTile()))
            motivation -= -10

        // Short-circuit to avoid expensive BFS
        if (motivation < atLeast) return motivation

        val reachableEnemyCitiesBfs = BFS(civInfo.getCapital(true)!!.getCenterTile()) { isTileCanMoveThrough(it,civInfo,otherCiv) }
        reachableEnemyCitiesBfs.stepToEnd()
        val reachableEnemyCities = otherCiv.cities.filter { reachableEnemyCitiesBfs.hasReachedTile(it.getCenterTile()) }
        if (reachableEnemyCities.isEmpty()) return -999f // Can't even reach the enemy city, no point in war.
        return motivation
    }

    /**
     * Returns a float up to 105 indicating how winnable this war is.
     * Returns 0 when in considerable strength disadvantage. Does not return negative.
     * Does not consider if the war is worth fighting at all, only if it is winnable.
     */
    fun motivationToStayInWar(civInfo: Civilization, otherCiv: Civilization):Float{
        val closestCities = NextTurnAutomation.getClosestCities(civInfo, otherCiv) ?: return 0f

        val ourCity = closestCities.city1
        val theirCity = closestCities.city2
        if (hasNoUnitsThatCanAttackCityWithoutDying(civInfo, theirCity))
            return 0f

        //Just because they are slightly stronger now does not mean we should sue for peace
        var motivation = powerAdvantageScore(civInfo,otherCiv,0.9f)
        if(motivation <= 0f) return  0f

        if (!otherCiv.isCityState() && theirCity.getTiles().none { tile -> tile.neighbors.any { it.getOwner() == theirCity.civ && it.getCity() != theirCity } }){
            //Isolated city
            motivation += 15
        }

        motivation -= (closestCities.aerialDistance - 4) * 3

        // Short-circuit to avoid expensive BFS
        if (motivation < 10f) return motivation

        val landPathBFS = BFS(ourCity.getCenterTile()) {
            it.isLand && isTileCanMoveThrough(it, civInfo, otherCiv)
        }

        landPathBFS.stepUntilDestination(theirCity.getCenterTile())
        if (!landPathBFS.hasReachedTile(theirCity.getCenterTile()))
            motivation -= -10

        // Short-circuit to avoid expensive BFS
        if (motivation < 10f) return motivation

        val reachableEnemyCitiesBfs = BFS(civInfo.getCapital(true)!!.getCenterTile()) { isTileCanMoveThrough(it,civInfo,otherCiv) }
        reachableEnemyCitiesBfs.stepToEnd()
        val reachableEnemyCities = otherCiv.cities.filter { reachableEnemyCitiesBfs.hasReachedTile(it.getCenterTile()) }
        if (reachableEnemyCities.isEmpty()) return 0f // Can't even reach the enemy city, no point in war.
        return motivation
    }

    private fun isTileCanMoveThrough(tile: Tile, civInfo: Civilization, otherCiv: Civilization): Boolean {
        val owner = tile.getOwner()
        return !tile.isImpassible()
            && (owner == otherCiv || owner == null || civInfo.diplomacyFunctions.canPassThroughTiles(owner))
    }

    /** Returns a value between -45 and 130 indicating how stronger a civ is against another*/
    private fun powerAdvantageScore(civInfo: Civilization, otherCiv: Civilization, minimunStrengthRatio:Float): Float{
        var powerDiff = 0f
        val powerRatio = calculateSelfCombatStrength(civInfo,30f) / calculateCombatStrengthWithProtectors(otherCiv, 30f, civInfo)
        if (powerRatio < minimunStrengthRatio) return 0f
        powerDiff += min(50f, (powerRatio - 1) * 25)
        val techDiff = civInfo.getStatForRanking(RankingType.Technologies) - otherCiv.getStatForRanking(RankingType.Technologies)
        powerDiff += min(20,max(4 * techDiff, -20))
        val prodRatio = civInfo.getStatForRanking(RankingType.Production).toFloat() / otherCiv.getStatForRanking(RankingType.Production).toFloat()
        powerDiff += min(20 * prodRatio - 20, 20f)
        //City-states are often having high scores. Why?
        val scoreRatio = otherCiv.getStatForRanking(RankingType.Score).toFloat() / civInfo.getStatForRanking(RankingType.Score).toFloat();
        powerDiff += min(30 / scoreRatio - 20, 20f) //Divided so that stronger civs are more targeted
        return powerDiff
    }

    private fun relationshipBonuses(diplomacyManager: DiplomacyManager): Int{
        var bonuses = 0
        if (diplomacyManager.resourcesFromTrade().any { it.amount > 0 })
        {
            //This should be contingent on the amount of resources, but it could make trade OP
            bonuses += 8
        }
        if (diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement)) {
            bonuses += 5
        }
        if (diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
        {
            bonuses += 15
        }
        if(diplomacyManager.hasFlag(DiplomacyFlags.DefensivePact))
        {
            //That's a heavy bonus, but the AI should not easily attack civs with defensive pact
            //If too OP, the best approach imo would be to make signing defensive pacts more difficult
            //because the whole point of an alliance is to protect each other
            bonuses+= 30
        }
        return bonuses
    }

    private fun calculateCombatStrengthWithProtectors(otherCiv: Civilization, baseForce: Float, civInfo: Civilization): Float {
        var theirCombatStrength = calculateSelfCombatStrength(otherCiv, baseForce)

        //for city-states, also consider their protectors
        if (otherCiv.isCityState() and otherCiv.cityStateFunctions.getProtectorCivs().isNotEmpty()) {
            theirCombatStrength += otherCiv.cityStateFunctions.getProtectorCivs().filterNot { it == civInfo }
                .sumOf { it.getStatForRanking(RankingType.Force) }
        }
        return theirCombatStrength
    }

    private fun calculateSelfCombatStrength(civInfo: Civilization, baseForce: Float): Float {
        var ourCombatStrength = civInfo.getStatForRanking(RankingType.Force).toFloat() + baseForce
        if (civInfo.getCapital() != null) ourCombatStrength += CityCombatant(civInfo.getCapital()!!).getCityStrength()
        return ourCombatStrength
    }

    //Keeping this one because it may be useful if desirability and winnability of war are separated
    private fun addWonderBasedMotivations(otherCiv: Civilization, modifierMap: HashMap<String, Int>) {
        var wonderCount = 0
        for (city in otherCiv.cities) {
            val construction = city.cityConstructions.getCurrentConstruction()
            if (construction is Building && construction.hasUnique(UniqueType.TriggersCulturalVictory))
                modifierMap["About to win"] = 15
            if (construction is BaseUnit && construction.hasUnique(UniqueType.AddInCapital))
                modifierMap["About to win"] = 15
            wonderCount += city.cityConstructions.getBuiltBuildings().count { it.isWonder }
        }

        // The more wonders they have, the more beneficial it is to conquer them
        // Civs need an army to protect thier wonders which give the most score
        if (wonderCount > 0)
            modifierMap["Owned Wonders"] = wonderCount
    }

    /** If they are at war with our allies, then we should join in */
    private fun getAlliedWarMotivation(civInfo: Civilization, otherCiv: Civilization): Int {
        var alliedWarMotivation = 0
        for (thirdCiv in civInfo.getDiplomacyManager(otherCiv).getCommonKnownCivs()) {
            val thirdCivDiploManager = civInfo.getDiplomacyManager(thirdCiv)
            if (thirdCivDiploManager.hasFlag(DiplomacyFlags.DeclinedDeclarationOfFriendship)
                && thirdCiv.isAtWarWith(otherCiv)
            ) {
                alliedWarMotivation += if (thirdCivDiploManager.hasFlag(DiplomacyFlags.DefensivePact)) 15 else 5
            }
        }
        return alliedWarMotivation
    }

    private fun hasNoUnitsThatCanAttackCityWithoutDying(civInfo: Civilization, theirCity: City) = civInfo.units.getCivUnits().filter { it.isMilitary() }.none {
        val damageReceivedWhenAttacking =
            BattleDamage.calculateDamageToAttacker(
                MapUnitCombatant(it),
                CityCombatant(theirCity)
            )
        damageReceivedWhenAttacking < 100
    }

}
