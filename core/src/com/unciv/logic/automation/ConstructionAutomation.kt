package com.unciv.logic.automation

import com.unciv.UncivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.logic.civilization.CityAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.BFS
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import kotlin.math.max
import kotlin.math.sqrt

class ConstructionAutomation(val cityConstructions: CityConstructions){

    private val cityInfo = cityConstructions.cityInfo
    private val civInfo = cityInfo.civInfo

    private val buildableBuildings = cityConstructions.getBuildableBuildings().toList()
    private val buildableNotWonders = buildableBuildings
            .filterNot { it.isAnyWonder() }
    private val buildableWonders = buildableBuildings
            .filter { it.isAnyWonder() }
    
    private val buildableUnits = cityConstructions.getConstructableUnits()

    private val civUnits = civInfo.getCivUnits()
    private val militaryUnits = civUnits.count { it.baseUnit.isMilitary() }
    private val workers = civUnits.count { it.hasUniqueToBuildImprovements && it.isCivilian() }.toFloat()
    private val cities = civInfo.cities.size
    private val allTechsAreResearched = civInfo.tech.getNumberOfTechsResearched() >= civInfo.gameInfo.ruleSet.technologies.size

    private val isAtWar = civInfo.isAtWar()
    private val buildingsForVictory = civInfo.gameInfo.getEnabledVictories().values
            .mapNotNull { civInfo.victoryManager.getNextMilestone(it.name) }
            .filter { it.type == MilestoneType.BuiltBuilding || it.type == MilestoneType.BuildingBuiltGlobally }
            .map { it.params[0] }
    
    private val spaceshipParts = civInfo.gameInfo.spaceResources

    
    private val averageProduction = civInfo.cities.map { it.cityStats.currentCityStats.production }.average()
    private val cityIsOverAverageProduction = cityInfo.cityStats.currentCityStats.production >= averageProduction

    private val relativeCostEffectiveness = ArrayList<ConstructionChoice>()

    private val faithConstruction = arrayListOf<BaseUnit>()

    private data class ConstructionChoice(val choice: String, var choiceModifier: Float, val remainingWork: Int)

    private fun addChoice(choices: ArrayList<ConstructionChoice>, choice: String, choiceModifier: Float) {
        choices.add(ConstructionChoice(choice, choiceModifier, cityConstructions.getRemainingWork(choice)))
    }


