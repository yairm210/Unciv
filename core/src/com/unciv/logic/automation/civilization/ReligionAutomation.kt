package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.ReligionState
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object ReligionAutomation {

    // region faith spending

    fun spendFaithOnReligion(civInfo: CivilizationInfo) {
        if (civInfo.cities.isEmpty()) return

        // Save for great prophet
        if (civInfo.religionManager.religionState != ReligionState.EnhancedReligion
            && (civInfo.religionManager.remainingFoundableReligions() != 0 || civInfo.religionManager.religionState > ReligionState.Pantheon)
        ) {
            buyGreatProphetInAnyCity(civInfo)
            return
        }

        // We don't have a religion and no more change of getting it :(
        if (civInfo.religionManager.religionState <= ReligionState.Pantheon) {
            tryBuyAnyReligiousBuilding(civInfo)
            // Todo: buy Great People post industrial era
            return
        }

        // If we don't have majority in all our own cities, build missionaries and inquisitors to solve this
        val citiesWithoutOurReligion = civInfo.cities.filter { it.religion.getMajorityReligion() != civInfo.religionManager.religion!! }
        // The original had a cap at 4 missionaries total, but 1/4 * the number of cities should be more appropriate imo
        if (citiesWithoutOurReligion.count() >
            4 * civInfo.getCivUnits().count { it.canDoReligiousAction(Constants.spreadReligion) || it.canDoReligiousAction(Constants.removeHeresy) }
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
            && civInfo.getCivUnits().count { it.hasUnique(UniqueType.PreventSpreadingReligion) } == 0
            && !holyCity.religion.isProtectedByInquisitor()
        ) {
            buyInquisitorNear(civInfo, holyCity)
            return
        }

        // Buy religious buildings in cities if possible
        val citiesWithMissingReligiousBuildings = civInfo.cities.filter { city ->
            city.religion.getMajorityReligion() != null
            && !city.cityConstructions.builtBuildings.containsAll(city.religion.getMajorityReligion()!!.buildingsPurchasableByBeliefs)
        }
        if (citiesWithMissingReligiousBuildings.any()) {
            tryBuyAnyReligiousBuilding(civInfo)
            return
        }

        // Todo: buy Great People post industrial era

        // Just buy missionaries to spread our religion outside of our civ
        if (civInfo.getCivUnits().count { it.canDoReligiousAction(Constants.spreadReligion) } < 4) {
            buyMissionaryInAnyCity(civInfo)
            return
        }
        // Todo: buy inquisitors for defence of other cities
    }

    private fun tryBuyAnyReligiousBuilding(civInfo: CivilizationInfo) {
        for (city in civInfo.cities) {
            if (city.religion.getMajorityReligion() == null) continue
            val buildings = city.religion.getMajorityReligion()!!.buildingsPurchasableByBeliefs
            val buildingToBePurchased = buildings
                .asSequence()
                .map { civInfo.getEquivalentBuilding(it).name }
                .map { city.cityConstructions.getConstruction(it) as INonPerpetualConstruction }
                .filter { it.isPurchasable(city.cityConstructions) }
                .filter { (it.getStatBuyCost(city, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith }
                .minByOrNull { it.getStatBuyCost(city, Stat.Faith)!! }
                ?: continue
            city.cityConstructions.purchaseConstruction(buildingToBePurchased.name, -1, true, Stat.Faith)
            return
        }
    }

    private fun buyMissionaryInAnyCity(civInfo: CivilizationInfo) {
        if (civInfo.religionManager.religionState < ReligionState.Religion) return
        var missionaries = civInfo.gameInfo.ruleSet.units.values.filter { unit ->
            unit.getMatchingUniques(UniqueType.CanActionSeveralTimes).filter { it.params[0] == Constants.spreadReligion }.any()
        }
        missionaries = missionaries.map { civInfo.getEquivalentUnit(it) }

        val missionaryConstruction = missionaries
            // Get list of cities it can be built in
            .associateBy({unit -> unit}) { unit -> civInfo.cities.filter { unit.isPurchasable(it.cityConstructions) && unit.canBePurchasedWithStat(it, Stat.Faith) } }
            .filter { it.value.isNotEmpty() }
            // And from that list determine the cheapest price
            .minByOrNull { it.value.minOf { city -> it.key.getStatBuyCost(city, Stat.Faith)!!  }}?.key
            ?: return


        val hasUniqueToTakeCivReligion = civInfo.gameInfo.ruleSet.units[missionaryConstruction.name]!!.hasUnique(UniqueType.TakeReligionOverBirthCity)

        val validCitiesToBuy = civInfo.cities.filter {
            (hasUniqueToTakeCivReligion || it.religion.getMajorityReligion() == civInfo.religionManager.religion)
            && (missionaryConstruction.getStatBuyCost(it, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith
            && missionaryConstruction.isPurchasable(it.cityConstructions)
            && missionaryConstruction.canBePurchasedWithStat(it, Stat.Faith)
        }
        if (validCitiesToBuy.isEmpty()) return

        val citiesWithBonusCharges = validCitiesToBuy.filter { city ->
            city.getLocalMatchingUniques(UniqueType.UnitStartingActions).filter { it.params[2] == Constants.spreadReligion }.any()
        }
        val holyCity = validCitiesToBuy.firstOrNull { it.isHolyCityOf(civInfo.religionManager.religion!!.name) }

        val cityToBuyMissionary = when {
            citiesWithBonusCharges.any() -> citiesWithBonusCharges.first()
            holyCity != null -> holyCity
            else -> validCitiesToBuy.first()
        }

        cityToBuyMissionary.cityConstructions.purchaseConstruction(missionaryConstruction.name, -1, true, Stat.Faith)
        return
    }

    private fun buyGreatProphetInAnyCity(civInfo: CivilizationInfo) {
        if (civInfo.religionManager.religionState < ReligionState.Religion) return
        var greatProphetUnit = civInfo.religionManager.getGreatProphetEquivalent() ?: return
        greatProphetUnit = civInfo.getEquivalentUnit(greatProphetUnit).name
        val greatProphetConstruction = civInfo.cities.first().cityConstructions.getConstruction(greatProphetUnit) as INonPerpetualConstruction
        val cityToBuyGreatProphet = civInfo.cities
            .asSequence()
            .filter { greatProphetConstruction.isPurchasable(it.cityConstructions) }
            .filter { greatProphetConstruction.canBePurchasedWithStat(it, Stat.Faith) }
            .filter { (greatProphetConstruction.getStatBuyCost(it, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith }
            .minByOrNull { greatProphetConstruction.getStatBuyCost(it, Stat.Faith)!! }
            ?: return
        cityToBuyGreatProphet.cityConstructions.purchaseConstruction(greatProphetUnit, -1, true, Stat.Faith)
    }

    private fun buyInquisitorNear(civInfo: CivilizationInfo, city: CityInfo) {
        if (civInfo.religionManager.religionState < ReligionState.Religion) return
        var inquisitors = civInfo.gameInfo.ruleSet.units.values.filter {
            it.getMapUnit(civInfo).canDoReligiousAction(Constants.removeHeresy)
            || it.hasUnique(UniqueType.PreventSpreadingReligion)
        }

        inquisitors = inquisitors.map { civInfo.getEquivalentUnit(it) }

        val inquisitorConstruction = inquisitors
            // Get list of cities it can be built in
            .associateBy({unit -> unit}) { unit -> civInfo.cities.filter { unit.isPurchasable(it.cityConstructions) && unit.canBePurchasedWithStat(it, Stat.Faith) } }
            .filter { it.value.isNotEmpty() }
            // And from that list determine the cheapest price
            .minByOrNull { it.value.minOf { city -> it.key.getStatBuyCost(city, Stat.Faith)!!  }}?.key
            ?: return


        val hasUniqueToTakeCivReligion = civInfo.gameInfo.ruleSet.units[inquisitorConstruction.name]!!.hasUnique(UniqueType.TakeReligionOverBirthCity)

        val validCitiesToBuy = civInfo.cities.filter {
            (hasUniqueToTakeCivReligion || it.religion.getMajorityReligion() == civInfo.religionManager.religion)
            && (inquisitorConstruction.getStatBuyCost(it, Stat.Faith) ?: return@filter false) <= civInfo.religionManager.storedFaith
            && inquisitorConstruction.isPurchasable(it.cityConstructions)
            && inquisitorConstruction.canBePurchasedWithStat(it, Stat.Faith)
        }
        if (validCitiesToBuy.isEmpty()) return

        val cityToBuy = validCitiesToBuy
            .filter {
                inquisitorConstruction.isPurchasable(it.cityConstructions)
                && inquisitorConstruction.canBePurchasedWithStat(it, Stat.Faith)
            }
            .minByOrNull { it.getCenterTile().aerialDistanceTo(city.getCenterTile()) } ?: return

        cityToBuy.cityConstructions.purchaseConstruction(inquisitorConstruction.name, -1, true, Stat.Faith)
    }

    // endregion

    // region rate beliefs

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

    private fun beliefBonusForCity(civInfo: CivilizationInfo, belief: Belief, city: CityInfo): Float {
        var score = 0f
        val ruleSet = civInfo.gameInfo.ruleSet
        for (unique in belief.uniqueObjects) {
            val modifier = 0.5f.pow(unique.conditionals.size)
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
                UniqueType.StatsFromTradeRoute ->
                    unique.stats.values.sum() *
                        if (city.isConnectedToCapital()) 2f
                        else 1f
                UniqueType.StatPercentFromReligionFollowers ->
                    min(unique.params[0].toFloat() * city.population.population, unique.params[2].toFloat())
                UniqueType.StatsPerCity ->
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
        val numberOfFoundedReligions = civInfo.gameInfo.civilizations.count {
            it.religionManager.religion != null && it.religionManager.religionState >= ReligionState.Religion
        }
        val maxNumberOfReligions = numberOfFoundedReligions + civInfo.religionManager.remainingFoundableReligions()

        // adjusts scores of certain beliefs as game evolves (adapted from Civ 5 DLL files on AI belief selection)
        // enable differentiation of early vs late founding of religion and early vs late enhancement of religion
        // this is mainly for mods which may shuffle enhancer and founder beliefs w.r.t. base UnCiv
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
                if (unique.conditionals.any { it.type == UniqueType.ConditionalOurUnit && it.params[0] == civInfo.religionManager.getGreatProphetEquivalent() }) 1/2f
                else 1f
            // Some city-filters are modified by personality (non-enemy foreign cities)
            score += modifier * when (unique.type) {
                UniqueType.KillUnitPlunderNearCity ->
                    unique.params[0].toFloat() * 4f *
                        if (civInfo.wantsToFocusOn(Victory.Focus.Military)) 2f
                        else 1f
                UniqueType.BuyUnitsForAmountStat, UniqueType.BuyBuildingsForAmountStat ->
                    if (civInfo.religionManager.religion != null
                        && civInfo.religionManager.religion!!.getFollowerUniques()
                            .any { it.type == unique.type }
                    ) 0f
                    // This is something completely different from the original, but I have no idea
                    // what happens over there
                    else civInfo.statsForNextTurn[Stat.valueOf(unique.params[2])] * 5f / unique.params[1].toFloat()
                UniqueType.BuyUnitsWithStat, UniqueType.BuyBuildingsWithStat ->
                    if (civInfo.religionManager.religion != null
                        && civInfo.religionManager.religion!!.getFollowerUniques()
                            .any { it.type == unique.type }
                    ) 0f
                    // This is something completely different from the original, but I have no idea
                    // what happens over there
                    else civInfo.statsForNextTurn[Stat.valueOf(unique.params[1])] * 10f / civInfo.getEra().baseUnitBuyCost
                UniqueType.BuyUnitsByProductionCost ->
                    15f * if (civInfo.wantsToFocusOn(Victory.Focus.Military)) 2f else 1f
                UniqueType.StatsWhenSpreading ->
                    unique.params[0].toFloat() / 5f
                UniqueType.StatsWhenAdoptingReligion, UniqueType.StatsWhenAdoptingReligionSpeed ->
                    unique.stats.values.sum() / 50f
                UniqueType.RestingPointOfCityStatesFollowingReligionChange ->
                    if (civInfo.wantsToFocusOn(Victory.Focus.CityStates))
                        unique.params[0].toFloat() / 3.5f
                    else
                        unique.params[0].toFloat() / 7f
                UniqueType.StatsFromGlobalCitiesFollowingReligion ->
                    unique.stats.values.sum()
                UniqueType.StatsFromGlobalFollowers ->
                    4f * (unique.stats.values.sum() / unique.params[1].toFloat())
                UniqueType.StatsSpendingGreatPeople ->
                    unique.stats.values.sum() / 2f
                UniqueType.Strength ->
                    unique.params[0].toFloat() / 4f
                UniqueType.ReligionSpreadDistance ->
                    (10f + unique.params[0].toFloat()) * goodEarlyModifier
                UniqueType.NaturalReligionSpreadStrength ->
                    unique.params[0].toFloat() * goodEarlyModifier / 5f
                UniqueType.SpreadReligionStrength ->
                    unique.params[0].toFloat() * goodLateModifier / 5f
                UniqueType.FaithCostOfGreatProphetChange ->
                    -unique.params[0].toFloat() * goodLateModifier / 2f
                UniqueType.BuyBuildingsDiscount, UniqueType.BuyItemsDiscount, UniqueType.BuyUnitsDiscount ->
                    -unique.params[2].toFloat() * goodLateModifier / 5f
                else -> 0f
            }
        }

        return score
    }

    //endregion
}
