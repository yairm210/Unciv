package com.unciv.logic.automation

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom
import kotlin.math.min

class NextTurnAutomation{

    fun automateCivMoves(civInfo: CivilizationInfo) {
        chooseTechToResearch(civInfo)
        adoptPolicy(civInfo)
        exchangeLuxuries(civInfo)
        declareWar(civInfo)
        automateUnits(civInfo)
        reassignWorkedTiles(civInfo)
        trainSettler(civInfo)
    }

    private fun chooseTechToResearch(civInfo: CivilizationInfo) {
        if (civInfo.tech.techsToResearch.isEmpty()) {
            val researchableTechs = GameBasics.Technologies.values.filter { civInfo.tech.canBeResearched(it.name) }
            val techToResearch = researchableTechs.groupBy { it.cost }.minBy { it.key }!!.value.getRandom()
            civInfo.tech.techsResearched.add(techToResearch.name)
        }
    }

    private fun adoptPolicy(civInfo: CivilizationInfo) {
        while (civInfo.policies.canAdoptPolicy()) {
            val adoptablePolicies = GameBasics.PolicyBranches.values.flatMap { it.policies.union(listOf(it)) }
                    .filter { civInfo.policies.isAdoptable(it) }
            val policyToAdopt = adoptablePolicies.getRandom()
            civInfo.policies.adopt(policyToAdopt)
        }
    }

    private fun exchangeLuxuries(civInfo: CivilizationInfo) {
        for (otherCiv in civInfo.diplomacy.values.map { it.otherCiv() }.filterNot { it.isPlayerCivilization() }) {
            val tradeLogic = TradeLogic(civInfo, otherCiv)
            val ourTradableLuxuryResources = tradeLogic.ourAvailableOffers
                    .filter { it.type == TradeType.Luxury_Resource && it.amount > 1 }
            val theirTradableLuxuryResources = tradeLogic.theirAvailableOffers
                    .filter { it.type == TradeType.Luxury_Resource && it.amount > 1 }
            val weHaveTheyDont = ourTradableLuxuryResources
                    .filter { resource ->
                        tradeLogic.theirAvailableOffers
                                .none { it.name == resource.name && it.type == TradeType.Luxury_Resource }
                    }
            val theyHaveWeDont = theirTradableLuxuryResources
                    .filter { resource ->
                        tradeLogic.ourAvailableOffers
                                .none { it.name == resource.name && it.type == TradeType.Luxury_Resource }
                    }
            val numberOfTrades = min(weHaveTheyDont.size, theyHaveWeDont.size)
            if (numberOfTrades > 0) {
                tradeLogic.currentTrade.ourOffers.addAll(weHaveTheyDont.take(numberOfTrades).map { it.copy(amount = 1) })
                tradeLogic.currentTrade.theirOffers.addAll(theyHaveWeDont.take(numberOfTrades).map { it.copy(amount = 1) })
                tradeLogic.acceptTrade()
            }
        }
    }

    fun getMinDistanceBetweenCities(civ1: CivilizationInfo, civ2: CivilizationInfo): Int {
        return civ1.cities.map { city -> civ2.cities.map { it.getCenterTile().arialDistanceTo(city.getCenterTile()) }.min()!! }.min()!!
    }

    private fun declareWar(civInfo: CivilizationInfo) {
        if (civInfo.cities.isNotEmpty() && civInfo.diplomacy.isNotEmpty()
                && !civInfo.isAtWar()
                && civInfo.getCivUnits().filter { !it.type.isCivilian() }.size > civInfo.cities.size * 2) {

            val enemyCivsByDistanceToOurs = civInfo.diplomacy.values.map { it.otherCiv() }
                    .filterNot { it == civInfo || it.cities.isEmpty() || !civInfo.diplomacy[it.civName]!!.canDeclareWar() }
                    .groupBy { getMinDistanceBetweenCities(civInfo, it) }
                    .toSortedMap()
            val ourCombatStrength = Automation().evaluteCombatStrength(civInfo)
            for (group in enemyCivsByDistanceToOurs) {
                if (group.key > 7) break
                for (otherCiv in group.value) {
                    if (Automation().evaluteCombatStrength(otherCiv) * 2 < ourCombatStrength) {
                        civInfo.diplomacy[otherCiv.civName]!!.declareWar()
                        break
                    }
                }
            }
        }
    }

    private fun automateUnits(civInfo: CivilizationInfo) {
        val rangedUnits = mutableListOf<MapUnit>()
        val meleeUnits = mutableListOf<MapUnit>()
        val civilianUnits = mutableListOf<MapUnit>()

        for (unit in civInfo.getCivUnits()) {
            if (unit.promotions.canBePromoted()) {
                val availablePromotions = unit.promotions.getAvailablePromotions()
                if (availablePromotions.isNotEmpty())
                    unit.promotions.addPromotion(availablePromotions.getRandom().name)
            }

            val unitType = unit.type
            when {
                unitType.isRanged() -> rangedUnits.add(unit)
                unitType.isMelee() -> meleeUnits.add(unit)
                else -> civilianUnits.add(unit)
            }
        }

        for (unit in civilianUnits) UnitAutomation().automateUnitMoves(unit) // They move first so that combat units can accompany a settler
        for (unit in rangedUnits) UnitAutomation().automateUnitMoves(unit)
        for (unit in meleeUnits) UnitAutomation().automateUnitMoves(unit)
    }

    private fun reassignWorkedTiles(civInfo: CivilizationInfo) {
        for (city in civInfo.cities) {
            city.workedTiles.clear()
            city.population.specialists.clear()
            (0..city.population.population).forEach { city.population.autoAssignPopulation() }
            Automation().chooseNextConstruction(city.cityConstructions)
            if (city.health < city.getMaxHealth())
                Automation().trainCombatUnit(city) // override previous decision if city is under attack
        }
    }

    private fun trainSettler(civInfo: CivilizationInfo) {
        if (civInfo.cities.any()
                && civInfo.happiness > civInfo.cities.size + 5
                && civInfo.getCivUnits().none { it.name == "Settler" }
                && civInfo.cities.none { it.cityConstructions.currentConstruction == "Settler" }) {

            val bestCity = civInfo.cities.maxBy { it.cityStats.currentCityStats.production }!!
            if (bestCity.cityConstructions.builtBuildings.size > 1) // 2 buildings or more, otherwisse focus on self first
                bestCity.cityConstructions.currentConstruction = "Settler"
        }
    }

}