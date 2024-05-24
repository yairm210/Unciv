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
    fun hasAtLeastMotivationToAttack(civInfo: Civilization, otherCiv: Civilization, atLeast: Int): Int {
        val targetCitiesWithOurCity = civInfo.threatManager.getNeighboringCitiesOfOtherCivs().filter { it.second.civ == otherCiv }.toList()
        val targetCities = targetCitiesWithOurCity.map { it.second }

        if (targetCitiesWithOurCity.isEmpty()) return 0

        if (targetCities.all { hasNoUnitsThatCanAttackCityWithoutDying(civInfo, it) })
            return 0

        val baseForce = 100f

        val ourCombatStrength = calculateSelfCombatStrength(civInfo, baseForce)
        val theirCombatStrength = calculateCombatStrengthWithProtectors(otherCiv, baseForce, civInfo)

        if (theirCombatStrength > ourCombatStrength) return 0

        val modifierMap = HashMap<String, Int>()
        modifierMap["Relative combat strength"] = getCombatStrengthModifier(ourCombatStrength, theirCombatStrength)

        modifierMap["Their allies"] = getDefensivePactAlliesScore(otherCiv, civInfo, baseForce, ourCombatStrength)

        val scoreRatioModifier = getScoreRatioModifier(otherCiv, civInfo)
        modifierMap["Relative score"] = scoreRatioModifier

        if (civInfo.stats.getUnitSupplyDeficit() != 0) {
            modifierMap["Over unit supply"] = (civInfo.stats.getUnitSupplyDeficit() * 2).coerceAtMost(20)
        } else if (otherCiv.stats.getUnitSupplyDeficit() == 0) {
            modifierMap["Relative production"] = getProductionRatioModifier(civInfo, otherCiv)
        }

        modifierMap["Relative technologies"] = getRelativeTechModifier(civInfo, otherCiv)

        val minTargetCityDistance = targetCitiesWithOurCity.minOf { it.second.getCenterTile().aerialDistanceTo(it.first.getCenterTile()) }
        modifierMap["Far away cities"] = when {
            minTargetCityDistance > 15 -> -15
            minTargetCityDistance > 10 -> -10
            minTargetCityDistance > 8 -> -5
            minTargetCityDistance < 6 -> 10
            else -> 0
        }

        val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)
        if (diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement))
            modifierMap["Research Agreement"] = -5

        if (diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            modifierMap["Declaration of Friendship"] = -10

        if (diplomacyManager.hasFlag(DiplomacyFlags.DefensivePact))
            modifierMap["Defensive Pact"] = -10

        modifierMap["Relationship"] = getRelationshipModifier(diplomacyManager)

        if (diplomacyManager.resourcesFromTrade().any { it.amount > 0 })
            modifierMap["Receiving trade resources"] = -5
        // If their cities don't have any nearby cities that are also targets to us and it doesn't include their capital
        // Then there cities are likely isolated and a good target.
        if (otherCiv.getCapital(true) !in targetCities
                && targetCities.all { theirCity -> !theirCity.neighboringCities.any { it !in targetCities } }) {
            modifierMap["Isolated city"] = 15
        }

        if (otherCiv.isCityState()) {
            modifierMap["City-state"] = -20
            if (otherCiv.getAllyCiv() == civInfo.civName)
                modifierMap["Allied City-state"] = -20 // There had better be a DAMN good reason
        }

        addWonderBasedMotivations(otherCiv, modifierMap)

        modifierMap["War with allies"] = getAlliedWarMotivation(civInfo, otherCiv)


        var motivationSoFar = modifierMap.values.sum()

        // Short-circuit to avoid expensive BFS
        if (motivationSoFar < atLeast) return motivationSoFar

        motivationSoFar += getAttackPathsModifier(civInfo, otherCiv, targetCitiesWithOurCity)

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

    private fun getRelationshipModifier(diplomacyManager: DiplomacyManager): Int {
        val relationshipModifier = when (diplomacyManager.relationshipIgnoreAfraid()) {
            RelationshipLevel.Unforgivable -> 10
            RelationshipLevel.Enemy -> 5
            RelationshipLevel.Ally -> -5 // this is so that ally + DoF is not too unbalanced -
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
            scoreRatio > 2f -> 15
            scoreRatio > 1.5f -> 10
            scoreRatio > 1.25f -> 5
            scoreRatio > 1f -> 0
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
            combatStrengthRatio > 3f -> 30
            combatStrengthRatio > 2.5f -> 25
            combatStrengthRatio > 2f -> 20
            combatStrengthRatio > 1.5f -> 10
            else -> 0
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

    private fun getAttackPathsModifier(civInfo: Civilization, otherCiv: Civilization, targetCitiesWithOurCity: List<Pair<City, City>>): Int {

        fun isTileCanMoveThrough(civInfo: Civilization, tile: Tile): Boolean {
            val owner = tile.getOwner()
            return !tile.isImpassible()
                    && (owner == otherCiv || owner == null || civInfo.diplomacyFunctions.canPassThroughTiles(owner))
        }

        fun isLandTileCnaMoveThrough(civInfo: Civilization, tile: Tile): Boolean {
            return tile.isLand && isTileCanMoveThrough(civInfo, tile)
        }

        val attackPaths: MutableList<List<Tile>> = mutableListOf()
        var attackPathModifiers: Int = 0


        for (potentialAttack in targetCitiesWithOurCity) {
            val landAttackPath = MapPathing.getConnection(civInfo, potentialAttack.first.getCenterTile(), potentialAttack.second.getCenterTile(), ::isLandTileCnaMoveThrough)
            if (landAttackPath != null) {
                attackPaths.add(landAttackPath)
                attackPathModifiers += 3
                continue
            }
            val landSeaAttackPath = MapPathing.getConnection(civInfo, potentialAttack.first.getCenterTile(), potentialAttack.second.getCenterTile(), ::isTileCanMoveThrough)
            if (landSeaAttackPath != null) {
                attackPaths.add(landSeaAttackPath)
                attackPathModifiers += 2
            }
        }

        if (attackPaths.isEmpty()) {
            // Do an expensive BFS to find any possible attack path
            val reachableEnemyCitiesBfs = BFS(civInfo.getCapital(true)!!.getCenterTile()) { isTileCanMoveThrough(civInfo, it) }
            reachableEnemyCitiesBfs.stepToEnd()
            val reachableEnemyCities = otherCiv.cities.filter { reachableEnemyCitiesBfs.hasReachedTile(it.getCenterTile()) }
            if (reachableEnemyCities.isEmpty()) return 0 // Can't even reach the enemy city, no point in war.
            val minAttackDistance = reachableEnemyCities.minOf { reachableEnemyCitiesBfs.getPathTo(it.getCenterTile()).count() }

            // Longer attack paths are worse, but if the attack path is too far away we shouldn't completely discard the posibility 
            attackPathModifiers -= (minAttackDistance - 10).coerceIn(0, 30) 
        }
        return attackPathModifiers
    }
}
