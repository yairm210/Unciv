package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.Unit
import com.unciv.ui.utils.getRandom

class Automation {


    internal fun rankTile(tile: TileInfo, civInfo: CivilizationInfo): Float {
        val stats = tile.getTileStats(null, civInfo)
        var rank = 0.0f
        if (stats.food <= 2) rank += stats.food
        else rank += (2 + (stats.food - 2) / 2f) // 1 point for each food up to 2, from there on half a point
        rank += stats.gold / 2
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

        for (unit in civInfo.getCivUnits()) {
            UnitAutomation().automateUnitMoves(unit)
        }

        // train settler?
        if (civInfo.cities.any()
                && civInfo.happiness > 2*civInfo.cities.size +5
                && civInfo.getCivUnits().none { it.name == "Settler" }
                && civInfo.cities.none { it.cityConstructions.currentConstruction == "Settler" }) {

            val bestCity = civInfo.cities.maxBy { it.cityStats.currentCityStats.production }!!
            if(bestCity.cityConstructions.builtBuildings.size > 1) // 2 buildings or more, otherwisse focus on self first
                bestCity.cityConstructions.currentConstruction = "Settler"
        }

        for (city in civInfo.cities) {
            if (city.health < city.getMaxHealth()) trainCombatUnit(city)
            // reassign everyone from scratch
            city.workedTiles.clear()
            (0..city.population.population).forEach { city.population.autoAssignPopulation()}
        }

    }

    private fun trainCombatUnit(city: CityInfo) {
        val combatUnits = city.cityConstructions.getConstructableUnits().filter { it.unitType != UnitType.Civilian }
        val chosenUnit: Unit
        if(city.civInfo.cities.any { it.getCenterTile().militaryUnit==null}
                && combatUnits.any { it.unitType== UnitType.Archery }) // this is for city defence so get an archery unit if we can
            chosenUnit = combatUnits.filter { it.unitType== UnitType.Archery }.maxBy { it.cost }!!

        else{ // randomize type of unit and takee the most expensive of its kind
            val chosenUnitType = combatUnits.map { it.unitType }.distinct().getRandom()
            chosenUnit = combatUnits.filter { it.unitType==chosenUnitType }.maxBy { it.cost }!!
        }

        city.cityConstructions.currentConstruction = chosenUnit.name
    }


    fun chooseNextConstruction(cityConstructions: CityConstructions) {
        cityConstructions.run {
            val buildableNotWonders = getBuildableBuildings().filterNot { it.isWonder }
            val buildableWonders = getBuildableBuildings().filter { it.isWonder }

            val civUnits = cityInfo.civInfo.getCivUnits()
            val militaryUnits = civUnits.filter { it.getBaseUnit().unitType != UnitType.Civilian }.size
            val workers = civUnits.filter { it.name == CityConstructions.Worker }.size
            val cities = cityInfo.civInfo.cities.size

            when {
                !buildableNotWonders.isEmpty() -> currentConstruction = buildableNotWonders.first().name
                militaryUnits==0 -> trainCombatUnit(cityInfo)
                workers==0 -> currentConstruction = CityConstructions.Worker
                militaryUnits<cities -> trainCombatUnit(cityInfo)
                workers<cities -> currentConstruction = CityConstructions.Worker
                buildableWonders.isNotEmpty() -> currentConstruction = buildableWonders.getRandom().name
                else -> trainCombatUnit(cityInfo)
            }

            if (cityInfo.civInfo == cityInfo.civInfo.gameInfo.getPlayerCivilization())
                cityInfo.civInfo.addNotification("Work has started on $currentConstruction", cityInfo.location, Color.BROWN)
        }
    }

}

