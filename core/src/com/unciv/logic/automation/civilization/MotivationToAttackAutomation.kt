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
import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.screens.victoryscreen.RankingType

object MotivationToAttackAutomation {

    /** Will return the motivation to attack, but might short circuit if the value is guaranteed to
     * be lower than `atLeast`. So any values below `atLeast` should not be used for comparison. */
    fun hasAtLeastMotivationToAttack(civInfo: Civilization, targetCiv: Civilization, atLeast: Int): Int {
        val targetCitiesWithOurCity = civInfo.threatManager.getNeighboringCitiesOfOtherCivs().filter { it.second.civ == targetCiv }.toList()
        val targetCities = targetCitiesWithOurCity.map { it.second }

        if (targetCitiesWithOurCity.isEmpty()) return 0

        if (targetCities.all { hasNoUnitsThatCanAttackCityWithoutDying(civInfo, it) })
            return 0

        val baseForce = 100f

        val ourCombatStrength = calculateSelfCombatStrength(civInfo, baseForce)
        val theirCombatStrength = calculateCombatStrengthWithProtectors(targetCiv, baseForce, civInfo)

        val modifiers:MutableList<Pair<String, Int>> = mutableListOf()
        modifiers.add(Pair("Base motivation", -15))

        modifiers.add(Pair("Relative combat strength", getCombatStrengthModifier(ourCombatStrength, theirCombatStrength + 0.8f * civInfo.threatManager.getCombinedForceOfWarringCivs())))
        // TODO: For now this will be a very high value because the AI can't handle multiple fronts, this should be changed later though
        modifiers.add(Pair("Concurrent wars", -civInfo.getCivsAtWarWith().count { it.isMajorCiv() && it != targetCiv } * 20))
        modifiers.add(Pair("Their concurrent wars", targetCiv.getCivsAtWarWith().count { it.isMajorCiv() } * 3))

        modifiers.add(Pair("Their allies", getDefensivePactAlliesScore(targetCiv, civInfo, baseForce, ourCombatStrength)))

        if (civInfo.threatManager.getNeighboringCivilizations().none { it != targetCiv && it.isMajorCiv() 
                        && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelLT(RelationshipLevel.Friend) })
            modifiers.add(Pair("No other threats", 10))

        if (targetCiv.isMajorCiv()) {
            val scoreRatioModifier = getScoreRatioModifier(targetCiv, civInfo)
            modifiers.add(Pair("Relative score", scoreRatioModifier))

            modifiers.add(Pair("Relative technologies", getRelativeTechModifier(civInfo, targetCiv)))

            if (civInfo.stats.getUnitSupplyDeficit() != 0) {
                modifiers.add(Pair("Over unit supply", (civInfo.stats.getUnitSupplyDeficit() * 2).coerceAtMost(20)))
            } else if (targetCiv.stats.getUnitSupplyDeficit() == 0 && !targetCiv.isCityState()) {
                modifiers.add(Pair("Relative production", getProductionRatioModifier(civInfo, targetCiv)))
            }
        }

        val minTargetCityDistance = targetCitiesWithOurCity.minOf { it.second.getCenterTile().aerialDistanceTo(it.first.getCenterTile()) }
        modifiers.add(Pair("Far away cities", when {
            minTargetCityDistance > 20 -> -10
            minTargetCityDistance > 14 -> -8
            minTargetCityDistance > 10 -> -3
            else -> 0
        }))
        if (minTargetCityDistance < 6) modifiers.add(Pair("Close cities", 5))

        val diplomacyManager = civInfo.getDiplomacyManager(targetCiv)!!

        if (diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement))
            modifiers.add(Pair("Research Agreement", -5))

