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
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.screens.victoryscreen.RankingType
import yairm210.purity.annotations.Readonly
import kotlin.math.pow

object MotivationToAttackAutomation {

    /** Will return the motivation to attack, but might short circuit if the value is guaranteed to
     * be lower than `atLeast`. So any values below `atLeast` should not be used for comparison. */
    @Readonly
    fun hasAtLeastMotivationToAttack(civInfo: Civilization, targetCiv: Civilization, atLeast: Float): Float {
        val diplomacyManager = civInfo.getDiplomacyManager(targetCiv)!!
        val personality = civInfo.getPersonality()

        val targetCitiesWithOurCity = civInfo.threatManager.getNeighboringCitiesOfOtherCivs().filter { it.second.civ == targetCiv }.toList()
        val targetCities = targetCitiesWithOurCity.map { it.second }

        if (targetCitiesWithOurCity.isEmpty()) return 0f

        if (targetCities.all { hasNoUnitsThatCanAttackCityWithoutDying(civInfo, it) })
            return 0f

        val baseForce = 100f

        val ourCombatStrength = calculateSelfCombatStrength(civInfo, baseForce)
        val theirCombatStrength = calculateCombatStrengthWithProtectors(targetCiv, baseForce, civInfo)

        val modifiers: MutableList<Pair<String, Float>> = mutableListOf()

        // If our personality is to declare war more then we should have a higher base motivation (a negative number closer to 0)
        modifiers.add(Pair("Base motivation", -(15f * personality.inverseModifierFocus(PersonalityValue.DeclareWar, 0.5f))))

        modifiers.add(Pair("Relative combat strength", getCombatStrengthModifier(civInfo, targetCiv, ourCombatStrength, theirCombatStrength + 0.8f * civInfo.threatManager.getCombinedForceOfWarringCivs())))
        // TODO: For now this will be a very high value because the AI can't handle multiple fronts, this should be changed later though
        modifiers.add(Pair("Concurrent wars", -civInfo.getCivsAtWarWith().count { it.isMajorCiv() && it != targetCiv } * 20f))
        modifiers.add(Pair("Their concurrent wars", targetCiv.getCivsAtWarWith().count { it.isMajorCiv() } * 3f))

        modifiers.add(Pair("Their allies", getDefensivePactAlliesScore(targetCiv, civInfo, baseForce, ourCombatStrength)))

        if (civInfo.threatManager.getNeighboringCivilizations().none { it != targetCiv && it.isMajorCiv()
                        && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelLT(RelationshipLevel.Friend) })
            modifiers.add(Pair("No other threats", 10f))

        if (targetCiv.isMajorCiv()) {
            val scoreRatioModifier = getScoreRatioModifier(targetCiv, civInfo)
            modifiers.add(Pair("Relative score", scoreRatioModifier))

            if (civInfo.stats.getUnitSupplyDeficit() != 0) {
                modifiers.add(Pair("Over unit supply", (civInfo.stats.getUnitSupplyDeficit() * 2f).coerceAtMost(20f)))
            } else if (targetCiv.stats.getUnitSupplyDeficit() == 0 && !targetCiv.isCityState) {
                modifiers.add(Pair("Relative production", getProductionRatioModifier(civInfo, targetCiv)))
            }
        }

        val minTargetCityDistance = targetCitiesWithOurCity.minOf { it.second.getCenterTile().aerialDistanceTo(it.first.getCenterTile()) }
        // Defensive civs should avoid fighting civilizations that are farther away and don't pose a threat
        modifiers.add(Pair("Far away cities", when {
            minTargetCityDistance > 20 -> -10f
            minTargetCityDistance > 14 -> -8f
            minTargetCityDistance > 10 -> -3f
            else -> 0f
        } * personality.inverseModifierFocus(PersonalityValue.Aggressive, 0.2f)))

        // Defensive civs want to deal with potential nearby cities to protect themselves
        if (minTargetCityDistance < 6)
            modifiers.add(Pair("Close cities", 5f * personality.inverseModifierFocus(PersonalityValue.Aggressive, 1f)))

        if (diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement))
            modifiers.add(Pair("Research Agreement", -5f * personality.scaledFocus(PersonalityValue.Science) * personality.scaledFocus(PersonalityValue.Commerce)))

