package com.unciv.logic.automation

import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import kotlin.math.min
import kotlin.random.Random

object ChooseBeliefsAutomation {
    
    fun rateBelief(civInfo: CivilizationInfo, belief: Belief): Float {
        var score = 0f
        
        for (city in civInfo.cities) {
            for (tile in city.getCenterTile().getTilesInDistance(3)) {
                val tileScore = beliefBonusForTile(belief, tile, city)
                score += tileScore * when {
                    city.workedTiles.contains(tile.position) -> 8
                    tile.getCity() == city -> 5
                    else -> 3
                } * (Random.nextFloat() * 0.05f + 0.975f) 
            }
            
            score += beliefBonusForCity(civInfo, belief, city) * (Random.nextFloat() * 0.1f + 0.95f)
        }
        
        score += beliefBonusForPlayer(civInfo, belief) * (Random.nextFloat() * 0.3f + 0.85f)

        // All of these Random.nextFloat() don't exist in the original, but I've added them to make things a bit more random.
        
        if (belief.type == BeliefType.Pantheon)
            score *= 0.9f
        
        return score
    }
    
    private fun beliefBonusForTile(belief: Belief, tile: TileInfo, city: CityInfo): Float {
        var bonusYield = 0f
        for (unique in belief.uniqueObjects) {
            when (unique.placeholderText) {
                UniqueType.StatsFromObject.placeholderText -> if (tile.matchesFilter(unique.params[1]))
                    bonusYield += unique.stats.values.sum()
                "[] from [] tiles without [] []" -> 
                    if (city.matchesFilter(unique.params[3])
                        && tile.matchesFilter(unique.params[1])
                        && !tile.matchesFilter(unique.params[2])
                    ) bonusYield += unique.stats.values.sum()
                // ToDo: Also calculate add stats for improvements that will be buildable
            }
        }
        return bonusYield
    }
    
    private fun beliefBonusForCity(civInfo: CivilizationInfo, belief: Belief, city: CityInfo): Float {
        var score = 0f
        val ruleSet = civInfo.gameInfo.ruleSet
        for (unique in belief.uniqueObjects) {
            var modifier = 1f
            if (unique.conditionals.any { it.isOfType(UniqueType.ConditionalWar) 
                || it.isOfType(UniqueType.ConditionalNotWar) 
                || it.isOfType(UniqueType.ConditionalAttacking)
                || it.isOfType(UniqueType.ConditionalDefending) }
            ) {
                modifier *= 0.5f
            }
            // Multiply by 3/10 if has an obsoleted era
            // Multiply by 2 if enough pop/followers (best implemented with conditionals, so left open for now)
            // If obsoleted, continue
            score += modifier * when (unique.placeholderText) {
                UniqueType.GrowthPercentBonus.placeholderText -> unique.params[0].toFloat() / 3f
                "[]% cost of natural border growth" -> -unique.params[0].toFloat() * 2f / 10f
                "[]% Strength for cities" -> unique.params[0].toFloat() / 10f // Modified by personality
                "[] Units adjacent to this city heal [] HP per turn when healing" -> unique.params[1].toFloat() / 10f
                "+[]% Production when constructing []" -> unique.params[0].toFloat() / 3f
                UniqueType.StatsFromCitiesOnSpecificTiles.placeholderText ->
                    if (city.getCenterTile().matchesFilter(unique.params[1])) 
                        unique.stats.values.sum() // Modified by personality 
                    else 0f
                UniqueType.StatsFromObject.placeholderText,
                "[] from every [] in cities where this religion has at least [] followers" ->
                    when {
                        ruleSet.buildings.containsKey(unique.params[1]) -> {
                            unique.stats.values.sum() / 
                                if (ruleSet.buildings[unique.params[1]]!!.isWonder) 2f
                                else 1f
                            
                        }
                        ruleSet.specialists.containsKey(unique.params[1]) -> {
                            unique.stats.values.sum() *
                                if (city.population.population > 8f) 3f
                                else 1f
                        }
                        else -> 0f
                    }
                UniqueType.StatsFromXPopulation.placeholderText ->
                    unique.stats.values.sum() // Modified by personality
                "[] from each Trade Route" ->
                    unique.stats.values.sum() *
                        if (city.isConnectedToCapital()) 2f
                        else 1f
                "[]% [] from every follower, up to []%" -> 
                    min(unique.params[0].toFloat() * city.population.population, unique.params[2].toFloat()) 
                UniqueType.StatsPerCity.placeholderText ->
                    if (city.matchesFilter(unique.params[1]))
                        unique.stats.values.sum()
                    else 0f
                else -> 0f
            }
        }
        
        return score
    }
    
