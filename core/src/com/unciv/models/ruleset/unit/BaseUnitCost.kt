package com.unciv.models.ruleset.unit

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.toPercent

class BaseUnitCost(val baseUnit: BaseUnit) {

    fun getProductionCost(civInfo: Civilization): Int {
        var productionCost = baseUnit.cost.toFloat()

        for (unique in baseUnit.getMatchingUniques(UniqueType.CostIncreasesPerCity, StateForConditionals(civInfo)))
            productionCost += civInfo.cities.size * unique.params[0].toInt()

        for (unique in baseUnit.getMatchingUniques(UniqueType.CostIncreasesWhenBuilt, StateForConditionals(civInfo)))
            productionCost += civInfo.civConstructions.builtItemsWithIncreasingPrice[baseUnit.name] * unique.params[0].toInt()

        if (civInfo.isCityState())
            productionCost *= 1.5f
        productionCost *= if (civInfo.isHuman())
            civInfo.getDifficulty().unitCostModifier
        else
            civInfo.gameInfo.getDifficulty().aiUnitCostModifier
        productionCost *= civInfo.gameInfo.speed.productionCostModifier
        return productionCost.toInt()
    }


    /** Contains only unit-specific uniques that allow purchasing with stat */
    fun canBePurchasedWithStat(city: City, stat: Stat): Boolean {
        val conditionalState = StateForConditionals(civInfo = city.civ, city = city)

        if (city.getMatchingUniques(UniqueType.BuyUnitsIncreasingCost, conditionalState)
                    .any {
                        it.params[2] == stat.name
                                && baseUnit.matchesFilter(it.params[0])
                                && city.matchesFilter(it.params[3])
                    }
        ) return true

        if (city.getMatchingUniques(UniqueType.BuyUnitsByProductionCost, conditionalState)
                    .any { it.params[1] == stat.name && baseUnit.matchesFilter(it.params[0]) }
        )
            return true

        if (city.getMatchingUniques(UniqueType.BuyUnitsWithStat, conditionalState)
                    .any {
                        it.params[1] == stat.name
                                && baseUnit.matchesFilter(it.params[0])
                                && city.matchesFilter(it.params[2])
                    }
        )
            return true

        if (city.getMatchingUniques(UniqueType.BuyUnitsForAmountStat, conditionalState)
                    .any {
                        it.params[2] == stat.name
                                && baseUnit.matchesFilter(it.params[0])
                                && city.matchesFilter(it.params[3])
                    }
        )
            return true

        return false
    }


    fun getStatBuyCost(city: City, stat: Stat): Int? {
        var cost = baseUnit.getBaseBuyCost(city, stat)?.toDouble() ?: return null

        for (unique in city.getMatchingUniques(UniqueType.BuyUnitsDiscount)) {
            if (stat.name == unique.params[0] && baseUnit.matchesFilter(unique.params[1]))
                cost *= unique.params[2].toPercent()
        }
        for (unique in city.getMatchingUniques(UniqueType.BuyItemsDiscount))
            if (stat.name == unique.params[0])
                cost *= unique.params[1].toPercent()

        return (cost / 10f).toInt() * 10
    }


    fun getBaseBuyCosts(city: City, stat: Stat): Sequence<Float> {
        val conditionalState = StateForConditionals(civInfo = city.civ, city = city)
        return sequence {
            yieldAll(city.getMatchingUniques(UniqueType.BuyUnitsIncreasingCost, conditionalState)
                .filter {
                    it.params[2] == stat.name
                            && baseUnit.matchesFilter(it.params[0])
                            && city.matchesFilter(it.params[3])
                }.map {
                    baseUnit.getCostForConstructionsIncreasingInPrice(
                        it.params[1].toInt(),
                        it.params[4].toInt(),
                        city.civ.civConstructions.boughtItemsWithIncreasingPrice[baseUnit.name]
                    ) * city.civ.gameInfo.speed.statCostModifiers[stat]!!
                }
            )
            yieldAll(city.getMatchingUniques(UniqueType.BuyUnitsByProductionCost, conditionalState)
                .filter { it.params[1] == stat.name && baseUnit.matchesFilter(it.params[0]) }
                .map { (getProductionCost(city.civ) * it.params[2].toInt()).toFloat() }
            )

            if (city.getMatchingUniques(UniqueType.BuyUnitsWithStat, conditionalState)
                        .any {
                            it.params[1] == stat.name
                                    && baseUnit.matchesFilter(it.params[0])
                                    && city.matchesFilter(it.params[2])
                        }
            ) yield(city.civ.getEra().baseUnitBuyCost * city.civ.gameInfo.speed.statCostModifiers[stat]!!)

            yieldAll(city.getMatchingUniques(UniqueType.BuyUnitsForAmountStat, conditionalState)
                .filter {
                    it.params[2] == stat.name
                            && baseUnit.matchesFilter(it.params[0])
                            && city.matchesFilter(it.params[3])
                }.map { it.params[1].toInt() * city.civ.gameInfo.speed.statCostModifiers[stat]!! }
            )
        }
    }
}
