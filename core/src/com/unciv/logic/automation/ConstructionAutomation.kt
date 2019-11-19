package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.SpecialConstruction
import com.unciv.logic.civilization.CityAction
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.VictoryType
import com.unciv.models.stats.Stat
import kotlin.math.min
import kotlin.math.sqrt

class ConstructionAutomation(val cityConstructions: CityConstructions){

    val cityInfo = cityConstructions.cityInfo
    val civInfo = cityInfo.civInfo

    val buildableNotWonders = cityConstructions.getBuildableBuildings().filterNot { it.isWonder || it.isNationalWonder }
    val buildableWonders = cityConstructions.getBuildableBuildings().filter { it.isWonder || it.isNationalWonder }

    val civUnits = civInfo.getCivUnits()
    val militaryUnits = civUnits.filter { !it.type.isCivilian()}.size
    val workers = civUnits.filter { it.name == Constants.worker }.size.toFloat()
    val cities = civInfo.cities.size
    val canBuildWorkboat = cityInfo.cityConstructions.getConstructableUnits().map { it.name }.contains("Work Boats")
            && !cityInfo.getTiles().any { it.civilianUnit?.name == "Work Boats" }
    val needWorkboat = canBuildWorkboat
            && cityInfo.getTiles().any { it.isWater && it.hasViewableResource(civInfo) && it.improvement == null }

    val isAtWar = civInfo.isAtWar()
    val preferredVictoryType = civInfo.victoryType()

    val averageProduction = civInfo.cities.map { it.cityStats.currentCityStats.production }.average()
    val cityIsOverAverageProduction = cityInfo.cityStats.currentCityStats.production >= averageProduction

    val relativeCostEffectiveness = ArrayList<ConstructionChoice>()

    data class ConstructionChoice(val choice:String, var choiceModifier:Float,val remainingWork:Int)

    fun addChoice(choices:ArrayList<ConstructionChoice>, choice:String, choiceModifier: Float){
        choices.add(ConstructionChoice(choice,choiceModifier,cityConstructions.getRemainingWork(choice)))
    }

    fun chooseNextConstruction() {
        if (!UnCivGame.Current.settings.autoAssignCityProduction
                && civInfo.playerType== PlayerType.Human && !cityInfo.isPuppet)
            return
        if (cityConstructions.getCurrentConstruction() !is SpecialConstruction) return  // don't want to be stuck on these forever

        addFoodBuildingChoice()
        addProductionBuildingChoice()
        addGoldBuildingChoice()
        addScienceBuildingChoice()
        addHappinessBuildingChoice()
        addDefenceBuildingChoice()
        addUnitTrainingBuildingChoice()
        addCultureBuildingChoice()

        if(!cityInfo.isPuppet) {
            addWondersChoice()
            addWorkerChoice()
            addWorkBoatChoice()
            addMilitaryUnitChoice()
        }

        val production = cityInfo.cityStats.currentCityStats.production

        val theChosenOne: String
        if (relativeCostEffectiveness.isEmpty()) { // choose one of the special constructions instead
            // add science!
            if (SpecialConstruction.science.isBuildable(cityConstructions))
                theChosenOne = "Science"
            else if (SpecialConstruction.gold.isBuildable(cityConstructions))
                theChosenOne = "Gold"
            else theChosenOne = "Nothing"
        } else if (relativeCostEffectiveness.any { it.remainingWork < production * 30 }) {
            relativeCostEffectiveness.removeAll { it.remainingWork >= production * 30 }
            theChosenOne = relativeCostEffectiveness.minBy { it.remainingWork / it.choiceModifier }!!.choice
        }
        // it's possible that this is a new city and EVERYTHING is way expensive - ignore modifiers, go for the cheapest.
        // Nobody can plan 30 turns ahead, I don't care how cost-efficient you are.
        else theChosenOne = relativeCostEffectiveness.minBy { it.remainingWork }!!.choice

        civInfo.addNotification("Work has started on [$theChosenOne]", Color.BROWN, CityAction(cityInfo.location))
        cityConstructions.currentConstruction = theChosenOne
    }