        if (diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            modifiers.add(Pair("Declaration of Friendship", -10f * personality.modifierFocus(PersonalityValue.Loyal, .5f)))

        if (diplomacyManager.hasFlag(DiplomacyFlags.DefensivePact))
            modifiers.add(Pair("Defensive Pact", -15f * personality.modifierFocus(PersonalityValue.Loyal, .3f)))

        modifiers.add(Pair("Relationship", getRelationshipModifier(diplomacyManager)))

        if (diplomacyManager.hasFlag(DiplomacyFlags.Denunciation)) {
            modifiers.add(Pair("Denunciation", 5f * personality.inverseModifierFocus(PersonalityValue.Diplomacy, .5f)))
        }

        if (diplomacyManager.hasFlag(DiplomacyFlags.WaryOf) && diplomacyManager.getFlag(DiplomacyFlags.WaryOf) < 0) {
            // Completely defensive civs will plan defensively and have a 0 here
            modifiers.add(Pair("PlanningAttack", -diplomacyManager.getFlag(DiplomacyFlags.WaryOf) * personality.scaledFocus(PersonalityValue.Aggressive) / 2))
        } else {
            val attacksPlanned = civInfo.diplomacy.values.count { it.hasFlag(DiplomacyFlags.WaryOf) && it.getFlag(DiplomacyFlags.WaryOf) < 0 }
            modifiers.add(Pair("PlanningAttackAgainstOtherCivs", -attacksPlanned * 5f * personality.inverseModifierFocus(PersonalityValue.Aggressive, .5f)))
        }

        if (diplomacyManager.resourcesFromTrade().any { it.amount > 0 })
            modifiers.add(Pair("Receiving trade resources", -8f * personality.modifierFocus(PersonalityValue.Commerce, .5f)))

        // If their cities don't have any nearby cities that are also targets to us and it doesn't include their capital
        // Then there cities are likely isolated and a good target.
        if (targetCiv.getCapital(true) !in targetCities
                && targetCities.all { theirCity -> !theirCity.neighboringCities.any { it !in targetCities } }) {
            modifiers.add(Pair("Isolated city", 10f * personality.modifierFocus(PersonalityValue.Aggressive, .8f)))
        }

        if (targetCiv.isCityState) {
            modifiers.add(Pair("Protectors", -targetCiv.cityStateFunctions.getProtectorCivs().size * 3f * personality.modifierFocus(PersonalityValue.Diplomacy, .8f)))
            //The more potential friends of this CS, the more times the friend bonus is shared and the utilitarian option is to leave it alive
            modifiers.add(Pair("Influence", -targetCiv.getDiplomacyManager(civInfo)!!.getInfluence() / 10f * personality.modifierFocus(PersonalityValue.Diplomacy, .8f)))
            // The more we invested into the city state already, the less likely we're going to attack it, and vice versa
            if (targetCiv.getAllyCivName() == civInfo.civName)
                modifiers.add(Pair("Allied City-state", -20 * personality.modifierFocus(PersonalityValue.Diplomacy, .8f))) // There had better be a DAMN good reason
        }

        modifiers += getWonderBasedMotivations(targetCiv)

        modifiers.add(Pair("War with allies", getAlliedWarMotivation(civInfo, targetCiv)))

        // Purely for debugging, remove modifiers that don't have an effect
        modifiers.removeAll { it.second == 0f }
        var motivationSoFar = modifiers.map { it.second }.sum()

        // Short-circuit to avoid A-star
        if (motivationSoFar < atLeast) return motivationSoFar

        motivationSoFar += getAttackPathsModifier(civInfo, targetCiv, targetCitiesWithOurCity)

        return motivationSoFar
    }

    @Readonly
    private fun calculateCombatStrengthWithProtectors(otherCiv: Civilization, baseForce: Float, civInfo: Civilization): Float {
        var theirCombatStrength = calculateSelfCombatStrength(otherCiv, baseForce)

        //for city-states, also consider their protectors
        if (otherCiv.isCityState and otherCiv.cityStateFunctions.getProtectorCivs().isNotEmpty()) {
            theirCombatStrength += otherCiv.cityStateFunctions.getProtectorCivs().filterNot { it == civInfo }
                .sumOf { it.getStatForRanking(RankingType.Force) }
        }
        return theirCombatStrength
    }

    @Readonly
    private fun calculateSelfCombatStrength(civInfo: Civilization, baseForce: Float): Float {
        var ourCombatStrength = civInfo.getStatForRanking(RankingType.Force).toFloat() + baseForce
        if (civInfo.getCapital() != null) ourCombatStrength += CityCombatant(civInfo.getCapital()!!).getCityStrength()
        return ourCombatStrength
    }