    fun chooseNextConstruction() {
        if (!UncivGame.Current.settings.autoAssignCityProduction
                && civInfo.playerType == PlayerType.Human && !cityInfo.isPuppet)
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
        addOtherBuildingChoice()

        if (!cityInfo.isPuppet) {
            addSpaceshipPartChoice()
            addWondersChoice()
            addWorkerChoice()
            addWorkBoatChoice()
            addMilitaryUnitChoice()
            addReligiousUnit()
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

        val chosenItem = faithConstruction.firstOrNull {
            it.getStatBuyCost(cityInfo, stat = Stat.Faith)!! <= civInfo.religionManager.storedFaith
        } ?: return

        cityConstructions.purchaseConstruction(chosenItem.name, -1, false, stat=Stat.Faith)
    }

    private fun addMilitaryUnitChoice() {
        if (!isAtWar && !cityIsOverAverageProduction) return // don't make any military units here. Infrastructure first!
        if (!isAtWar && civInfo.statsForNextTurn.gold > 0 && militaryUnits < max(5, cities * 2)
                || isAtWar && civInfo.gold > -50
        ) {
            val militaryUnit = Automation.chooseMilitaryUnit(cityInfo) ?: return
            val unitsToCitiesRatio = cities.toFloat() / (militaryUnits + 1)
            // most buildings and civ units contribute the the civ's growth, military units are anti-growth
            var modifier = sqrt(unitsToCitiesRatio) / 2
            if (civInfo.wantsToFocusOn(Victory.Focus.Military)) modifier *= 3
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
        val buildableWorkboatUnits = buildableUnits
            .filter {
                it.hasUnique(UniqueType.CreateWaterImprovements)
                && Automation.allowAutomatedConstruction(civInfo, cityInfo, it)
            }
        val alreadyHasWorkBoat = buildableWorkboatUnits.any()
            && !cityInfo.getTiles().any { 
                it.civilianUnit?.hasUnique(UniqueType.CreateWaterImprovements) == true 
            }
        if (!alreadyHasWorkBoat) return


        val bfs = BFS(cityInfo.getCenterTile()) {
            (it.isWater || it.isCityCenter()) && it.isFriendlyTerritory(civInfo)
        }
        for (i in 1..10) bfs.nextStep()
        if (!bfs.getReachedTiles()
            .any { tile -> 
                tile.hasViewableResource(civInfo) && tile.improvement == null && tile.getOwner() == civInfo
                && tile.tileResource.getImprovements().any {
                    tile.canBuildImprovement(tile.ruleset.tileImprovements[it]!!, civInfo) 
                }
            }
        ) return

        addChoice(
            relativeCostEffectiveness, buildableWorkboatUnits.minByOrNull { it.cost }!!.name,
            0.6f
        )
    }

    private fun addWorkerChoice() {
        val workerEquivalents = buildableUnits
            .filter {
                it.hasUnique(UniqueType.BuildImprovements)
                && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) 
            }
        if (workerEquivalents.none()) return // for mods with no worker units

        if (workers < cities) {
            var modifier = cities / (workers + 0.1f) // The worse our worker to city ratio is, the more desperate we are
            if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this
            addChoice(relativeCostEffectiveness, workerEquivalents.minByOrNull { it.cost }!!.name, modifier)
        }
    }

    private fun addCultureBuildingChoice() {
        val cultureBuilding = buildableNotWonders
                .filter { it.isStatRelated(Stat.Culture)
                        && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }.minByOrNull { it.cost }
        if (cultureBuilding != null) {
            var modifier = 0.5f
            if (cityInfo.cityStats.currentCityStats.culture == 0f) // It won't grow if we don't help it
                modifier = 0.8f
            if (civInfo.wantsToFocusOn(Victory.Focus.Culture)) modifier = 1.6f
            addChoice(relativeCostEffectiveness, cultureBuilding.name, modifier)
        }
    }

    private fun addSpaceshipPartChoice() {
        val spaceshipPart = (buildableNotWonders + buildableUnits).firstOrNull { it.name in spaceshipParts }
        if (spaceshipPart != null) {
            val modifier = 2f
            addChoice(relativeCostEffectiveness, spaceshipPart.name, modifier)
        }
    }

    private fun addOtherBuildingChoice() {
        val otherBuilding = buildableNotWonders
            .filter { Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }.minByOrNull { it.cost }
        if (otherBuilding != null) {
            val modifier = 0.6f
            addChoice(relativeCostEffectiveness, otherBuilding.name, modifier)
        }
    }

    private fun getWonderPriority(wonder: Building): Float {
        // Only start building if we are the city that would complete it the soonest
        if (wonder.hasUnique(UniqueType.TriggersCulturalVictory) 
            && cityInfo == civInfo.cities.minByOrNull {
                it.cityConstructions.turnsToConstruction(wonder.name)
            }!!
        ) {
            return 10f
        }
        if (wonder.name in buildingsForVictory)
            return 5f
        if (civInfo.wantsToFocusOn(Victory.Focus.Culture)
            // TODO: Moddability
                && wonder.name in listOf("Sistine Chapel", "Eiffel Tower", "Cristo Redentor", "Neuschwanstein", "Sydney Opera House"))
            return 3f
        if (wonder.isStatRelated(Stat.Science)) {
            if (allTechsAreResearched) return .5f
            return if (civInfo.wantsToFocusOn(Victory.Focus.Science)) 1.5f
            else 1.3f
        }
        if (wonder.name == "Manhattan Project") {
            return if (civInfo.wantsToFocusOn(Victory.Focus.Military)) 2f
            else 1.3f
        }
        if (wonder.isStatRelated(Stat.Happiness)) return 1.2f
        if (wonder.isStatRelated(Stat.Production)) return 1.1f
        return 1f
    }

    private fun addWondersChoice() {
        if (!buildableWonders.any()) return

        val highestPriorityWonder = buildableWonders
            .filter { Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }
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
                        && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }.minByOrNull { it.cost }
        if (unitTrainingBuilding != null && (!civInfo.wantsToFocusOn(Victory.Focus.Culture) || isAtWar)) {
            var modifier = if (cityIsOverAverageProduction) 0.5f else 0.1f // You shouldn't be cranking out units anytime soon
            if (isAtWar) modifier *= 2
            if (civInfo.wantsToFocusOn(Victory.Focus.Military))
                modifier *= 1.3f
            addChoice(relativeCostEffectiveness, unitTrainingBuilding.name, modifier)
        }
    }

