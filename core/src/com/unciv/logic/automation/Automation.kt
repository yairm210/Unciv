package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.getRandom
import kotlin.math.max
import kotlin.math.sqrt

class Automation {

    internal fun rankTile(tile: TileInfo?, civInfo: CivilizationInfo): Float {
        if (tile == null) return 0.0f
        val stats = tile.getTileStats(null, civInfo)
        var rank = rankStatsValue(stats, civInfo)
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.hasViewableResource(civInfo)) rank += 1.0f
        return rank
    }

    internal fun rankSpecialist(stats: Stats?, civInfo: CivilizationInfo): Float {
        if (stats == null) return 0.0f
        var rank = rankStatsValue(stats, civInfo)
        rank += 0.3f //GPP bonus
        return rank
    }

    fun rankStatsValue(stats: Stats, civInfo: CivilizationInfo): Float {
        var rank = 0.0f
        if (stats.food <= 2) rank += (stats.food * 1.2f) //food get more value to kepp city growing
        else rank += (2.4f + (stats.food - 2) / 2) // 1.2 point for each food up to 2, from there on half a point

        if (civInfo.gold < 0 && civInfo.getStatsForNextTurn().gold <= 0) rank += stats.gold
        else rank += stats.gold / 2

        rank += stats.production
        rank += stats.science
        rank += stats.culture
        return rank
    }

    fun trainCombatUnit(city: CityInfo) {
        val name = chooseCombatUnit(city)
        city.cityConstructions.currentConstruction = name
    }

    fun chooseCombatUnit(city: CityInfo) : String {
        val combatUnits = city.cityConstructions.getConstructableUnits().filter { !it.unitType.isCivilian() }
        val chosenUnit: BaseUnit
        if(!city.civInfo.isAtWar() && city.civInfo.cities.any { it.getCenterTile().militaryUnit==null}
                && combatUnits.any { it.unitType== UnitType.Ranged }) // this is for city defence so get an archery unit if we can
            chosenUnit = combatUnits.filter { it.unitType== UnitType.Ranged }.maxBy { it.cost }!!

        else{ // randomize type of unit and take the most expensive of its kind
            val chosenUnitType = combatUnits.map { it.unitType }.distinct().filterNot{it==UnitType.Scout}.getRandom()
            chosenUnit = combatUnits.filter { it.unitType==chosenUnitType }.maxBy { it.cost }!!
        }
        return chosenUnit.name
    }


    fun chooseNextConstruction(cityConstructions: CityConstructions) {
        cityConstructions.run {
            if (currentConstruction!="") return
            val buildableNotWonders = getBuildableBuildings().filterNot { it.isWonder }
            val buildableWonders = getBuildableBuildings().filter { it.isWonder }
            val buildableUnits = getConstructableUnits()

            val civUnits = cityInfo.civInfo.getCivUnits()
            val militaryUnits = civUnits.filter { !it.type.isCivilian()}.size
            val workers = civUnits.filter { it.name == CityConstructions.Worker }.size
            val cities = cityInfo.civInfo.cities.size
            val canBuildWorkboat = cityInfo.cityConstructions.getConstructableUnits().map { it.name }.contains("Work Boats")
                    && !cityInfo.getTiles().any { it.civilianUnit?.name == "Work Boats" }
            val needWorkboat = canBuildWorkboat
                    && cityInfo.getTiles().any { it.isWater() && it.hasViewableResource(cityInfo.civInfo) && it.improvement == null }

            val isAtWar = cityInfo.civInfo.isAtWar()
            val cityProduction = cityInfo.cityStats.currentCityStats.production.toFloat()

            var buildingValues = HashMap<String, Float>()
            //Food buildings : Granary and lighthouse and hospital
            val foodBuilding = buildableNotWonders.filter { it.food>0
                    || (it.resourceBonusStats!=null && it.resourceBonusStats!!.food>0) }
                    .minBy{ it.cost }
            if (foodBuilding!=null) {
                buildingValues[foodBuilding.name] = foodBuilding.cost / cityProduction
                if (cityInfo.population.population < foodBuilding.food + 5) {
                    buildingValues[foodBuilding.name] = buildingValues[foodBuilding.name]!! / 2.0f
                }
            }

            //Production buildings : Workshop, factory
            val productionBuilding = buildableNotWonders.filter { it.production>0
                    || (it.resourceBonusStats!=null && it.resourceBonusStats!!.production>0) }
                    .minBy{it.cost}
            if (productionBuilding!=null) {
                buildingValues[productionBuilding.name] = productionBuilding.cost / cityProduction / 1.5f
            }

            //Gold buildings : Market, bank
            val goldBuilding = buildableNotWonders.filter { it.gold>0
                    || (it.resourceBonusStats!=null && it.resourceBonusStats!!.gold>0) }
                    .minBy{it.cost}
            if (goldBuilding!=null) {
                buildingValues[goldBuilding.name] = goldBuilding.cost / cityProduction / 1.2f
                if (cityInfo.civInfo.getStatsForNextTurn().gold<0) {
                    buildingValues[goldBuilding.name] = buildingValues[goldBuilding.name]!! / 3.0f
                }
            }

            //Happiness
            val happinessBuilding = buildableNotWonders.filter { it.happiness>0
                    || (it.resourceBonusStats!=null && it.resourceBonusStats!!.happiness>0) }
                    .minBy{it.cost}
            if (happinessBuilding!=null) {
                buildingValues[happinessBuilding.name] = happinessBuilding.cost / cityProduction
                if (cityInfo.civInfo.happiness < 0) {
                    buildingValues[happinessBuilding.name] = buildingValues[happinessBuilding.name]!! / 3.0f
                }
            }

            //War buildings
            val wartimeBuildings = buildableNotWonders.filter { it.xpForNewUnits>0 || it.cityStrength>0 }
                    .minBy { it.cost }
            if (wartimeBuildings!=null) {
                buildingValues[wartimeBuildings.name] = wartimeBuildings.cost / cityProduction
            }

            //Wonders
            if (buildableWonders.isNotEmpty()) {
                val wonder = buildableWonders.getRandom()
                buildingValues[wonder.name] = wonder.cost / cityProduction / 5.0f
            }

            //other buildings
            val other = buildableNotWonders.minBy{it.cost}
            if (other!=null) {
                buildingValues[other.name] = other.cost / cityProduction * 1.2f
            }

            //worker
            if (workers<(cities+1)/2) {
                buildingValues[CityConstructions.Worker] =
                        buildableUnits.first{ it.name == CityConstructions.Worker }.cost / cityProduction *
                                (workers/(cities+1))
            }

            //Work boat
            if (needWorkboat) {
                buildingValues["Work Boats"] =
                        buildableUnits.first{ it.name == "Work Boats" }.cost / cityProduction * 1.5f
            }

            //Army
            val militaryUnit = chooseCombatUnit(cityInfo)
            buildingValues[militaryUnit] =
                    buildableUnits.first{ it.name == militaryUnit }.cost / cityProduction * 1.5f * militaryUnits / (cities+1)
            if (isAtWar) {
                buildingValues[militaryUnit] = buildingValues[militaryUnit]!! / 3.0f
            }

            val name = buildingValues.minBy{it.value}!!.key
            currentConstruction = name
            cityInfo.civInfo.addNotification("Work has started on [$currentConstruction]", cityInfo.location, Color.BROWN)
        }
    }


    fun evaluteCombatStrength(civInfo: CivilizationInfo): Int {
        // Since units become exponentially stronger per combat strength increase, we square em all
        fun square(x:Int) = x*x
        val unitStrength =  civInfo.getCivUnits().map { square(max(it.baseUnit().strength, it.baseUnit().rangedStrength)) }.sum()
        val cityStrength = civInfo.cities.map { square(CityCombatant(it).getCityStrength()) }.sum()
        return (sqrt(unitStrength.toDouble()) /*+ sqrt(cityStrength.toDouble())*/).toInt() + 1 //avoid 0
    }

    fun threatAssessment(assessor:CivilizationInfo, assessed: CivilizationInfo): ThreatLevel {
        val powerLevelComparison = evaluteCombatStrength(assessed)/evaluteCombatStrength(assessor).toFloat()
        when{
            powerLevelComparison>2 -> return ThreatLevel.VeryHigh
            powerLevelComparison>1.5f -> return ThreatLevel.High
            powerLevelComparison<(1/1.5f) -> return ThreatLevel.Low
            powerLevelComparison<0.5f -> return ThreatLevel.VeryLow
            else -> return ThreatLevel.Medium
        }
    }

}

enum class ThreatLevel{
    VeryLow,
    Low,
    Medium,
    High,
    VeryHigh
}

