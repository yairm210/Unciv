package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.SpecialConstruction
import com.unciv.logic.civilization.CityAction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.VictoryType
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import kotlin.math.max
import kotlin.math.min
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
        if (stats.food <= 2) rank += (stats.food * 1.2f) //food get more value to keep city growing
        else rank += (2.4f + (stats.food - 2) / 2) // 1.2 point for each food up to 2, from there on half a point

        if (civInfo.gold < 0 && civInfo.statsForNextTurn.gold <= 0) rank += stats.gold
        else rank += stats.gold / 2

        rank += stats.production
        rank += stats.science
        rank += stats.culture
        return rank
    }

    fun trainMilitaryUnit(city: CityInfo) {
        val name = chooseMilitaryUnit(city)
        city.cityConstructions.currentConstruction = name
    }

    fun chooseMilitaryUnit(city: CityInfo) : String {
        var militaryUnits = city.cityConstructions.getConstructableUnits().filter { !it.unitType.isCivilian() }
        if (militaryUnits.map { it.name }.contains(city.cityConstructions.currentConstruction))
            return city.cityConstructions.currentConstruction

        val findWaterConnectedCitiesAndEnemies = BFS(city.getCenterTile()){it.isWater || it.isCityCenter()}
        findWaterConnectedCitiesAndEnemies.stepToEnd()
        if(findWaterConnectedCitiesAndEnemies.tilesReached.keys.none {
                    (it.isCityCenter() && it.getOwner() != city.civInfo)
                            || (it.militaryUnit != null && it.militaryUnit!!.civInfo != city.civInfo)
                }) // there is absolutely no reason for you to make water units on this body of water.
            militaryUnits = militaryUnits.filter { it.unitType.isLandUnit() }

        val chosenUnit: BaseUnit
        if(!city.civInfo.isAtWar() && city.civInfo.cities.any { it.getCenterTile().militaryUnit==null}
                && militaryUnits.any { it.unitType== UnitType.Ranged }) // this is for city defence so get an archery unit if we can
            chosenUnit = militaryUnits.filter { it.unitType== UnitType.Ranged }.maxBy { it.cost }!!

        else{ // randomize type of unit and take the most expensive of its kind
            val chosenUnitType = militaryUnits.map { it.unitType }.distinct().filterNot{it==UnitType.Scout}.random()
            chosenUnit = militaryUnits.filter { it.unitType==chosenUnitType }.maxBy { it.cost }!!
        }
        return chosenUnit.name
    }


    fun chooseNextConstruction(cityConstructions: CityConstructions) {
        cityConstructions.run {
            if(!UnCivGame.Current.settings.autoAssignCityProduction) return
            if (getCurrentConstruction() !is SpecialConstruction) return  // don't want to be stuck on these forever

            val buildableNotWonders = getBuildableBuildings().filterNot { it.isWonder || it.isNationalWonder }
            val buildableWonders = getBuildableBuildings().filter { it.isWonder || it.isNationalWonder }

            val civUnits = cityInfo.civInfo.getCivUnits()
            val militaryUnits = civUnits.filter { !it.type.isCivilian()}.size
            val workers = civUnits.filter { it.name == Constants.worker }.size.toFloat()
            val cities = cityInfo.civInfo.cities.size
            val canBuildWorkboat = cityInfo.cityConstructions.getConstructableUnits().map { it.name }.contains("Work Boats")
                    && !cityInfo.getTiles().any { it.civilianUnit?.name == "Work Boats" }
            val needWorkboat = canBuildWorkboat
                    && cityInfo.getTiles().any { it.isWater && it.hasViewableResource(cityInfo.civInfo) && it.improvement == null }

            val isAtWar = cityInfo.civInfo.isAtWar()
            val preferredVictoryType = cityInfo.civInfo.victoryType()

            data class ConstructionChoice(val choice:String, var choiceModifier:Float){
                val remainingWork:Int = getRemainingWork(choice)
            }

            val relativeCostEffectiveness = ArrayList<ConstructionChoice>()

            //Food buildings : Granary and lighthouse and hospital
            val foodBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Food) }
                    .minBy{ it.cost }
            if (foodBuilding!=null) {
                val choice = ConstructionChoice(foodBuilding.name,1f)
                if (cityInfo.population.population < 5) choice.choiceModifier=1.3f
                relativeCostEffectiveness.add(choice)
            }

            //Production buildings : Workshop, factory
            val productionBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Production) }
                    .minBy{it.cost}
            if (productionBuilding!=null) {
                relativeCostEffectiveness.add(ConstructionChoice(productionBuilding.name, 1.5f))
            }

            //Gold buildings : Market, bank
            val goldBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Gold) }
                    .minBy{it.cost}
            if (goldBuilding!=null) {
                val choice = ConstructionChoice(goldBuilding.name,1.2f)
                if (cityInfo.civInfo.statsForNextTurn.gold<0) {
                    choice.choiceModifier=3f
                }
                relativeCostEffectiveness.add(choice)
            }

            //Science buildings
            val scienceBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Science) }
                    .minBy{it.cost}
            if (scienceBuilding!=null) {
                var modifier = 1.1f
                if(preferredVictoryType==VictoryType.Scientific)
                    modifier*=1.4f
                val choice = ConstructionChoice(scienceBuilding.name,modifier)
                relativeCostEffectiveness.add(choice)
            }

            //Happiness
            val happinessBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Happiness) }
                    .minBy{it.cost}
            if (happinessBuilding!=null) {
                val choice = ConstructionChoice(happinessBuilding.name,1f)
                val civHappiness = cityInfo.civInfo.getHappiness()
                if (civHappiness > 5) choice.choiceModifier = 1/2f // less desperate
                if (civHappiness < 0) choice.choiceModifier = 3f // more desperate
                relativeCostEffectiveness.add(choice)
            }

            //War buildings
            val wartimeBuilding = buildableNotWonders.filter { it.xpForNewUnits>0 || it.cityStrength>0 }
                    .minBy { it.cost }
            if (wartimeBuilding!=null && (preferredVictoryType!=VictoryType.Cultural || isAtWar)) {
                var modifier = 0.5f
                if(isAtWar) modifier = 1f
                if(preferredVictoryType==VictoryType.Domination)
                    modifier *= 1.3f
                relativeCostEffectiveness.add(ConstructionChoice(wartimeBuilding.name,modifier))
            }

            //Wonders
            if (buildableWonders.isNotEmpty()) {
                fun getWonderPriority(wonder: Building): Float {
                    if(preferredVictoryType==VictoryType.Cultural
                            && wonder.name in listOf("Sistine Chapel","Eiffel Tower","Cristo Redentor","Neuschwanstein","Sydney Opera House"))
                        return 3f
                    if(wonder.isStatRelated(Stat.Science)){
                        if(preferredVictoryType==VictoryType.Scientific) return 1.5f
                        else return 1.3f
                    }
                    if(wonder.isStatRelated(Stat.Happiness)) return 1.2f
                    if(wonder.isStatRelated(Stat.Production)) return 1.1f
                    return 1f
                }
                val wondersByPriority = buildableWonders
                        .sortedByDescending { getWonderPriority(it) }
                val wonder = wondersByPriority.first()
                val citiesBuildingWonders = cityInfo.civInfo.cities
                        .count { it.cityConstructions.isBuildingWonder() }

                relativeCostEffectiveness.add(ConstructionChoice(wonder.name,
                        3.5f * getWonderPriority(wonder) / (citiesBuildingWonders + 1)))
            }

            // culture buildings
            val cultureBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Culture) }.minBy { it.cost }
            if(cultureBuilding!=null){
                var modifier = 0.8f
                if(preferredVictoryType==VictoryType.Cultural) modifier =1.6f
                relativeCostEffectiveness.add(ConstructionChoice(cultureBuilding.name, modifier))
            }

            //other buildings
            val other = buildableNotWonders.minBy{it.cost}
            if (other!=null) {
                relativeCostEffectiveness.add(ConstructionChoice(other.name,0.8f))
            }

            //worker
            val citiesCountedTowardsWorkers = min(5, cities) // above 5 cities, extra cities won't make us want more workers - see #
            if (workers < citiesCountedTowardsWorkers * 0.6f) {
                relativeCostEffectiveness.add(ConstructionChoice(Constants.worker,citiesCountedTowardsWorkers/(workers+0.1f)))
            }

            //Work boat
            if (needWorkboat) {
                relativeCostEffectiveness.add(ConstructionChoice("Work Boats",0.6f))
            }

            //Army
            if((!isAtWar && cityInfo.civInfo.statsForNextTurn.gold>0 && militaryUnits<cities*2)
                    || (isAtWar && cityInfo.civInfo.gold > -50)) {
                val militaryUnit = chooseMilitaryUnit(cityInfo)
                val unitsToCitiesRatio = cities.toFloat() / (militaryUnits + 1)
                // most buildings and civ units contribute the the civ's growth, military units are anti-growth
                val militaryChoice = ConstructionChoice(militaryUnit, unitsToCitiesRatio / 2)
                if (isAtWar) militaryChoice.choiceModifier = unitsToCitiesRatio * 2
                else if (preferredVictoryType == VictoryType.Domination) militaryChoice.choiceModifier = unitsToCitiesRatio * 1.5f
                relativeCostEffectiveness.add(militaryChoice)
            }

            val production = cityInfo.cityStats.currentCityStats.production

            val theChosenOne:String
            if(relativeCostEffectiveness.isEmpty()){ // choose one of the special constructions instead
                // add science!
                if(SpecialConstruction.science.isBuildable(cityConstructions))
                    theChosenOne="Science"
                else if(SpecialConstruction.gold.isBuildable(cityConstructions))
                    theChosenOne="Gold"
                else theChosenOne = "Nothing"
            }
            else if(relativeCostEffectiveness.any { it.remainingWork < production*30 }) {
                relativeCostEffectiveness.removeAll { it.remainingWork >= production * 30 }
                theChosenOne = relativeCostEffectiveness.minBy { it.remainingWork/it.choiceModifier }!!.choice
            }
            // it's possible that this is a new city and EVERYTHING is way expensive - ignore modifiers, go for the cheapest.
            // Nobody can plan 30 turns ahead, I don't care how cost-efficient you are.
            else theChosenOne = relativeCostEffectiveness.minBy { it.remainingWork }!!.choice

            currentConstruction = theChosenOne
            cityInfo.civInfo.addNotification("Work has started on [$currentConstruction]", Color.BROWN, CityAction(cityInfo.location))
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

