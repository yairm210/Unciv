package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object ReligionAutomation {

    // region faith spending

    fun spendFaithOnReligion(civInfo: Civilization) {
        if (civInfo.cities.isEmpty()) return

        // Save for great prophet
        if (civInfo.religionManager.religionState != ReligionState.EnhancedReligion
            && (civInfo.religionManager.remainingFoundableReligions() != 0 || civInfo.religionManager.religionState > ReligionState.Pantheon)
        ) {
            buyGreatProphetInAnyCity(civInfo)
            return
        }
        
        if (civInfo.religionManager.remainingFoundableReligions() == 0 ) {
            buyGreatPerson(civInfo)
            tryBuyAnyReligiousBuilding(civInfo)
            return
        }

        // If we don't have majority in all our own cities, build missionaries and inquisitors to solve this
        //TODO: in late game, this may not be worth it
        val citiesWithoutOurReligion = civInfo.cities.filter { it.religion.getMajorityReligion() != civInfo.religionManager.religion!! }
        // The original had a cap at 4 missionaries total, but 1/4 * the number of cities should be more appropriate imo
        if (citiesWithoutOurReligion.count() >
            4 * civInfo.units.getCivUnits().count {
                    it.hasUnique(UniqueType.CanSpreadReligion)
                    || it.hasUnique(UniqueType.CanRemoveHeresy) }
        ) {
            val (city, pressureDifference) = citiesWithoutOurReligion.map { city ->
                city to city.religion.getPressureDeficit(civInfo.religionManager.religion?.name)
            }.maxByOrNull { it.second }!!
            if (pressureDifference >= Constants.aiPreferInquisitorOverMissionaryPressureDifference)
                buyInquisitorNear(civInfo, city)
            buyMissionaryInAnyCity(civInfo)
            return
        }

        // Get an inquisitor to defend our holy city
        val holyCity = civInfo.religionManager.getHolyCity()
        if (holyCity != null
            && holyCity in civInfo.cities
            && civInfo.units.getCivUnits().count { it.hasUnique(UniqueType.PreventSpreadingReligion) } == 0
            && !holyCity.religion.isProtectedByInquisitor()
        ) {
            buyInquisitorNear(civInfo, holyCity)
            return
        }
        
        // Just buy missionaries to spread our religion outside of our civ
        if (civInfo.units.getCivUnits().count { it.hasUnique(UniqueType.CanSpreadReligion) } < 4) {
            buyMissionaryInAnyCity(civInfo)
            return
        }
        // Todo: declare war if enemy missionaries enter our civ without permission
    }

    private fun tryBuyAnyReligiousBuilding(civInfo: Civilization) {
        for (city in civInfo.cities) {
            if (city.religion.getMajorityReligion() == null) continue
            val buildings = city.religion.getMajorityReligion()!!.buildingsPurchasableByBeliefs
            val buildingToBePurchased = buildings
                .asSequence()
                .map { civInfo.getEquivalentBuilding(it) }
                .filter { it.isPurchasable(city.cityConstructions) }
                .filter { (it.getStatBuyCost(city, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith }
                .minByOrNull { it.getStatBuyCost(city, Stat.Faith)!! }
                ?: continue
            city.cityConstructions.purchaseConstruction(buildingToBePurchased, -1, true, Stat.Faith)
            return
        }
    }

    private fun buyMissionaryInAnyCity(civInfo: Civilization) {
        if (civInfo.religionManager.religionState < ReligionState.Religion) return
        var missionaries = civInfo.gameInfo.ruleset.units.values.filter { unit ->
                unit.hasUnique(UniqueType.CanSpreadReligion)
        }
        missionaries = missionaries.map { civInfo.getEquivalentUnit(it) }

        val missionaryConstruction = missionaries
            // Get list of cities it can be built in
            .associateBy({unit -> unit}) { unit -> civInfo.cities.filter { unit.isPurchasable(it.cityConstructions) && unit.canBePurchasedWithStat(it, Stat.Faith) } }
            .filter { it.value.isNotEmpty() }
            // And from that list determine the cheapest price
            .minByOrNull { it.value.minOf { city -> it.key.getStatBuyCost(city, Stat.Faith)!!  }}?.key
            ?: return


        val hasUniqueToTakeCivReligion = missionaryConstruction.hasUnique(UniqueType.TakeReligionOverBirthCity)

        val validCitiesToBuy = civInfo.cities.filter {
            (hasUniqueToTakeCivReligion || it.religion.getMajorityReligion() == civInfo.religionManager.religion)
            && (missionaryConstruction.getStatBuyCost(it, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith
            && missionaryConstruction.isPurchasable(it.cityConstructions)
            && missionaryConstruction.canBePurchasedWithStat(it, Stat.Faith)
        }
        if (validCitiesToBuy.isEmpty()) return

        val citiesWithBonusCharges = validCitiesToBuy.filter { city ->
            city.getMatchingUniques(UniqueType.UnitStartingPromotions).any {
                val promotionName = it.params[2]
                val promotion = city.getRuleset().unitPromotions[promotionName] ?: return@any false
                promotion.hasUnique(UniqueType.CanSpreadReligion)
            }
        }
        val holyCity = validCitiesToBuy.firstOrNull { it.isHolyCityOf(civInfo.religionManager.religion!!.name) }

        val cityToBuyMissionary = when {
            citiesWithBonusCharges.any() -> citiesWithBonusCharges.first()
            holyCity != null -> holyCity
            else -> validCitiesToBuy.first()
        }

        cityToBuyMissionary.cityConstructions.purchaseConstruction(missionaryConstruction, -1, true, Stat.Faith)
        return
    }

    private fun buyGreatProphetInAnyCity(civInfo: Civilization) {
        if (civInfo.religionManager.religionState < ReligionState.Religion) return
        var greatProphetUnit = civInfo.religionManager.getGreatProphetEquivalent() ?: return
        greatProphetUnit = civInfo.getEquivalentUnit(greatProphetUnit)
        val cityToBuyGreatProphet = civInfo.cities
            .asSequence()
            .filter { greatProphetUnit.isPurchasable(it.cityConstructions) }
            .filter { greatProphetUnit.canBePurchasedWithStat(it, Stat.Faith) }
            .filter { (greatProphetUnit.getStatBuyCost(it, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith }
            .minByOrNull { greatProphetUnit.getStatBuyCost(it, Stat.Faith)!! }
            ?: return
        cityToBuyGreatProphet.cityConstructions.purchaseConstruction(greatProphetUnit, -1, true, Stat.Faith)
    }

    private fun buyInquisitorNear(civInfo: Civilization, city: City) {
        if (civInfo.religionManager.religionState < ReligionState.Religion) return
        var inquisitors = civInfo.gameInfo.ruleset.units.values.filter {
            it.hasUnique(UniqueType.CanRemoveHeresy) || it.hasUnique(UniqueType.PreventSpreadingReligion)
        }

        inquisitors = inquisitors.map { civInfo.getEquivalentUnit(it) }

        val inquisitorConstruction = inquisitors
            // Get list of cities it can be built in
            .associateBy({unit -> unit}) { unit -> civInfo.cities.filter { unit.isPurchasable(it.cityConstructions) && unit.canBePurchasedWithStat(it, Stat.Faith) } }
            .filter { it.value.isNotEmpty() }
            // And from that list determine the cheapest price
            .minByOrNull { it.value.minOf { city -> it.key.getStatBuyCost(city, Stat.Faith)!!  }}?.key
            ?: return


        val hasUniqueToTakeCivReligion = inquisitorConstruction.hasUnique(UniqueType.TakeReligionOverBirthCity)

        val validCitiesToBuy = civInfo.cities.filter {
            (hasUniqueToTakeCivReligion || it.religion.getMajorityReligion() == civInfo.religionManager.religion)
            && (inquisitorConstruction.getStatBuyCost(it, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith
            && inquisitorConstruction.isPurchasable(it.cityConstructions)
            && inquisitorConstruction.canBePurchasedWithStat(it, Stat.Faith)
        }
        val cityToBuy = validCitiesToBuy
            .minByOrNull { it.getCenterTile().aerialDistanceTo(city.getCenterTile()) } ?: return

        cityToBuy.cityConstructions.purchaseConstruction(inquisitorConstruction, -1, true, Stat.Faith)
    }

    private fun buyGreatPerson(civInfo: Civilization) {
        val greatPersonUnit = civInfo.gameInfo.ruleset.units.values.filter {
            it.hasUnique(UniqueType.GreatPerson) && !it.hasUnique(UniqueType.MayFoundReligion) //we want to exclude great prophets from the list
        }
        val greatPersonConstruction = greatPersonUnit
            // Get list of cities it can be built in
            .associateBy({unit -> unit}) { unit -> civInfo.cities.filter { unit.isPurchasable(it.cityConstructions) && unit.canBePurchasedWithStat(it, Stat.Faith) } }
            .filter { it.value.isNotEmpty() }
            // And from that list determine the cheapest price
            .minByOrNull { it.value.minOf { city -> it.key.getStatBuyCost(city, Stat.Faith)!!  }}?.key
            ?: return

        val validCitiesToBuy = civInfo.cities.filter {
            (greatPersonConstruction.getStatBuyCost(it, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith
        }
        
        if (validCitiesToBuy.isEmpty()) return

        val cityToBuy = validCitiesToBuy.first()

        cityToBuy.cityConstructions.purchaseConstruction(greatPersonConstruction, -1, true, Stat.Faith)
    }

    // endregion

    // region rate beliefs

    fun rateBelief(civInfo: Civilization, belief: Belief): Float {
        var score = 0f // Roughly equivalent to the sum of stats gained across all cities

        for (city in civInfo.cities) {
            for (tile in city.getCenterTile().getTilesInDistance(city.getWorkRange())) {
                val tileScore = beliefBonusForTile(belief, tile, city)
                score += tileScore * when {
                    city.workedTiles.contains(tile.position) -> 1f // worked
                    tile.getCity() == city -> 0.7f // workable
                    else -> 0.3f // unavailable
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

    private fun beliefBonusForTile(belief: Belief, tile: Tile, city: City): Float {
        var bonusYield = 0f
        for (unique in belief.uniqueObjects) {
            when (unique.type) {
                UniqueType.StatsFromObject -> if (tile.matchesFilter(unique.params[1]))
                    bonusYield += unique.stats.values.sum()
                UniqueType.StatsFromTilesWithout ->
                    if (city.matchesFilter(unique.params[3])
                        && tile.matchesFilter(unique.params[1])
                        && !tile.matchesFilter(unique.params[2])
                    ) bonusYield += unique.stats.values.sum()
                // ToDo: Also calculate add stats for improvements that will be buildable
                else -> {}
            }
        }
        return bonusYield
    }

    private fun beliefBonusForCity(civInfo: Civilization, belief: Belief, city: City): Float {
        var score = 0f
        val ruleSet = civInfo.gameInfo.ruleset
        for (unique in belief.uniqueObjects) {
            val modifier = 0.5f.pow(unique.modifiers.size)
            // Multiply by 3/10 if has an obsoleted era
            // Multiply by 2 if enough pop/followers (best implemented with conditionals, so left open for now)
            // If obsoleted, continue
            score += modifier * when (unique.type) {
                UniqueType.GrowthPercentBonus -> unique.params[0].toFloat() / 3f
                UniqueType.BorderGrowthPercentage -> -unique.params[0].toFloat() * 2f / 10f
                UniqueType.StrengthForCities -> unique.params[0].toFloat() / 10f // Modified by personality
                UniqueType.CityHealingUnits -> unique.params[1].toFloat() / 10f
                UniqueType.PercentProductionBuildings -> unique.params[0].toFloat() / 3f
                UniqueType.PercentProductionWonders -> unique.params[0].toFloat() / 3f
                UniqueType.PercentProductionUnits -> unique.params[0].toFloat() / 3f
                UniqueType.StatsFromCitiesOnSpecificTiles ->
                    if (city.getCenterTile().matchesFilter(unique.params[1]))
                        unique.stats.values.sum() // Modified by personality
                    else 0f
                UniqueType.StatsFromObject ->
                    when {
                        ruleSet.buildings.containsKey(unique.params[1]) -> {
                            unique.stats.values.sum() *
                                if (ruleSet.buildings[unique.params[1]]!!.isNationalWonder) 0.5f //there's at most 1 copy of each of these in our empire
                                else 1.5f //yields for buildings are usually more desireable than faith-purchased buildings, as we might not need an upfront investment

                        }
                        ruleSet.specialists.containsKey(unique.params[1]) -> {
                            unique.stats.values.sum() *
                                if (city.population.population > 8f) 2f
                                else 1f
                        }
                        else -> unique.stats.values.sum() * 1f //should account for most edge cases here
                    }
                UniqueType.StatsFromTradeRoute ->
                    unique.stats.values.sum() *
                        if (city.isConnectedToCapital()) 2f
                        else 1f
                UniqueType.StatPercentFromReligionFollowers ->
                    min(unique.params[0].toFloat() * city.population.population, unique.params[2].toFloat())
                UniqueType.StatsPerCity ->
                    if (city.matchesFilter(unique.params[1]))
                        unique.stats.values.sum() * 2f //free yields
                    else 0f
                else -> 0f
            }
        }

        return score
    }

    private fun beliefBonusForPlayer(civInfo: Civilization, belief: Belief): Float {
        var score = 0f
        val numberOfFoundedReligions = civInfo.gameInfo.civilizations.count {
            it.religionManager.religion != null && it.religionManager.religionState >= ReligionState.Religion
        }
        val maxNumberOfReligions = numberOfFoundedReligions + civInfo.religionManager.remainingFoundableReligions()

        // adjusts scores of certain beliefs as game evolves (adapted from Civ 5 DLL files on AI belief selection)
        // enable differentiation of early vs late founding of religion and early vs late enhancement of religion
        // this is mainly for mods which may shuffle enhancer and founder beliefs w.r.t. base Unciv
        var gameTimeScalingPercent = 100
        when (civInfo.religionManager.religionState) {
            ReligionState.FoundingReligion -> {
                gameTimeScalingPercent = 100 - ((numberOfFoundedReligions * 100) / maxNumberOfReligions)
            }
            ReligionState.EnhancingReligion -> {
                val amountOfEnhancedReligions = civInfo.gameInfo.civilizations.count {
                    it.religionManager.religion != null && it.religionManager.religionState == ReligionState.EnhancedReligion
                }
                gameTimeScalingPercent = 100 - ((amountOfEnhancedReligions * 100) / maxNumberOfReligions)
            }
            else -> {} // pantheon shouldn't matter
        }
        val goodEarlyModifier = when {
            gameTimeScalingPercent < 33 -> 1f
            gameTimeScalingPercent < 66 -> 2f
            else -> 4f
        }
        val goodLateModifier = when {
            gameTimeScalingPercent < 33 -> 2f
            gameTimeScalingPercent < 66 -> 1f
            else -> 1/2f
        }

        for (unique in belief.uniqueObjects) {
            val modifier =
                if (unique.getModifiers(UniqueType.ConditionalOurUnit).any { it.params[0] == civInfo.religionManager.getGreatProphetEquivalent()?.name }) 1/2f
                else 1f
            // Some city-filters are modified by personality (non-enemy foreign cities)
            score += modifier * when (unique.type) {
                UniqueType.KillUnitPlunderNearCity ->
                    unique.params[0].toFloat() * 0.5f * //can be very strong, but a low weight for now as the AI currently isn't farming barb camp
                        if (civInfo.wantsToFocusOn(Victory.Focus.Military)) 2f
                        else 1f
                UniqueType.BuyUnitsForAmountStat, UniqueType.BuyBuildingsForAmountStat ->
                    if (civInfo.religionManager.religion != null
                        && civInfo.religionManager.religion!!.followerBeliefUniqueMap.getUniques(unique.type).any()
                    ) 0f
                    // This is something completely different from the original, but I have no idea
                    // what happens over there
                    else civInfo.stats.statsForNextTurn[Stat.valueOf(unique.params[2])] * 200f / unique.params[1].toFloat() //the costs of these are probably similar to the baseUnitBuyCost
                UniqueType.BuyUnitsWithStat, UniqueType.BuyBuildingsWithStat ->
                    if (civInfo.religionManager.religion != null
                        && civInfo.religionManager.religion!!.followerBeliefUniqueMap.getUniques(unique.type).any()
                    ) 0f
                    // This is something completely different from the original, but I have no idea
                    // what happens over there
                    else civInfo.stats.statsForNextTurn[Stat.valueOf(unique.params[1])] * 200f / civInfo.getEra().baseUnitBuyCost //baseUnitBuyCost is 200 in Standard speed pre-Renaissance
                UniqueType.BuyUnitsByProductionCost ->
                    0f //Holy Warriors is a waste if we don't buy units with it, and if we buy units with it'll cost us great persons
                UniqueType.StatsWhenSpreading ->
                    unique.params[0].toFloat() / 10f
                UniqueType.StatsWhenAdoptingReligion ->
                    unique.stats.values.sum() / 50f
                UniqueType.RestingPointOfCityStatesFollowingReligionChange ->
                    if (civInfo.wantsToFocusOn(Victory.Focus.CityStates))
                        unique.params[0].toFloat() / 4f
                    else
                        unique.params[0].toFloat() / 8f
                UniqueType.StatsFromGlobalCitiesFollowingReligion ->
                    unique.stats.values.sum() * 2f //free yields that are potentially more than our own number of cities would allow
                UniqueType.StatsFromGlobalFollowers ->
                    10f * (unique.stats.values.sum() / unique.params[1].toFloat())
                UniqueType.Strength ->
                    unique.params[0].toFloat() * 2f//combat strength from beliefs is very strong
                UniqueType.ReligionSpreadDistance ->
                    (10f + unique.params[0].toFloat()) * goodEarlyModifier
                UniqueType.NaturalReligionSpreadStrength ->
                    unique.params[0].toFloat() * goodEarlyModifier / 5f //We should weigh this according to cityFilter; Religious Texts is way stronger than Religious Unity
                UniqueType.SpreadReligionStrength ->
                    unique.params[0].toFloat() * goodLateModifier / 5f
                UniqueType.FaithCostOfGreatProphetChange ->
                    -unique.params[0].toFloat() * goodLateModifier / 10f //It's only about 1 more prophet, due to the increasing costs.
                UniqueType.BuyBuildingsDiscount, UniqueType.BuyUnitsDiscount ->
                    -unique.params[2].toFloat() * goodLateModifier / 5f
                UniqueType.BuyItemsDiscount ->
                    -unique.params[1].toFloat() * goodLateModifier / 5f
                else -> 0f
            }
        }

        return score
    }


    internal fun chooseReligiousBeliefs(civInfo: Civilization) {
        choosePantheon(civInfo)
        foundReligion(civInfo)
        enhanceReligion(civInfo)
        chooseFreeBeliefs(civInfo)
    }

    private fun choosePantheon(civInfo: Civilization) {
        if (!civInfo.religionManager.canFoundOrExpandPantheon()) return
        // So looking through the source code of the base game available online,
        // the functions for choosing beliefs total in at around 400 lines.
        // https://github.com/Gedemon/Civ5-DLL/blob/aa29e80751f541ae04858b6d2a2c7dcca454201e/CvGameCoreDLL_Expansion1/CvReligionClasses.cpp
        // line 4426 through 4870.
        // This is way too much work for now, so I'll just choose a random pantheon instead.
        // Should probably be changed later, but it works for now.
        val chosenPantheon = chooseBeliefOfType(civInfo, BeliefType.Pantheon)
            ?: return // panic!
        civInfo.religionManager.chooseBeliefs(
            listOf(chosenPantheon),
            useFreeBeliefs = civInfo.religionManager.usingFreeBeliefs()
        )
    }

    private fun foundReligion(civInfo: Civilization) {
        if (civInfo.religionManager.religionState != ReligionState.FoundingReligion) return
        val availableReligionIcons = civInfo.gameInfo.ruleset.religions
            .filterNot { civInfo.gameInfo.religions.values.map { religion -> religion.name }.contains(it) }
        val favoredReligion = civInfo.nation.favoredReligion
        val religionIcon =
            if (favoredReligion != null && favoredReligion in availableReligionIcons
                && (1..10).random() <= 5) favoredReligion
            else availableReligionIcons.randomOrNull()
                ?: return // Wait what? How did we pass the checking when using a great prophet but not this?

        civInfo.religionManager.foundReligion(religionIcon, religionIcon)

        val chosenBeliefs = chooseBeliefs(civInfo, civInfo.religionManager.getBeliefsToChooseAtFounding()).toList()
        civInfo.religionManager.chooseBeliefs(chosenBeliefs)
    }

    private fun enhanceReligion(civInfo: Civilization) {
        if (civInfo.religionManager.religionState != ReligionState.EnhancingReligion) return
        civInfo.religionManager.chooseBeliefs(
            chooseBeliefs(civInfo, civInfo.religionManager.getBeliefsToChooseAtEnhancing()).toList()
        )
    }

    private fun chooseFreeBeliefs(civInfo: Civilization) {
        if (!civInfo.religionManager.hasFreeBeliefs()) return
        civInfo.religionManager.chooseBeliefs(
            chooseBeliefs(civInfo, civInfo.religionManager.freeBeliefsAsEnums()).toList(),
            useFreeBeliefs = true
        )
    }

    private fun chooseBeliefs(civInfo: Civilization, beliefsToChoose: Counter<BeliefType>): HashSet<Belief> {
        val chosenBeliefs = hashSetOf<Belief>()
        // The `continue`s should never be reached, but just in case I'd rather have the AI have a
        // belief less than make the game crash. The `continue`s should only be reached whenever
        // there are not enough beliefs to choose, but there should be, as otherwise we could
        // not have used a great prophet to found/enhance our religion.
        for (belief in BeliefType.entries) {
            if (belief == BeliefType.None) continue
            repeat(beliefsToChoose[belief]) {
                chosenBeliefs.add(
                    chooseBeliefOfType(civInfo, belief, chosenBeliefs) ?: return@repeat
                )
            }
        }
        return chosenBeliefs
    }

    private fun chooseBeliefOfType(civInfo: Civilization, beliefType: BeliefType, additionalBeliefsToExclude: HashSet<Belief> = hashSetOf()): Belief? {
        return civInfo.gameInfo.ruleset.beliefs.values
            .filter {
                (it.type == beliefType || beliefType == BeliefType.Any)
                    && !additionalBeliefsToExclude.contains(it)
                    && civInfo.religionManager.getReligionWithBelief(it) == null
                    && it.getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)
                    .none { unique -> !unique.conditionalsApply(civInfo.state) }
            }
            .maxByOrNull { rateBelief(civInfo, it) }
    }


    //endregion
}