    private fun addMilitaryUnitChoice() {
        if(!isAtWar && !cityIsOverAverageProduction) return // don't make any military units here. Infrastructure first!
        if ((!isAtWar && civInfo.statsForNextTurn.gold > 0 && militaryUnits < cities * 2)
                || (isAtWar && civInfo.gold > -50)) {
            val militaryUnit = Automation().chooseMilitaryUnit(cityInfo)
            val unitsToCitiesRatio = cities.toFloat() / (militaryUnits + 1)
            // most buildings and civ units contribute the the civ's growth, military units are anti-growth
            var modifier = sqrt(unitsToCitiesRatio) / 2
            if (preferredVictoryType == VictoryType.Domination) modifier *= 3
            else if (isAtWar) modifier *= unitsToCitiesRatio * 2
            if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this

            if (cityInfo.getCenterTile().civilianUnit?.name == Constants.settler
                    && cityInfo.getCenterTile().getTilesInDistance(5).none { it.militaryUnit?.civInfo == civInfo })
                modifier = 5f // there's a settler just sitting here, doing nothing - BAD

            addChoice(relativeCostEffectiveness, militaryUnit, modifier)
        }
    }

    private fun addWorkBoatChoice() {
        if (needWorkboat) {
            addChoice(relativeCostEffectiveness, "Work Boats", 0.6f)
        }
    }

    private fun addWorkerChoice() {
        if(civInfo.getIdleUnits().any { it.name==Constants.worker && it.action== Constants.unitActionAutomation})
            return // If we have automated workers who have no work to do then it's silly to construct new workers.
        val citiesCountedTowardsWorkers = min(5, cities) // above 5 cities, extra cities won't make us want more workers
        if (workers < citiesCountedTowardsWorkers * 0.6f && civUnits.none { it.name==Constants.worker && it.isIdle() }) {
            var modifier = citiesCountedTowardsWorkers / (workers + 0.1f)
            if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this
            addChoice(relativeCostEffectiveness, Constants.worker, modifier)
        }
    }