        if (diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            modifiers.add(Pair("Declaration of Friendship", -10))

        if (diplomacyManager.hasFlag(DiplomacyFlags.DefensivePact))
            modifiers.add(Pair("Defensive Pact", -15))

        modifiers.add(Pair("Relationship", getRelationshipModifier(diplomacyManager)))

        if (diplomacyManager.hasFlag(DiplomacyFlags.Denunciation)) {
            modifiers.add(Pair("Denunciation", 5))
        }

        if (diplomacyManager.hasFlag(DiplomacyFlags.WaryOf) && diplomacyManager.getFlag(DiplomacyFlags.WaryOf) < 0) {
            modifiers.add(Pair("PlanningAttack", -diplomacyManager.getFlag(DiplomacyFlags.WaryOf)))
        } else {
            val attacksPlanned = civInfo.diplomacy.values.count { it.hasFlag(DiplomacyFlags.WaryOf) && it.getFlag(DiplomacyFlags.WaryOf) < 0 }
            modifiers.add(Pair("PlanningAttackAgainstOtherCivs", -attacksPlanned * 5))
        }

        if (diplomacyManager.resourcesFromTrade().any { it.amount > 0 })
            modifiers.add(Pair("Receiving trade resources", -5))

        // If their cities don't have any nearby cities that are also targets to us and it doesn't include their capital
        // Then there cities are likely isolated and a good target.
        if (targetCiv.getCapital(true) !in targetCities
                && targetCities.all { theirCity -> !theirCity.neighboringCities.any { it !in targetCities } }) {
            modifiers.add(Pair("Isolated city", 15))
        }

        if (targetCiv.isCityState()) {
            modifiers.add(Pair("Protectors", -targetCiv.cityStateFunctions.getProtectorCivs().size * 3))
            if (targetCiv.cityStateFunctions.getProtectorCivs().contains(civInfo))
                modifiers.add(Pair("Under our protection", -15))
            if (targetCiv.getAllyCiv() == civInfo.civName)
                modifiers.add(Pair("Allied City-state", -20)) // There had better be a DAMN good reason
        }

        addWonderBasedMotivations(targetCiv, modifiers)

        modifiers.add(Pair("War with allies", getAlliedWarMotivation(civInfo, targetCiv)))

        // Purely for debugging, remove modifiers that don't have an effect
        modifiers.removeAll { it.second == 0 }
        var motivationSoFar = modifiers.sumOf { it.second }

        // Short-circuit to avoid A-star
        if (motivationSoFar < atLeast) return motivationSoFar

        motivationSoFar += getAttackPathsModifier(civInfo, targetCiv, targetCitiesWithOurCity)

        return motivationSoFar
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

    private fun addWonderBasedMotivations(otherCiv: Civilization, modifiers: MutableList<Pair<String, Int>>) {
        var wonderCount = 0
        for (city in otherCiv.cities) {
            val construction = city.cityConstructions.getCurrentConstruction()
            if (construction is Building && construction.hasUnique(UniqueType.TriggersCulturalVictory))
                modifiers.add(Pair("About to win", 15))
            if (construction is BaseUnit && construction.hasUnique(UniqueType.AddInCapital))
                modifiers.add(Pair("About to win", 15))
            wonderCount += city.cityConstructions.getBuiltBuildings().count { it.isWonder }
        }

        // The more wonders they have, the more beneficial it is to conquer them
        // Civs need an army to protect thier wonders which give the most score
        if (wonderCount > 0)
            modifiers.add(Pair("Owned Wonders", wonderCount))
    }

    /** If they are at war with our allies, then we should join in */
    private fun getAlliedWarMotivation(civInfo: Civilization, otherCiv: Civilization): Int {
        var alliedWarMotivation = 0
        for (thirdCiv in civInfo.getDiplomacyManager(otherCiv)!!.getCommonKnownCivs()) {
            val thirdCivDiploManager = civInfo.getDiplomacyManager(thirdCiv)
            if (thirdCivDiploManager!!.isRelationshipLevelGE(RelationshipLevel.Friend)
                && thirdCiv.isAtWarWith(otherCiv)
            ) {
                if (thirdCiv.getDiplomacyManager(otherCiv)!!.hasFlag(DiplomacyFlags.Denunciation))
                    alliedWarMotivation += 2
                alliedWarMotivation += if (thirdCivDiploManager.hasFlag(DiplomacyFlags.DefensivePact)) 15 
                else if (thirdCivDiploManager.isRelationshipLevelGT(RelationshipLevel.Friend)) 5
                else 2
            }
        }
        return alliedWarMotivation
    }

    private fun getRelationshipModifier(diplomacyManager: DiplomacyManager): Int {
        val relationshipModifier = when (diplomacyManager.relationshipIgnoreAfraid()) {
            RelationshipLevel.Unforgivable -> 15
            RelationshipLevel.Enemy -> 10
            RelationshipLevel.Competitor -> 5
            RelationshipLevel.Favorable -> -2
            RelationshipLevel.Friend -> -5
            RelationshipLevel.Ally -> -10 // this is so that ally + DoF is not too unbalanced -
            // still possible for AI to declare war for isolated city
            else -> 0
        }
        return relationshipModifier
    }

    private fun getRelativeTechModifier(civInfo: Civilization, otherCiv: Civilization): Int {
        val relativeTech = civInfo.getStatForRanking(RankingType.Technologies) - otherCiv.getStatForRanking(RankingType.Technologies)
        val relativeTechModifier = when {
            relativeTech > 6 -> 10
            relativeTech > 3 -> 5
            relativeTech > -3 -> 0
            relativeTech > -6 -> -2
            relativeTech > -9 -> -5
            else -> -10
        }
        return relativeTechModifier
    }

    private fun getProductionRatioModifier(civInfo: Civilization, otherCiv: Civilization): Int {
        // If either of our Civs are suffering from a supply deficit, our army must be too large
        // There is no easy way to check the raw production if a civ has a supply deficit
        // We might try to divide the current production by the getUnitSupplyProductionPenalty()
        // but it only is true for our turn and not the previous turn and might result in odd values

        val productionRatio = civInfo.getStatForRanking(RankingType.Production).toFloat() / otherCiv.getStatForRanking(RankingType.Production).toFloat()
        val productionRatioModifier = when {
            productionRatio > 2f -> 15
            productionRatio > 1.5f -> 7
            productionRatio > 1.2 -> 3
            productionRatio > .8f -> 0
            productionRatio > .5f -> -5
            productionRatio > .25f -> -10
            else -> -10
        }
        return productionRatioModifier
    }

    private fun getScoreRatioModifier(otherCiv: Civilization, civInfo: Civilization): Int {
        // Civs with more score are more threatening to our victory
        // Bias towards attacking civs with a high score and low military
        // Bias against attacking civs with a low score and a high military
        // Designed to mitigate AIs declaring war on weaker civs instead of their rivals
        val scoreRatio = otherCiv.getStatForRanking(RankingType.Score).toFloat() / civInfo.getStatForRanking(RankingType.Score).toFloat()
        val scoreRatioModifier = when {
            scoreRatio > 2f -> 20
            scoreRatio > 1.5f -> 15
            scoreRatio > 1.25f -> 10
            scoreRatio > 1f -> 2
            scoreRatio > .8f -> 0
            scoreRatio > .5f -> -2
            scoreRatio > .25f -> -5
            else -> -10
        }
        return scoreRatioModifier
    }

    private fun getDefensivePactAlliesScore(otherCiv: Civilization, civInfo: Civilization, baseForce: Float, ourCombatStrength: Float): Int {
        var theirAlliesValue = 0
        for (thirdCiv in otherCiv.diplomacy.values.filter { it.hasFlag(DiplomacyFlags.DefensivePact) && it.otherCiv() != civInfo }) {
            val thirdCivCombatStrengthRatio = otherCiv.getStatForRanking(RankingType.Force).toFloat() + baseForce / ourCombatStrength
            theirAlliesValue += when {
                thirdCivCombatStrengthRatio > 5 -> -15
                thirdCivCombatStrengthRatio > 2.5 -> -10
                thirdCivCombatStrengthRatio > 2 -> -8
                thirdCivCombatStrengthRatio > 1.5 -> -5
                thirdCivCombatStrengthRatio > .8 -> -2
                else -> 0
            }
        }
        return theirAlliesValue
    }

    private fun getCombatStrengthModifier(ourCombatStrength: Float, theirCombatStrength: Float): Int {
        val combatStrengthRatio = ourCombatStrength / theirCombatStrength
        val combatStrengthModifier = when {
            combatStrengthRatio > 5f -> 30
            combatStrengthRatio > 4f -> 20
            combatStrengthRatio > 3f -> 15
            combatStrengthRatio > 2f -> 10
            combatStrengthRatio > 1.5f -> 5
            combatStrengthRatio > .8f -> 0
            combatStrengthRatio > .6f -> -5
            combatStrengthRatio > .4f -> -15
            combatStrengthRatio > .2f -> -20
            else -> -20
        }
        return combatStrengthModifier
    }

    private fun hasNoUnitsThatCanAttackCityWithoutDying(civInfo: Civilization, theirCity: City) = civInfo.units.getCivUnits().filter { it.isMilitary() }.none {
        val damageReceivedWhenAttacking =
            BattleDamage.calculateDamageToAttacker(
                MapUnitCombatant(it),
                CityCombatant(theirCity)
            )
        damageReceivedWhenAttacking < 100
    }

    /**
     * Checks the routes of attack against [otherCiv] using [targetCitiesWithOurCity].
     * 
     * The more routes of attack and shorter the path the higher a motivation will be returned.
     * Sea attack routes are less valuable
     * 
     * @return The motivation ranging from -30 to around +10
     */
    private fun getAttackPathsModifier(civInfo: Civilization, otherCiv: Civilization, targetCitiesWithOurCity: List<Pair<City, City>>): Int {

        fun isTileCanMoveThrough(civInfo: Civilization, tile: Tile): Boolean {
            val owner = tile.getOwner()
            return !tile.isImpassible()
                    && (owner == otherCiv || owner == null || civInfo.diplomacyFunctions.canPassThroughTiles(owner))
        }

        fun isLandTileCanMoveThrough(civInfo: Civilization, tile: Tile): Boolean {
            return tile.isLand && isTileCanMoveThrough(civInfo, tile)
        }

        val attackPaths: MutableList<List<Tile>> = mutableListOf()
        var attackPathModifiers: Int = -3

        // For each city, we want to calculate if there is an attack path to the enemy
        for (attacksGroupedByCity in targetCitiesWithOurCity.groupBy { it.first }) {
            val cityToAttackFrom = attacksGroupedByCity.key
            var cityAttackValue = 0

            // We only want to calculate the best attack path and use it's value
            // Land routes are clearly better than sea routes
            for ((_, cityToAttack) in attacksGroupedByCity.value) {
                val landAttackPath = MapPathing.getConnection(civInfo, cityToAttackFrom.getCenterTile(), cityToAttack.getCenterTile(), ::isLandTileCanMoveThrough)
                if (landAttackPath != null && landAttackPath.size < 16) {
                    attackPaths.add(landAttackPath)
                    cityAttackValue = 3
                    break
                }

                if (cityAttackValue > 0) continue

                val landAndSeaAttackPath = MapPathing.getConnection(civInfo, cityToAttackFrom.getCenterTile(), cityToAttack.getCenterTile(), ::isTileCanMoveThrough)
                if (landAndSeaAttackPath != null  && landAndSeaAttackPath.size < 16) {
                    attackPaths.add(landAndSeaAttackPath)
                    cityAttackValue += 1
                }
            }
            attackPathModifiers += cityAttackValue
        }

        if (attackPaths.isEmpty()) {
            // Do an expensive BFS to find any possible attack path
            val reachableEnemyCitiesBfs = BFS(civInfo.getCapital(true)!!.getCenterTile()) { isTileCanMoveThrough(civInfo, it) }
            reachableEnemyCitiesBfs.stepToEnd()
            val reachableEnemyCities = otherCiv.cities.filter { reachableEnemyCitiesBfs.hasReachedTile(it.getCenterTile()) }
            if (reachableEnemyCities.isEmpty()) return -50 // Can't even reach the enemy city, no point in war.
            val minAttackDistance = reachableEnemyCities.minOf { reachableEnemyCitiesBfs.getPathTo(it.getCenterTile()).count() }

            // Longer attack paths are worse, but if the attack path is too far away we shouldn't completely discard the possibility 
            attackPathModifiers -= (minAttackDistance - 10).coerceIn(0, 30) 
        }
        return attackPathModifiers
    }
}