    private fun addDefenceBuildingChoice() {
        val defensiveBuilding = buildableNotWonders.asSequence()
                .filter { it.cityStrength > 0
                        && Automation.allowAutomatedConstruction(civInfo, cityInfo, it)}.minByOrNull { it.cost }
        if (defensiveBuilding != null && (isAtWar || !civInfo.wantsToFocusOn(Victory.Focus.Culture))) {
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
                        && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }
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
            && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }
            .minByOrNull { it.cost }
        if (scienceBuilding != null) {
            var modifier = 1.1f
            if (civInfo.wantsToFocusOn(Victory.Focus.Science))
                modifier *= 1.4f
            addChoice(relativeCostEffectiveness, scienceBuilding.name, modifier)
        }
    }

    private fun addGoldBuildingChoice() {
        val goldBuilding = buildableNotWonders.asSequence().filter { it.isStatRelated(Stat.Gold)
            && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }
            .minByOrNull { it.cost }
        if (goldBuilding != null) {
            val modifier = if (civInfo.statsForNextTurn.gold < 0) 3f else 1.2f
            addChoice(relativeCostEffectiveness, goldBuilding.name, modifier)
        }
    }

    private fun addProductionBuildingChoice() {
        val productionBuilding = buildableNotWonders.asSequence()
            .filter { it.isStatRelated(Stat.Production) && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) }
            .minByOrNull { it.cost }
        if (productionBuilding != null) {
            addChoice(relativeCostEffectiveness, productionBuilding.name, 1.5f)
        }
    }

    private fun addFoodBuildingChoice() {
        val conditionalState = StateForConditionals(civInfo, cityInfo)
        val foodBuilding = buildableNotWonders.asSequence()
            .filter { 
                (it.isStatRelated(Stat.Food) 
                    || it.hasUnique(UniqueType.CarryOverFood, conditionalState)
                ) && Automation.allowAutomatedConstruction(civInfo, cityInfo, it) 
            }.minByOrNull { it.cost }
        if (foodBuilding != null) {
            var modifier = 1f
            if (cityInfo.population.population < 5) modifier = 1.3f
            addChoice(relativeCostEffectiveness, foodBuilding.name, modifier)
        }
    }

    private fun addReligiousUnit() {
        // these 4 if conditions are used to determine if an AI should buy units to spread religion, or spend faith to buy things like new military units or new buildings.
        // currently this AI can only buy inquisitors and missionaries with faith
        // this system will have to be reengineered to support buying other stuff with faith
        if (civInfo.wantsToFocusOn(Victory.Focus.Military)) return
        if (civInfo.religionManager.religion?.name == null) return
        if (cityInfo.religion.getMajorityReligion()?.name != civInfo.religionManager.religion?.name)
            return // you don't want to build units of opposing religions.


        var modifier = 0f

        // The performance of the regular getMatchingUniques is better, since it only tries to find one unique,
        //  while the canBePurchasedWithStat tries (at time of writing) *6* different uniques.
        val missionary = buildableUnits
            .firstOrNull { it -> it.getMatchingUniques("Can [] [] times").any { it.params[0] == "Spread Religion"}
                    && it.canBePurchasedWithStat(cityInfo, Stat.Faith) }


        val inquisitor = buildableUnits
            .firstOrNull { it.hasUnique("Prevents spreading of religion to the city it is next to")
                    && it.canBePurchasedWithStat(cityInfo, Stat.Faith) }


        if (civInfo.wantsToFocusOn(Victory.Focus.Culture)) modifier += 1
        if (isAtWar) modifier -= 0.5f

        val citiesNotFollowingOurReligion = civInfo.cities.asSequence()
            .filterNot { it.religion.getMajorityReligion()?.name == civInfo.religionManager.religion!!.name }

        val buildInquisitor = citiesNotFollowingOurReligion
            .filter { it.religion.getMajorityReligion()?.name == civInfo.religionManager.religion?.name }
            .toList().size.toFloat() / 10 + modifier

        val possibleSpreadReligionTargets = civInfo.gameInfo.getCities()
            .filter { it.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile()) < 30 }

        val buildMissionary = possibleSpreadReligionTargets.toList().size.toFloat() / 15 + modifier

        if (buildMissionary > buildInquisitor && missionary != null) faithConstruction.add(missionary)
        else if(inquisitor != null) faithConstruction.add(inquisitor)
    }

}