    private fun beliefBonusForPlayer(civInfo: CivilizationInfo, belief: Belief): Float {
        var score = 0f
        val amountOfEnhancedReligions = civInfo.religionManager.amountOfFoundableReligions()
        val goodEarlyModifier = when {
            amountOfEnhancedReligions < 33 -> 1f
            amountOfEnhancedReligions < 66 -> 2f
            else -> 4f
        }
        val goodLateModifier = when {
            amountOfEnhancedReligions < 33 -> 2f
            amountOfEnhancedReligions < 66 -> 1f
            else -> 1/2f
        } 
        
        for (unique in belief.uniqueObjects) {
            val modifier =
                if (unique.conditionals.any { it.type == UniqueType.ConditionalOurUnit && it.params[0] == civInfo.religionManager.getGreatProphetEquivalent() }) 1/2f
                else 1f
            // Some city-filters are modified by personality (non-enemy foreign cities)
            score += modifier * when (unique.placeholderText) {
                "Earn []% of [] unit's [] as [] when killed within 4 tiles of a city following this religion" ->
                    unique.params[0].toFloat() * 4f *
                        if (civInfo.victoryType() == VictoryType.Domination) 2f
                        else 1f
                "May buy [] buildings for [] [] []", "May buy [] units for [] [] []" ->
                    if (civInfo.religionManager.religion != null 
                        && civInfo.religionManager.religion!!.getFollowerUniques()
                            .any { it.placeholderText == unique.placeholderText } 
                    ) 0f
                    // This is something completely different from the original, but I have no idea
                    // what happens over there
                    else civInfo.statsForNextTurn[Stat.valueOf(unique.params[2])] * 5f / unique.params[1].toFloat()
                "May buy [] buildings with []", "May buy [] units with []" ->
                    if (civInfo.religionManager.religion != null
                        && civInfo.religionManager.religion!!.getFollowerUniques()
                            .any { it.placeholderText == unique.placeholderText }
                    ) 0f
                    // This is something completely different from the original, but I have no idea
                    // what happens over there
                    else civInfo.statsForNextTurn[Stat.valueOf(unique.params[1])] * 10f / civInfo.getEra().baseUnitBuyCost
                UniqueType.BuyUnitsByProductionCost.placeholderText ->
                    15f * if (civInfo.victoryType() == VictoryType.Domination) 2f else 1f
                "when a city adopts this religion for the first time (modified by game speed)" -> // Modified by personality
                    unique.stats.values.sum() * 10f
                "When spreading religion to a city, gain [] times the amount of followers of other religions as []" ->
                    unique.params[0].toInt() / 5f
                "[] when a city adopts this religion for the first time (modified by game speed)" ->
                    unique.stats.values.sum() / 50f
                "Resting point for influence with City-States following this religion []" ->
                    unique.params[0].toInt() / 7f
                "[] for each global city following this religion" ->
                    50f / unique.stats.values.sum()
                UniqueType.StatsSpendingGreatPeople.placeholderText ->
                    unique.stats.values.sum() / 2f
                "[]% Natural religion spread to []" ->
                    unique.params[0].toFloat() / 4f
                UniqueType.Strength.placeholderText ->
                    unique.params[0].toInt() / 4f
                "Religion naturally spreads to cities [] tiles away" ->
                    (10 + unique.params[0].toInt()) / goodEarlyModifier
                "[]% Natural religion spread []", "[]% Natural religion spread [] with []" ->
                    (10 + unique.params[0].toInt()) / goodEarlyModifier
                UniqueType.SpreadReligionStrength.placeholderText ->
                    unique.params[0].toInt() / goodLateModifier
                "[]% Faith cost of generating Great Prophet equivalents" ->
                    unique.params[0].toInt() / goodLateModifier / 2f    
                "[] cost for [] units []%" ->
                    unique.params[2].toInt() / goodLateModifier
                else -> 0f
            }
        }
        
        return score
    }
}
