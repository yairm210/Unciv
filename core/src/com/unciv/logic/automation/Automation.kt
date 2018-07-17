package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.Unit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.getRandom

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
            val techToResearch = researchableTechs.minBy { it.cost }
            civInfo.tech.techsResearched.add(techToResearch!!.name)
        }

        while(civInfo.policies.canAdoptPolicy()){
            val adoptablePolicies = GameBasics.PolicyBranches.values.flatMap { it.policies.union(listOf(it))}
                    .filter { civInfo.policies.isAdoptable(it) }
            val policyToAdopt = adoptablePolicies.getRandom()
            civInfo.policies.adopt(policyToAdopt)
        }

        // Order roads between cities if you can
//        for(city in civInfo.cities.filter { it.population.population>3 && !it.isCapital()
//                &&  !it.cityStats.isConnectedToCapital(RoadStatus.Road) }){
//            val closestConnectedCity = civInfo.cities.filter { it.isCapital() || it.cityStats.isConnectedToCapital(RoadStatus.Road) }
//                    .minBy { HexMath().getDistance(city.location,it.location) }!!
//            val pathToClosestCity = civInfo.gameInfo.tileMap.getShortestPathBetweenTwoTiles(city.getCenterTile(),closestConnectedCity.getCenterTile())
//        }


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
        val chosenUnit: Unit
        if(city.civInfo.cities.any { it.getCenterTile().militaryUnit==null}
                && combatUnits.any { it.unitType== UnitType.Ranged }) // this is for city defence so get an archery unit if we can
            chosenUnit = combatUnits.filter { it.unitType== UnitType.Ranged }.maxBy { it.cost }!!

        else{ // randomize type of unit and takee the most expensive of its kind
            val chosenUnitType = combatUnits.map { it.unitType }.distinct().filterNot{it==UnitType.Scout}.getRandom()
            chosenUnit = combatUnits.filter { it.unitType==chosenUnitType }.maxBy { it.cost }!!
        }

        city.cityConstructions.currentConstruction = chosenUnit.name
    }


    fun chooseNextConstruction(cityConstructions: CityConstructions) {
        cityConstructions.run {
            currentConstruction="" // This is so that if we're currently in the middle of building a wonder,
            // buildableWonders will still contain it

            val buildableNotWonders = getBuildableBuildings().filterNot { it.isWonder }
            val buildableWonders = getBuildableBuildings().filter { it.isWonder }

            val civUnits = cityInfo.civInfo.getCivUnits()
            val militaryUnits = civUnits.filter { it.getBaseUnit().unitType != UnitType.Civilian }.size
            val workers = civUnits.filter { it.name == CityConstructions.Worker }.size
            val cities = cityInfo.civInfo.cities.size

            val goldBuildings = buildableNotWonders.filter { it.gold>0 }
            val zeroMaintainanceBuildings = buildableNotWonders.filter { it.maintenance == 0 }

            when {
                buildableNotWonders.isNotEmpty() // if the civ is in the gold red-zone, build markets or somesuch
                        && cityInfo.civInfo.getStatsForNextTurn().gold <0
                        && goldBuildings.isNotEmpty()
                        -> currentConstruction = goldBuildings.first().name
                buildableNotWonders.any { it.name=="Monument"} -> currentConstruction = "Monument"
                buildableNotWonders.any { it.name=="Granary"} -> currentConstruction = "Granary"
                buildableNotWonders.any { it.name=="Library"} -> currentConstruction = "Library"
                buildableNotWonders.any { it.name=="Market"} -> currentConstruction = "Market"
                militaryUnits==0 -> trainCombatUnit(cityInfo)
                workers==0 -> currentConstruction = CityConstructions.Worker
                zeroMaintainanceBuildings.isNotEmpty() -> currentConstruction = zeroMaintainanceBuildings.getRandom().name
                buildableWonders.isNotEmpty() -> currentConstruction = buildableWonders.getRandom().name
                buildableNotWonders.isNotEmpty() -> currentConstruction = buildableNotWonders.minBy { it.maintenance }!!.name
                militaryUnits<cities -> trainCombatUnit(cityInfo)
                workers<cities/2 -> currentConstruction = CityConstructions.Worker
                else -> trainCombatUnit(cityInfo)
            }

            cityInfo.civInfo.addNotification("Work has started on [$currentConstruction]", cityInfo.location, Color.BROWN)
        }
    }

}

