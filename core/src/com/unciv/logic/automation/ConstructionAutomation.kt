package com.unciv.logic.automation

import com.unciv.UncivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.logic.civilization.CityAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.BFS
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ConstructionAutomation(val cityConstructions: CityConstructions){

    val cityInfo = cityConstructions.cityInfo
    val civInfo = cityInfo.civInfo

    val buildableNotWonders = cityConstructions.getBuildableBuildings()
            .filterNot { it.isAnyWonder() }
    private val buildableWonders = cityConstructions.getBuildableBuildings()
            .filter { it.isAnyWonder() }

    val civUnits = civInfo.getCivUnits()
    val militaryUnits = civUnits.count { it.baseUnit.isMilitary() }
    val workers = civUnits.count { it.hasUniqueToBuildImprovements && it.isCivilian() }.toFloat()
    val cities = civInfo.cities.size
    val allTechsAreResearched = civInfo.tech.getNumberOfTechsResearched() >= civInfo.gameInfo.ruleSet.technologies.size

    val isAtWar = civInfo.isAtWar()
    val preferredVictoryType = civInfo.victoryType()

    private val averageProduction = civInfo.cities.map { it.cityStats.currentCityStats.production }.average()
    val cityIsOverAverageProduction = cityInfo.cityStats.currentCityStats.production >= averageProduction

    val relativeCostEffectiveness = ArrayList<ConstructionChoice>()

    private val faithConstruction = arrayListOf<BaseUnit>()

    data class ConstructionChoice(val choice:String, var choiceModifier:Float,val remainingWork:Int)

    fun addChoice(choices:ArrayList<ConstructionChoice>, choice:String, choiceModifier: Float){
        choices.add(ConstructionChoice(choice,choiceModifier,cityConstructions.getRemainingWork(choice)))
    }


    fun chooseNextConstruction() {
        if (!UncivGame.Current.settings.autoAssignCityProduction
            && civInfo.playerType == PlayerType.Human && !cityInfo.isPuppet
        )
            return
        if (cityConstructions.getCurrentConstruction() !is PerpetualConstruction) return  // don't want to be stuck on these forever

        addFoodBuildingChoice()
        addProductionBuildingChoice()
        addGoldBuildingChoice()
        addScienceBuildingChoice()
        addHappinessBuildingChoice()
        addDefenceBuildingChoice()
        addUnitTrainingBuildingChoice()
        addCultureBuildingChoice()
        addSpaceshipPartChoice()
        addOtherBuildingChoice()
        addReligousUnit()

        if (!cityInfo.isPuppet) {
            addWondersChoice()
            addWorkerChoice()
            addWorkBoatChoice()
            addMilitaryUnitChoice()
        }

        val production = cityInfo.cityStats.currentCityStats.production

        val chosenConstruction: String =
            if (relativeCostEffectiveness.isEmpty()) { // choose one of the special constructions instead
                // add science!
                when {
                    PerpetualConstruction.science.isBuildable(cityConstructions) && !allTechsAreResearched -> PerpetualConstruction.science.name
                    PerpetualConstruction.gold.isBuildable(cityConstructions) -> PerpetualConstruction.gold.name
                    else -> PerpetualConstruction.idle.name
                }
            } else if (relativeCostEffectiveness.any { it.remainingWork < production * 30 }) {
                relativeCostEffectiveness.removeAll { it.remainingWork >= production * 30 }
                relativeCostEffectiveness.minByOrNull { it.remainingWork / it.choiceModifier }!!.choice
            }
            // it's possible that this is a new city and EVERYTHING is way expensive - ignore modifiers, go for the cheapest.
            // Nobody can plan 30 turns ahead, I don't care how cost-efficient you are.
            else relativeCostEffectiveness.minByOrNull { it.remainingWork }!!.choice

        civInfo.addNotification(
            "Work has started on [$chosenConstruction]",
            CityAction(cityInfo.location),
            NotificationIcon.Construction
        )
        cityConstructions.currentConstructionFromQueue = chosenConstruction

        if (civInfo.isPlayerCivilization()) return // don't want the ai to control what a player uses faith for

        val chosenItem = faithConstruction.asSequence()
            .filterNotNull()
            .filter { it.getStatBuyCost(cityInfo, stat = Stat.Faith)!! <= civInfo.religionManager.storedFaith }
            .firstOrNull() ?: return


        cityConstructions.purchaseConstruction(chosenItem.name, -1, false, stat=Stat.Faith)

    }

    private fun addMilitaryUnitChoice() {
        if (!isAtWar && !cityIsOverAverageProduction) return // don't make any military units here. Infrastructure first!
        if ((!isAtWar && civInfo.statsForNextTurn.gold > 0 && militaryUnits < max(5, cities * 2))
                || (isAtWar && civInfo.gold > -50)) {
            val militaryUnit = Automation.chooseMilitaryUnit(cityInfo) ?: return
            val unitsToCitiesRatio = cities.toFloat() / (militaryUnits + 1)
            // most buildings and civ units contribute the the civ's growth, military units are anti-growth
            var modifier = sqrt(unitsToCitiesRatio) / 2
            if (preferredVictoryType == VictoryType.Domination) modifier *= 3
            else if (isAtWar) modifier *= unitsToCitiesRatio * 2

            if (Automation.afraidOfBarbarians(civInfo)) modifier = 2f // military units are pro-growth if pressured by barbs
            if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this

            val civilianUnit = cityInfo.getCenterTile().civilianUnit
            if (civilianUnit != null && civilianUnit.hasUnique(UniqueType.FoundCity)
                    && cityInfo.getCenterTile().getTilesInDistance(5).none { it.militaryUnit?.civInfo == civInfo })
                modifier = 5f // there's a settler just sitting here, doing nothing - BAD

            addChoice(relativeCostEffectiveness, militaryUnit, modifier)
        }
    }

    private fun addWorkBoatChoice() {
        val buildableWorkboatUnits = cityInfo.cityConstructions.getConstructableUnits()
            .filter {
                it.hasUnique(UniqueType.CreateWaterImprovements)
                        && Automation.allowSpendingResource(civInfo, it)
            }
        val canBuildWorkboat = buildableWorkboatUnits.any()
                && !cityInfo.getTiles()
            .any { it.civilianUnit?.hasUnique(UniqueType.CreateWaterImprovements) == true }
        if (!canBuildWorkboat) return


        val bfs = BFS(cityInfo.getCenterTile()) {
            (it.isWater || it.isCityCenter()) && it.isFriendlyTerritory(civInfo)
        }
        for (i in 1..10) bfs.nextStep()
        if (!bfs.getReachedTiles()
                .any { it.hasViewableResource(civInfo) && it.improvement == null && it.getOwner() == civInfo }
        ) return

        addChoice(
            relativeCostEffectiveness, buildableWorkboatUnits.minByOrNull { it.cost }!!.name,
            0.6f
        )
    }

    private fun addWorkerChoice() {
        val workerEquivalents = civInfo.gameInfo.ruleSet.units.values
            .filter {
                it.hasUnique(UniqueType.BuildImprovements)
                && it.isBuildable(cityConstructions)
                && Automation.allowSpendingResource(civInfo, it) }
        if (workerEquivalents.isEmpty()) return // for mods with no worker units
        if (civInfo.getIdleUnits().any { it.isAutomated() && it.hasUniqueToBuildImprovements })
            return // If we have automated workers who have no work to do then it's silly to construct new workers.

        val citiesCountedTowardsWorkers = min(5, cities) // above 5 cities, extra cities won't make us want more workers
        if (workers < citiesCountedTowardsWorkers * 0.6f && civUnits.none { it.hasUniqueToBuildImprovements && it.isIdle() }) {
            var modifier = citiesCountedTowardsWorkers / (workers + 0.1f)
            if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this
            addChoice(relativeCostEffectiveness, workerEquivalents.minByOrNull { it.cost }!!.name, modifier)
        }
    }

    private fun addCultureBuildingChoice() {
        val cultureBuilding = buildableNotWonders
                .filter { it.isStatRelated(Stat.Culture)
                        && Automation.allowSpendingResource(civInfo, it) }.minByOrNull { it.cost }
        if (cultureBuilding != null) {
            var modifier = 0.5f
            if (cityInfo.cityStats.currentCityStats.culture == 0f) // It won't grow if we don't help it
                modifier = 0.8f
            if (preferredVictoryType == VictoryType.Cultural) modifier = 1.6f
            addChoice(relativeCostEffectiveness, cultureBuilding.name, modifier)
        }
    }

    private fun addSpaceshipPartChoice() {
        val spaceshipPart = buildableNotWonders.firstOrNull { it.hasUnique(UniqueType.SpaceshipPart) }
        if (spaceshipPart != null) {
            var modifier = 1.5f
            if (preferredVictoryType == VictoryType.Scientific) modifier = 2f
            addChoice(relativeCostEffectiveness, spaceshipPart.name, modifier)
        }
    }

    private fun addOtherBuildingChoice() {
        val otherBuilding = buildableNotWonders
            .filter { Automation.allowSpendingResource(civInfo, it) }.minByOrNull { it.cost }
        if (otherBuilding != null) {
            val modifier = 0.6f
            addChoice(relativeCostEffectiveness, otherBuilding.name, modifier)
        }
    }

    private fun getWonderPriority(wonder: Building): Float {
        if (wonder.uniques.contains("Enables construction of Spaceship parts"))
            return 2f
        if (preferredVictoryType == VictoryType.Cultural
                && wonder.name in listOf("Sistine Chapel", "Eiffel Tower", "Cristo Redentor", "Neuschwanstein", "Sydney Opera House"))
            return 3f
        // Only start building if we are the city that would complete it the soonest
        if (wonder.uniques.contains("Triggers a Cultural Victory upon completion") && cityInfo == civInfo.cities.minByOrNull {
                it.cityConstructions.turnsToConstruction(wonder.name) 
            }!!)
            return 10f
        if (wonder.isStatRelated(Stat.Science)) {
            if (allTechsAreResearched) return .5f
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

    private fun addWondersChoice() {
        if (!buildableWonders.any()) return

        val highestPriorityWonder = buildableWonders
            .filter { Automation.allowSpendingResource(civInfo, it) }
            .maxByOrNull { getWonderPriority(it) }!!
        val citiesBuildingWonders = civInfo.cities
                .count { it.cityConstructions.isBuildingWonder() }

        var modifier = 2f * getWonderPriority(highestPriorityWonder) / (citiesBuildingWonders + 1)
        if (!cityIsOverAverageProduction) modifier /= 5  // higher production cities will deal with this
        addChoice(relativeCostEffectiveness, highestPriorityWonder.name, modifier)
    }

    private fun addUnitTrainingBuildingChoice() {
        val unitTrainingBuilding = buildableNotWonders.asSequence()
                .filter { it.hasUnique(UniqueType.UnitStartingExperience)
                        && Automation.allowSpendingResource(civInfo, it)}.minByOrNull { it.cost }
        if (unitTrainingBuilding != null && (preferredVictoryType != VictoryType.Cultural || isAtWar)) {
            var modifier = if (cityIsOverAverageProduction) 0.5f else 0.1f // You shouldn't be cranking out units anytime soon
            if (isAtWar) modifier *= 2
            if (preferredVictoryType == VictoryType.Domination)
                modifier *= 1.3f
            addChoice(relativeCostEffectiveness, unitTrainingBuilding.name, modifier)
        }
    }

    private fun addDefenceBuildingChoice() {
        val defensiveBuilding = buildableNotWonders.asSequence()
                .filter { it.cityStrength > 0
                        && Automation.allowSpendingResource(civInfo, it)}.minByOrNull { it.cost }
        if (defensiveBuilding != null && (isAtWar || preferredVictoryType != VictoryType.Cultural)) {
            var modifier = 0.2f
            if (isAtWar) modifier = 0.5f

            // If this city is the closest city to another civ, that makes it a likely candidate for attack
            if (civInfo.getKnownCivs().filter { it.cities.isNotEmpty() }
                            .any { NextTurnAutomation.getClosestCities(civInfo, it).city1 == cityInfo })
                modifier *= 1.5f

            addChoice(relativeCostEffectiveness, defensiveBuilding.name, modifier)
        }
    }

    private fun addHappinessBuildingChoice() {
        val happinessBuilding = buildableNotWonders.asSequence()
                .filter { (it.isStatRelated(Stat.Happiness)
                        || it.uniques.contains("Remove extra unhappiness from annexed cities"))
                        && Automation.allowSpendingResource(civInfo, it)}
            .minByOrNull { it.cost }
        if (happinessBuilding != null) {
            var modifier = 1f
            val civHappiness = civInfo.getHappiness()
            if (civHappiness > 5) modifier = 1 / 2f // less desperate
            if (civHappiness < 0) modifier = 3f // more desperate
            addChoice(relativeCostEffectiveness, happinessBuilding.name, modifier)
        }
    }

    private fun addScienceBuildingChoice() {
        if (allTechsAreResearched) return
        val scienceBuilding = buildableNotWonders.asSequence()
            .filter { it.isStatRelated(Stat.Science)
            && Automation.allowSpendingResource(civInfo, it)}
            .minByOrNull { it.cost }
        if (scienceBuilding != null) {
            var modifier = 1.1f
            if (preferredVictoryType == VictoryType.Scientific)
                modifier *= 1.4f
            addChoice(relativeCostEffectiveness, scienceBuilding.name, modifier)
        }
    }

    private fun addGoldBuildingChoice() {
        val goldBuilding = buildableNotWonders.asSequence().filter { it.isStatRelated(Stat.Gold)
            && Automation.allowSpendingResource(civInfo, it)}
            .minByOrNull { it.cost }
        if (goldBuilding != null) {
            val modifier = if (civInfo.statsForNextTurn.gold < 0) 3f else 1.2f
            addChoice(relativeCostEffectiveness, goldBuilding.name, modifier)
        }
    }

    private fun addProductionBuildingChoice() {
        val productionBuilding = buildableNotWonders.asSequence()
            .filter { it.isStatRelated(Stat.Production) && Automation.allowSpendingResource(civInfo, it) }
            .minByOrNull { it.cost }
        if (productionBuilding != null) {
            addChoice(relativeCostEffectiveness, productionBuilding.name, 1.5f)
        }
    }

    private fun addFoodBuildingChoice() {
        val foodBuilding = buildableNotWonders.asSequence().filter { (it.isStatRelated(Stat.Food)
                || it.uniqueObjects.any { it.placeholderText=="[]% of food is carried over after population increases" })
                && Automation.allowSpendingResource(civInfo, it) }
            .minByOrNull { it.cost }
        if (foodBuilding != null) {
            var modifier = 1f
            if (cityInfo.population.population < 5) modifier = 1.3f
            addChoice(relativeCostEffectiveness, foodBuilding.name, modifier)
        }
    }

    private fun addReligousUnit(){

        var modifier = 0f

        val missionary = cityInfo.getRuleset().units.values.asSequence()
            .firstOrNull { it -> it.canBePurchasedWithStat(cityInfo, Stat.Faith)
                    && it.getMatchingUniques("Can [] [] times").any { it.params[0] == "Spread Religion"} }


        val inquisitor = cityInfo.getRuleset().units.values.asSequence()
            .firstOrNull { it.canBePurchasedWithStat(cityInfo, Stat.Faith)
                    && it.hasUnique("Prevents spreading of religion to the city it is next to") }



        // these 4 if conditions are used to determine if an AI should buy units to spread religion, or spend faith to buy things like new military units or new buildings.
        // currently this AI can only buy inquisitors and missionaries with faith
        // this system will have to be reengineered to support buying other stuff with faith
        if (preferredVictoryType == VictoryType.Domination) return
        if (civInfo.religionManager.religion?.name == null) return
        if (preferredVictoryType == VictoryType.Cultural) modifier += 1
        if (isAtWar) modifier -= 0.5f
        if (cityInfo.religion.getMajorityReligion()?.name != civInfo.religionManager.religion?.name)
            return // you don't want to build units of opposing religions.


        val citiesNotFollowingOurReligion = civInfo.cities.asSequence()
            .filterNot { it.religion.getMajorityReligion()?.name == civInfo.religionManager.religion!!.name }

        val buildInqusitor = citiesNotFollowingOurReligion
            .filter { it.religion.getMajorityReligion()?.name == civInfo.religionManager.religion?.name }
            .toList().size.toFloat() / 10 + modifier

        val possibleSpreadReligionTargets = civInfo.gameInfo.getCities()
            .filter { it.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile()) < 30 }

        val buildMissionary = possibleSpreadReligionTargets.toList().size.toFloat() / 15 + modifier

        if (buildMissionary > buildInqusitor && missionary != null) faithConstruction.add(missionary)
        else if(inquisitor != null) faithConstruction.add(inquisitor)


    }

}