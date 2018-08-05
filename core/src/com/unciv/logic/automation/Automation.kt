package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.getRandom
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Automation {


    internal fun rankTile(tile: TileInfo, civInfo: CivilizationInfo): Float {
        val stats = tile.getTileStats(null, civInfo)
        var rank = 0.0f
        if (stats.food <= 2) rank += stats.food
        else rank += (2 + (stats.food - 2) / 2) // 1 point for each food up to 2, from there on half a point
        if(civInfo.gold>0 && civInfo.getStatsForNextTurn().gold>0) rank += stats.gold / 2
        else rank += stats.gold
        rank += stats.production
        rank += stats.science
        rank += stats.culture
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.hasViewableResource(civInfo)) rank += 1.0f
        return rank
    }

    fun automateCivMoves(civInfo: CivilizationInfo) {
        if (civInfo.tech.techsToResearch.isEmpty()) {
            val researchableTechs = GameBasics.Technologies.values.filter { civInfo.tech.canBeResearched(it.name) }
            val techToResearch = researchableTechs.groupBy { it.cost }.minBy { it.key }!!.value.getRandom()
            civInfo.tech.techsResearched.add(techToResearch.name)
        }

        while(civInfo.policies.canAdoptPolicy()){
            val adoptablePolicies = GameBasics.PolicyBranches.values.flatMap { it.policies.union(listOf(it))}
                    .filter { civInfo.policies.isAdoptable(it) }
            val policyToAdopt = adoptablePolicies.getRandom()
            civInfo.policies.adopt(policyToAdopt)
        }

        // trade luxuries for luxuries if you can
        for(otherCiv in civInfo.diplomacy.values.map { it.otherCiv() }.filterNot { it.isPlayerCivilization() }){
            val tradeLogic = TradeLogic(civInfo, otherCiv)
            val ourTradableLuxuryResources = tradeLogic.ourAvailableOffers
                    .filter {  it.type==TradeType.Luxury_Resource && it.amount>1 }
            val theirTradableLuxuryResources  = tradeLogic.theirAvailableOffers
                    .filter {  it.type==TradeType.Luxury_Resource && it.amount>1 }
            val weHaveTheyDont = ourTradableLuxuryResources
                    .filter { resource -> tradeLogic.theirAvailableOffers
                            .none { it.name==resource.name && it.type==TradeType.Luxury_Resource } }
            val theyHaveWeDont = theirTradableLuxuryResources
                    .filter { resource -> tradeLogic.ourAvailableOffers
                            .none { it.name==resource.name && it.type==TradeType.Luxury_Resource  } }
            val numberOfTrades = min(weHaveTheyDont.size,theyHaveWeDont.size)
            if(numberOfTrades>0){
                tradeLogic.currentTrade.ourOffers.addAll(weHaveTheyDont.take(numberOfTrades).map { it.copy(amount = 1) })
                tradeLogic.currentTrade.theirOffers.addAll(theyHaveWeDont.take(numberOfTrades).map { it.copy(amount = 1) })
                tradeLogic.acceptTrade()
            }
        }

        fun getMinDistanceBetweenCities(civ1:CivilizationInfo,civ2:CivilizationInfo): Float {
            return civ1.cities.map { city -> civ2.cities.map { it.getCenterTile().arialDistanceTo(city.getCenterTile()) }.min()!! }.min()!!
        }

        // Declare war?
        if(civInfo.cities.isNotEmpty() && civInfo.diplomacy.isNotEmpty()
                && !civInfo.isAtWar()) {

            val enemyCivsByDistanceToOurs = civInfo.diplomacy.values.map { it.otherCiv() }
                    .filterNot { it == civInfo || it.cities.isEmpty() || !civInfo.diplomacy[it.civName]!!.canDeclareWar() }
                    .groupBy { getMinDistanceBetweenCities(civInfo,it) }
            val ourCombatStrength = evaluteCombatStrength(civInfo)
            for (group in enemyCivsByDistanceToOurs){
                if(group.key>7) break
                for(otherCiv in group.value){
                    if(evaluteCombatStrength(otherCiv)*1.5<ourCombatStrength){
                        civInfo.diplomacy[otherCiv.civName]!!.declareWar()
                        break
                    }
                }
            }
        }


        val rangedUnits = mutableListOf<MapUnit>()
        val meleeUnits = mutableListOf<MapUnit>()
        val civilianUnits = mutableListOf<MapUnit>()

        for (unit in civInfo.getCivUnits()) {
            if(unit.promotions.canBePromoted()){
                val availablePromotions = unit.promotions.getAvailablePromotions()
                if(availablePromotions.isNotEmpty())
                    unit.promotions.addPromotion(availablePromotions.getRandom().name)
            }

            val unitType = unit.getBaseUnit().unitType
            if(unitType.isRanged()) rangedUnits.add(unit)
            else if(unitType.isMelee()) meleeUnits.add(unit)
            else civilianUnits.add(unit)
        }

        for (unit in civilianUnits) UnitAutomation().automateUnitMoves(unit) // They move first so that combat units can accompany a settler
        for (unit in rangedUnits) UnitAutomation().automateUnitMoves(unit)
        for (unit in meleeUnits) UnitAutomation().automateUnitMoves(unit)


        for (city in civInfo.cities) {
            // reassign everyone from scratch
            city.workedTiles.clear()
            (0..city.population.population).forEach { city.population.autoAssignPopulation()}
            chooseNextConstruction(city.cityConstructions)
            if (city.health < city.getMaxHealth())
                trainCombatUnit(city) // override previous decision if city is under attack
        }


        // train settler?
        if (civInfo.cities.any()
                && civInfo.happiness > civInfo.cities.size +5
                && civInfo.getCivUnits().none { it.name == "Settler" }
                && civInfo.cities.none { it.cityConstructions.currentConstruction == "Settler" }) {

            val bestCity = civInfo.cities.maxBy { it.cityStats.currentCityStats.production }!!
            if(bestCity.cityConstructions.builtBuildings.size > 1) // 2 buildings or more, otherwisse focus on self first
                bestCity.cityConstructions.currentConstruction = "Settler"
        }

    }

    private fun trainCombatUnit(city: CityInfo) {
        val combatUnits = city.cityConstructions.getConstructableUnits().filter { it.unitType != UnitType.Civilian }
        val chosenUnit: BaseUnit
        if(!city.civInfo.isAtWar() && city.civInfo.cities.any { it.getCenterTile().militaryUnit==null}
                && combatUnits.any { it.unitType== UnitType.Ranged }) // this is for city defence so get an archery unit if we can
            chosenUnit = combatUnits.filter { it.unitType== UnitType.Ranged }.maxBy { it.cost }!!

        else{ // randomize type of unit and take the most expensive of its kind
            val chosenUnitType = combatUnits.map { it.unitType }.distinct().filterNot{it==UnitType.Scout}.getRandom()
            chosenUnit = combatUnits.filter { it.unitType==chosenUnitType }.maxBy { it.cost }!!
        }

        city.cityConstructions.currentConstruction = chosenUnit.name
    }


    fun chooseNextConstruction(cityConstructions: CityConstructions) {
        cityConstructions.run {
            //currentConstruction="" // This is so that if we're currently in the middle of building a wonder,
            // buildableWonders will still contain it

            val buildableNotWonders = getBuildableBuildings().filterNot { it.isWonder }
            val buildableWonders = getBuildableBuildings().filter { it.isWonder }

            val civUnits = cityInfo.civInfo.getCivUnits()
            val militaryUnits = civUnits.filter { it.getBaseUnit().unitType != UnitType.Civilian }.size
            val workers = civUnits.filter { it.name == CityConstructions.Worker }.size
            val cities = cityInfo.civInfo.cities.size

            val goldBuildings = buildableNotWonders.filter { it.gold>0 }
            val wartimeBuildings = buildableNotWonders.filter { it.xpForNewUnits>0 || it.cityStrength>0 }.sortedBy { it.maintenance }
            val zeroMaintainanceBuildings = buildableNotWonders.filter { it.maintenance == 0 && it !in wartimeBuildings }
            val isAtWar = cityInfo.civInfo.isAtWar()

            when {
                buildableNotWonders.isNotEmpty() // if the civ is in the gold red-zone, build markets or somesuch
                        && cityInfo.civInfo.getStatsForNextTurn().gold <0
                        && goldBuildings.isNotEmpty()
                        -> currentConstruction = goldBuildings.first().name
                currentConstruction!="" -> return
                buildableNotWonders.any { it.name=="Monument"} -> currentConstruction = "Monument"
                buildableNotWonders.any { it.name=="Granary"} -> currentConstruction = "Granary"
                buildableNotWonders.any { it.name=="Library"} -> currentConstruction = "Library"
                buildableNotWonders.any { it.name=="Market"} -> currentConstruction = "Market"
                militaryUnits==0 -> trainCombatUnit(cityInfo)
                workers==0 -> currentConstruction = CityConstructions.Worker
                zeroMaintainanceBuildings.isNotEmpty() -> currentConstruction = zeroMaintainanceBuildings.getRandom().name
                isAtWar && militaryUnits<cities -> trainCombatUnit(cityInfo)
                isAtWar && wartimeBuildings.isNotEmpty() -> currentConstruction = wartimeBuildings.getRandom().name
                workers<cities/2 -> currentConstruction = CityConstructions.Worker
                militaryUnits<cities -> trainCombatUnit(cityInfo)
                buildableNotWonders.isNotEmpty() -> currentConstruction = buildableNotWonders.minBy { it.maintenance }!!.name
                buildableWonders.isNotEmpty() -> currentConstruction = buildableWonders.getRandom().name
                else -> trainCombatUnit(cityInfo)
            }

            cityInfo.civInfo.addNotification("Work has started on [$currentConstruction]", cityInfo.location, Color.BROWN)
        }
    }


    fun evaluteCombatStrength(civInfo: CivilizationInfo): Int {
        // Since units become exponentially stronger per combat strength increase, we square em all
        fun square(x:Int) = x*x
        val unitStrength =  civInfo.getCivUnits().map { square(max(it.getBaseUnit().strength, it.getBaseUnit().rangedStrength)) }.sum()
        val cityStrength = civInfo.cities.map { square(CityCombatant(it).getCityStrength()) }.sum()
        return (sqrt(unitStrength.toDouble()) /*+ sqrt(cityStrength.toDouble())*/).toInt()
    }

}