    @Readonly
    private fun getWonderBasedMotivations(otherCiv: Civilization): MutableList<Pair<String, Float>> {
        var wonderCount = 0
        val modifiers = mutableListOf<Pair<String, Float>>()
        for (city in otherCiv.cities) {
            val construction = city.cityConstructions.getCurrentConstruction()
            if (construction is Building && construction.hasUnique(UniqueType.TriggersCulturalVictory))
                modifiers.add(Pair("About to win", 15f))
            if (construction is BaseUnit && construction.hasUnique(UniqueType.AddInCapital))
                modifiers.add(Pair("About to win", 15f))
            wonderCount += city.cityConstructions.getBuiltBuildings().count { it.isWonder }
        }

        // The more wonders they have, the more beneficial it is to conquer them
        // Civs need an army to protect thier wonders which give the most score
        if (wonderCount > 0)
            modifiers.add(Pair("Owned Wonders", wonderCount.toFloat()))
        return modifiers
    }

    /** If they are at war with our allies, then we should join in */
    @Readonly
    private fun getAlliedWarMotivation(civInfo: Civilization, otherCiv: Civilization): Float {
        var alliedWarMotivation = 0f
        for (thirdCiv in civInfo.getDiplomacyManager(otherCiv)!!.getCommonKnownCivs()) {
            val thirdCivDiploManager = civInfo.getDiplomacyManager(thirdCiv)
            if (thirdCivDiploManager!!.isRelationshipLevelLT(RelationshipLevel.Friend)) continue

            if (thirdCiv.getDiplomacyManager(otherCiv)!!.hasFlag(DiplomacyFlags.Denunciation))
                alliedWarMotivation += 2f

            if (thirdCiv.isAtWarWith(otherCiv)) {
                alliedWarMotivation += if (thirdCivDiploManager.hasFlag(DiplomacyFlags.DefensivePact)) 15f
                else if (thirdCivDiploManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)) 5f
                else 2f
            }
        }
        return alliedWarMotivation * civInfo.getPersonality().modifierFocus(PersonalityValue.Loyal, .5f)
    }

    @Readonly
    private fun getRelationshipModifier(diplomacyManager: DiplomacyManager): Float {
        val relationshipModifier = when (diplomacyManager.relationshipIgnoreAfraid()) {
            RelationshipLevel.Unforgivable -> 10f
            RelationshipLevel.Enemy -> 5f
            RelationshipLevel.Competitor -> 2f
            RelationshipLevel.Favorable -> -2f
            RelationshipLevel.Friend -> -5f
            RelationshipLevel.Ally -> -10f // this is so that ally + DoF is not too unbalanced -
            // still possible for AI to declare war for isolated city
            else -> 0f
        }
        return relationshipModifier * diplomacyManager.civInfo.getPersonality().modifierFocus(PersonalityValue.Loyal, .3f)
    }

    @Readonly
    private fun getProductionRatioModifier(civInfo: Civilization, otherCiv: Civilization): Float {
        // If either of our Civs are suffering from a supply deficit, our army must be too large
        // There is no easy way to check the raw production if a civ has a supply deficit
        // We might try to divide the current production by the getUnitSupplyProductionPenalty()
        // but it only is true for our turn and not the previous turn and might result in odd values

        val productionRatio = civInfo.getStatForRanking(RankingType.Production).toFloat() / otherCiv.getStatForRanking(RankingType.Production).toFloat()
        val productionRatioModifier = when {
            productionRatio > 2f -> 10f
            productionRatio > 1.5f -> 5f
            productionRatio > 1.2 -> 3f
            productionRatio > .8f -> 0f
            productionRatio > .5f -> -5f
            productionRatio > .25f -> -10f
            else -> -15f
        }
        return productionRatioModifier
    }

    @Readonly
    private fun getScoreRatioModifier(otherCiv: Civilization, civInfo: Civilization): Float {
        // Civs with more score are more threatening to our victory
        // Bias towards attacking civs with a high score and low military
        // Bias against attacking civs with a low score and a high military
        // Designed to mitigate AIs declaring war on weaker civs instead of their rivals
        val scoreRatio = otherCiv.getStatForRanking(RankingType.Score).toFloat() / civInfo.getStatForRanking(RankingType.Score).toFloat()
        val scoreRatioModifier = when {
            scoreRatio > 2f -> 15f
            scoreRatio > 1.5f -> 10f
            scoreRatio > 1.25f -> 5f
            scoreRatio > 1f -> 2f
            scoreRatio > .8f -> 0f
            scoreRatio > .5f -> -2f
            scoreRatio > .25f -> -5f
            else -> -10f
        }
        return scoreRatioModifier * civInfo.getPersonality().modifierFocus(PersonalityValue.Culture, .3f)
    }

    @Readonly
    private fun getDefensivePactAlliesScore(otherCiv: Civilization, civInfo: Civilization, baseForce: Float, ourCombatStrength: Float): Float {
        var theirAlliesValue = 0f
        for (thirdCiv in otherCiv.diplomacy.values.filter { it.hasFlag(DiplomacyFlags.DefensivePact) && it.otherCiv() != civInfo }) {
            val thirdCivCombatStrengthRatio = (otherCiv.getStatForRanking(RankingType.Force).toFloat() + baseForce) / ourCombatStrength
            theirAlliesValue += when {
                thirdCivCombatStrengthRatio > 5 -> -15f
                thirdCivCombatStrengthRatio > 2.5 -> -10f
                thirdCivCombatStrengthRatio > 2 -> -8f
                thirdCivCombatStrengthRatio > 1.5 -> -5f
                thirdCivCombatStrengthRatio > .8 -> -2f
                else -> 0f
            }
        }
        return theirAlliesValue
    }

    @Readonly
    private fun getCombatStrengthModifier(civInfo: Civilization, targetCiv: Civilization, ourCombatStrength: Float, theirCombatStrength: Float): Float {
        var combatStrengthRatio = ourCombatStrength / theirCombatStrength

        // At higher difficulty levels the AI gets a unit production boost.
        // In that case while we may have more units than them, we don't nessesarily want to be more aggressive.
        // This is to reduce the amount that the AI targets players at these higher levels somewhat.
        if (civInfo.isAI() && targetCiv.isHuman()) {
            combatStrengthRatio *= civInfo.gameInfo.getDifficulty().aiUnitCostModifier.pow(1.5f)
        }
        val combatStrengthModifier = when {
            combatStrengthRatio > 5f -> 20f
            combatStrengthRatio > 4f -> 15f
            combatStrengthRatio > 3f -> 12f
            combatStrengthRatio > 2f -> 10f
            combatStrengthRatio > 1.8f -> 8f
            combatStrengthRatio > 1.6f -> 6f
            combatStrengthRatio > 1.4f -> 4f
            combatStrengthRatio > 1.2f -> 2f
            combatStrengthRatio > .8f -> -5f
            combatStrengthRatio > .6f -> -10f
            combatStrengthRatio > .4f -> -20f
            else -> -40f
        }
        return combatStrengthModifier
    }

    @Readonly
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
    @Readonly
    private fun getAttackPathsModifier(civInfo: Civilization, otherCiv: Civilization, targetCitiesWithOurCity: List<Pair<City, City>>): Float {

        @Readonly
        fun isTileCanMoveThrough(civInfo: Civilization, tile: Tile): Boolean {
            val owner = tile.getOwner()
            return !tile.isImpassible()
                    && (owner == otherCiv || owner == null || civInfo.diplomacyFunctions.canPassThroughTiles(owner))
        }

        @Readonly
        fun isLandTileCanMoveThrough(civInfo: Civilization, tile: Tile): Boolean {
            return tile.isLand && isTileCanMoveThrough(civInfo, tile)
        }

        val attackPaths: MutableList<List<Tile>> = mutableListOf()
        var attackPathModifiers: Float = -3f

        // For each city, we want to calculate if there is an attack path to the enemy
        for (attacksGroupedByCity in targetCitiesWithOurCity.groupBy { it.first }) {
            val cityToAttackFrom = attacksGroupedByCity.key
            var cityAttackValue = 0f

            // We only want to calculate the best attack path and use it's value
            // Land routes are clearly better than sea routes
            for ((_, cityToAttack) in attacksGroupedByCity.value) {
                val landAttackPath = MapPathing.getConnection(civInfo, cityToAttackFrom.getCenterTile(), cityToAttack.getCenterTile(), ::isLandTileCanMoveThrough)
                if (landAttackPath != null && landAttackPath.size < 16) {
                    attackPaths.add(landAttackPath)
                    cityAttackValue = 3f
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
            if (reachableEnemyCities.isEmpty()) return -50f // Can't even reach the enemy city, no point in war.
            val minAttackDistance = reachableEnemyCities.minOf { reachableEnemyCitiesBfs.getPathTo(it.getCenterTile()).count() }

            // Longer attack paths are worse, but if the attack path is too far away we shouldn't completely discard the possibility
            attackPathModifiers -= (minAttackDistance - 10).coerceIn(0, 30)
        }
        return attackPathModifiers
    }
}