    private fun addCultureBuildingChoice() {
        val cultureBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Culture) }.minBy { it.cost }
        if (cultureBuilding != null) {
            var modifier = 0.5f
            if(cityInfo.cityStats.currentCityStats.culture==0f) // It won't grow if we don't help it
                modifier=0.8f
            if (preferredVictoryType == VictoryType.Cultural) modifier = 1.6f
            addChoice(relativeCostEffectiveness, cultureBuilding.name, modifier)
        }
    }

    private fun addWondersChoice() {
        if (buildableWonders.isNotEmpty()) {
            fun getWonderPriority(wonder: Building): Float {
                if (preferredVictoryType == VictoryType.Cultural
                        && wonder.name in listOf("Sistine Chapel", "Eiffel Tower", "Cristo Redentor", "Neuschwanstein", "Sydney Opera House"))
                    return 3f
                if (wonder.isStatRelated(Stat.Science)) {
                    if (preferredVictoryType == VictoryType.Scientific) return 1.5f
                    else return 1.3f
                }
                if (wonder.name == "Manhattan Project") {
                    if (preferredVictoryType == VictoryType.Domination) return 2f
                    else return 1.3f
                }
                if (wonder.isStatRelated(Stat.Happiness)) return 1.2f
                if (wonder.isStatRelated(Stat.Production)) return 1.1f
                return 1f
            }

            val wondersByPriority = buildableWonders
                    .sortedByDescending { getWonderPriority(it) }
            val wonder = wondersByPriority.first()
            val citiesBuildingWonders = civInfo.cities
                    .count { it.cityConstructions.isBuildingWonder() }

            var modifier = 2f * getWonderPriority(wonder) / (citiesBuildingWonders + 1)
            if (!cityIsOverAverageProduction) modifier /= 5  // higher production cities will deal with this
            addChoice(relativeCostEffectiveness, wonder.name, modifier)
        }
    }

    private fun addUnitTrainingBuildingChoice() {
        val unitTrainingBuilding = buildableNotWonders.filter { it.xpForNewUnits > 0 }
                .minBy { it.cost }
        if (unitTrainingBuilding != null && (preferredVictoryType != VictoryType.Cultural || isAtWar)) {
            var modifier = if (cityIsOverAverageProduction) 0.5f else 0.1f // You shouldn't be cranking out units anytime soon
            if (isAtWar) modifier *= 2
            if (preferredVictoryType == VictoryType.Domination)
                modifier *= 1.3f
            addChoice(relativeCostEffectiveness, unitTrainingBuilding.name, modifier)
        }
    }

    private fun addDefenceBuildingChoice() {
        val defensiveBuilding = buildableNotWonders.filter { it.cityStrength > 0 }
                .minBy { it.cost }
        if (defensiveBuilding != null && (isAtWar || preferredVictoryType != VictoryType.Cultural)) {
            var modifier = 0.2f
            if (isAtWar) modifier = 0.5f

            // If this city is the closest city to another civ, that makes it a likely candidate for attack
            if (civInfo.getKnownCivs().filter { it.cities.isNotEmpty() }
                            .any { NextTurnAutomation().getClosestCities(civInfo, it).city1 == cityInfo })
                modifier *= 1.5f

            addChoice(relativeCostEffectiveness, defensiveBuilding.name, modifier)
        }
    }

    private fun addHappinessBuildingChoice() {
        val happinessBuilding = buildableNotWonders
                .filter { it.isStatRelated(Stat.Happiness)
                        || it.uniques.contains("Remove extra unhappiness from annexed cities") }
                .minBy { it.cost }
        if (happinessBuilding != null) {
            var modifier = 1f
            val civHappiness = civInfo.getHappiness()
            if (civHappiness > 5) modifier = 1 / 2f // less desperate
            if (civHappiness < 0) modifier = 3f // more desperate
            addChoice(relativeCostEffectiveness, happinessBuilding.name, modifier)
        }
    }

    private fun addScienceBuildingChoice() {
        val scienceBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Science) || it.name=="Library" } // only stat related in unique
                .minBy { it.cost }
        if (scienceBuilding != null) {
            var modifier = 1.1f
            if (preferredVictoryType == VictoryType.Scientific)
                modifier *= 1.4f
            addChoice(relativeCostEffectiveness, scienceBuilding.name, modifier)
        }
    }

    private fun addGoldBuildingChoice() {
        val goldBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Gold) }
                .minBy { it.cost }
        if (goldBuilding != null) {
            val modifier = if (civInfo.statsForNextTurn.gold < 0) 3f else 1.2f
            addChoice(relativeCostEffectiveness, goldBuilding.name, modifier)
        }
    }

    private fun addProductionBuildingChoice() {
        val hasWaterResource = cityInfo.tilesInRange
                .any { it.isWater && it.resource!=null && it.position in cityInfo.tiles }
        val productionBuilding = buildableNotWonders
                .filter { it.isStatRelated(Stat.Production)
                        || (hasWaterResource && (it.uniques.contains("+1 production and gold from all sea resources worked by the city")
                                                || it.uniques.contains("+1 production from all sea resources worked by the city")) )
                }
                .minBy { it.cost }
        if (productionBuilding != null) {
            addChoice(relativeCostEffectiveness, productionBuilding.name, 1.5f)
        }
    }

    private fun addFoodBuildingChoice() {
        val foodBuilding = buildableNotWonders.filter { it.isStatRelated(Stat.Food)
                || it.getBaseBuilding().name == "Aqueduct" || it.getBaseBuilding().name == "Medical Lab"}  // only stat related in unique
                .minBy { it.cost }
        if (foodBuilding != null) {
            var modifier = 1f
            if (cityInfo.population.population < 5) modifier = 1.3f
            addChoice(relativeCostEffectiveness, foodBuilding.name, modifier)
        }
    }

}