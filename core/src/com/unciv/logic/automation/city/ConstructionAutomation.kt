package com.unciv.logic.automation.city

import com.unciv.GUI
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.WorkerAutomation
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.CityAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.BFS
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import kotlin.math.max
import kotlin.math.sqrt

class ConstructionAutomation(val cityConstructions: CityConstructions) {

    private val city = cityConstructions.city
    private val civInfo = city.civ

    private val personality = civInfo.getPersonality()

    private val constructionsToAvoid = personality.getMatchingUniques(UniqueType.WillNotBuild, StateForConditionals(city))
        .map{ it.params[0] }
    private fun shouldAvoidConstruction (construction: IConstruction): Boolean {
        for (toAvoid in constructionsToAvoid) {
            if (construction is Building && construction.matchesFilter(toAvoid))
                return true
            if (construction is BaseUnit && construction.matchesFilter(toAvoid))
                return true
        }
        return false
    }

    private val disabledAutoAssignConstructions: Set<String> =
        if (civInfo.isHuman()) GUI.getSettings().disabledAutoAssignConstructions
        else emptySet()

    private val buildableBuildings = hashMapOf<String, Boolean>()
    private val buildableUnits = hashMapOf<String, Boolean>()
    private val buildings = city.getRuleset().buildings.values.asSequence()
        .filterNot { it.name in disabledAutoAssignConstructions || shouldAvoidConstruction(it) }

    private val nonWonders = buildings.filterNot { it.isAnyWonder() }
        .filterNot { buildableBuildings[it.name] == false } // if we already know that this building can't be built here then don't even consider it
    private val statBuildings = nonWonders.filter { !it.isEmpty() && Automation.allowAutomatedConstruction(civInfo, city, it) }
    private val wonders = buildings.filter { it.isAnyWonder() }

    private val units = city.getRuleset().units.values.asSequence()
        .filterNot { buildableUnits[it.name] == false || // if we already know that this unit can't be built here then don't even consider it
            it.name in disabledAutoAssignConstructions || shouldAvoidConstruction(it) }

    private val civUnits = civInfo.units.getCivUnits()
    private val militaryUnits = civUnits.count { it.baseUnit.isMilitary() }
    private val workers = civUnits.count { it.cache.hasUniqueToBuildImprovements}.toFloat()
    private val cities = civInfo.cities.size
    private val allTechsAreResearched = civInfo.gameInfo.ruleset.technologies.values
        .all { civInfo.tech.isResearched(it.name) || !civInfo.tech.canBeResearched(it.name)}

    private val isAtWar = civInfo.isAtWar()
    private val buildingsForVictory = civInfo.gameInfo.getEnabledVictories().values
            .mapNotNull { civInfo.victoryManager.getNextMilestone(it) }
            .filter { it.type == MilestoneType.BuiltBuilding || it.type == MilestoneType.BuildingBuiltGlobally }
            .map { it.params[0] }

    private val spaceshipParts = civInfo.gameInfo.spaceResources


    private val averageProduction = civInfo.cities.map { it.cityStats.currentCityStats.production }.average()
    private val cityIsOverAverageProduction = city.cityStats.currentCityStats.production >= averageProduction
    private val averageBuildingCost = buildings.filterBuildable().sumOf { it.cost } / buildings.filterBuildable().count()

    private val relativeCostEffectiveness = ArrayList<ConstructionChoice>()
    private val cityState = StateForConditionals(city)
    private val cityStats = city.cityStats

    private data class ConstructionChoice(val choice: String, var choiceModifier: Float,
                                          val remainingWork: Int, val production: Int)

    private fun addChoice(choices: ArrayList<ConstructionChoice>, choice: String, choiceModifier: Float) {
        choices.add(ConstructionChoice(choice, choiceModifier,
            cityConstructions.getRemainingWork(choice), cityConstructions.productionForConstruction(choice)))
    }

    private fun Sequence<INonPerpetualConstruction>.filterBuildable(): Sequence<INonPerpetualConstruction> {
        return this.filter {
            val cache = if (it is Building) buildableBuildings else buildableUnits
            if (cache[it.name] == null) {
                cache[it.name] = it.isBuildable(cityConstructions)
            }
            cache[it.name]!!
        }
    }


    fun chooseNextConstruction() {
        if (cityConstructions.getCurrentConstruction() !is PerpetualConstruction) return  // don't want to be stuck on these forever

        addBuildingChoices()

        if (!city.isPuppet) {
            addSpaceshipPartChoice()
            addWorkerChoice()
            addWorkBoatChoice()
            addMilitaryUnitChoice()
        }

        val chosenConstruction: String =
            if (relativeCostEffectiveness.isEmpty()) { // choose one of the special constructions instead
                // add science!
                when {
                    PerpetualConstruction.science.isBuildable(cityConstructions) && !allTechsAreResearched -> PerpetualConstruction.science.name
                    PerpetualConstruction.gold.isBuildable(cityConstructions) -> PerpetualConstruction.gold.name
                    else -> PerpetualConstruction.idle.name
                }
            } else if (relativeCostEffectiveness.any { it.remainingWork < it.production * 30 }) {
                relativeCostEffectiveness.removeAll { it.remainingWork >= it.production * 30 }
                relativeCostEffectiveness.minByOrNull { it.remainingWork / it.choiceModifier / it.production.coerceAtLeast(1) }!!.choice
            }
            // it's possible that this is a new city and EVERYTHING is way expensive - ignore modifiers, go for the cheapest.
            // Nobody can plan 30 turns ahead, I don't care how cost-efficient you are.
            else relativeCostEffectiveness.minByOrNull { it.remainingWork / it.production.coerceAtLeast(1) }!!.choice

        civInfo.addNotification(
            "Work has started on [$chosenConstruction]",
            CityAction(city.location),
            NotificationCategory.Production,
            NotificationIcon.Construction
        )
        cityConstructions.currentConstructionFromQueue = chosenConstruction
    }

    private fun addMilitaryUnitChoice() {
        if (!isAtWar && !cityIsOverAverageProduction) return // don't make any military units here. Infrastructure first!
        if (!isAtWar && (civInfo.stats.statsForNextTurn.gold < 0 || militaryUnits > max(5, cities * 2))) return
        if (civInfo.gold < -50) return

        val militaryUnit = Automation.chooseMilitaryUnit(city, units) ?: return
        val unitsToCitiesRatio = cities.toFloat() / (militaryUnits + 1)
        // most buildings and civ units contribute the the civ's growth, military units are anti-growth
        var modifier = sqrt(unitsToCitiesRatio) / 2
        if (civInfo.wantsToFocusOn(Victory.Focus.Military) || isAtWar) modifier *= 2

        if (Automation.afraidOfBarbarians(civInfo)) modifier = 2f // military units are pro-growth if pressured by barbs
        if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this

        val civilianUnit = city.getCenterTile().civilianUnit
        if (civilianUnit != null && civilianUnit.hasUnique(UniqueType.FoundCity)
                && city.getCenterTile().getTilesInDistance(5).none { it.militaryUnit?.civ == civInfo })
            modifier = 5f // there's a settler just sitting here, doing nothing - BAD

        if (!civInfo.isAIOrAutoPlaying()) modifier /= 2 // Players prefer to make their own unit choices usually
        modifier *= personality.scaledFocus(PersonalityValue.Military)
        addChoice(relativeCostEffectiveness, militaryUnit, modifier)
    }

    private fun addWorkBoatChoice() {
        // Does the ruleset even have "Workboats"?
        val buildableWorkboatUnits = units
            .filter {
                it.hasUnique(UniqueType.CreateWaterImprovements)
                    && Automation.allowAutomatedConstruction(civInfo, city, it)
            }.filterBuildable()
            .toSet()
        if (buildableWorkboatUnits.isEmpty()) return

        // Is there already a Workboat nearby?
        // todo Still ignores whether that boat can reach the not-yet-found tile to improve
        val twoTurnsMovement = buildableWorkboatUnits.maxOf { (it as BaseUnit).movement } * 2
        fun MapUnit.isOurWorkBoat() = cache.hasUniqueToCreateWaterImprovements && this.civ == this@ConstructionAutomation.civInfo
        val alreadyHasWorkBoat = city.getCenterTile().getTilesInDistanceRange(1..twoTurnsMovement)
            .any { it.civilianUnit?.isOurWorkBoat() == true }
        if (alreadyHasWorkBoat) return

        // Define what makes a tile worth sending a Workboat to
        // todo Prepare for mods that allow improving water tiles without a resource?
        fun Tile.isWorthImproving(): Boolean {
            if (getOwner() != civInfo) return false
            if (!WorkerAutomation.hasWorkableSeaResource(this, civInfo)) return false
            return WorkerAutomation.isNotBonusResourceOrWorkable(this, civInfo)
        }

        // Search for a tile justifiying producing a Workboat
        // todo should workboatAutomationSearchMaxTiles depend on game state?
        fun findTileWorthImproving(): Boolean {
            val searchMaxTiles = civInfo.gameInfo.ruleset.modOptions.constants.workboatAutomationSearchMaxTiles
            val bfs = BFS(city.getCenterTile()) {
                (it.isWater || it.isCityCenter())
                    && (it.getOwner() == null || it.isFriendlyTerritory(civInfo))
                    && it.isExplored(civInfo)  // Sending WB's through unexplored terrain would be cheating
            }
            do {
                val tile = bfs.nextStep() ?: break
                if (tile.isWorthImproving()) return true
            } while (bfs.size() < searchMaxTiles)
            return false
        }

        if (!findTileWorthImproving()) return

        addChoice(relativeCostEffectiveness, buildableWorkboatUnits.minBy { it.cost }.name, 0.6f)
    }

    private fun addWorkerChoice() {
        val workerEquivalents = units
            .filter {
                it.hasUnique(UniqueType.BuildImprovements)
                        && Automation.allowAutomatedConstruction(civInfo, city, it)
            }.filterBuildable()
        if (workerEquivalents.none()) return // for mods with no worker units

        // Dedicate a worker for the first 5 cities, from then on only build another worker for every 2 cities.
        val numberOfWorkersWeWant = if (cities <= 5) cities else 5 + (cities - 5 / 2)

        if (workers < numberOfWorkersWeWant) {
            var modifier = numberOfWorkersWeWant / (workers + 0.4f) // The worse our worker to city ratio is, the more desperate we are
            if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this
            addChoice(relativeCostEffectiveness, workerEquivalents.minByOrNull { it.cost }!!.name, modifier)
        }
    }

    private fun addSpaceshipPartChoice() {
        if (!civInfo.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts)) return
        val spaceshipPart = (nonWonders + units).filter { it.name in spaceshipParts }.filterBuildable().firstOrNull()
            ?: return
        val modifier = 2f
        addChoice(relativeCostEffectiveness, spaceshipPart.name, modifier)
    }

    private fun addBuildingChoices() {
        for (building in buildings.filterBuildable() as Sequence<Building>) {
            if (building.isWonder && city.isPuppet) continue
            addChoice(relativeCostEffectiveness, building.name, getValueOfBuilding(building))
        }
    }

    fun getValueOfBuilding(building: Building): Float {
        var value = 0f
        value = applyBuildingStats(building, value)
        value = applyMilitaryBuildingValue(building, value)
        value = applyVictoryBuildingValue(building, value)
        value = applyOnetimeUniqueBonuses(building, value)
        return value
    }


    private fun applyOnetimeUniqueBonuses(building: Building, pastValue: Float): Float {
        var value = pastValue
        // TODO: Add specific Uniques here
        return value
    }

    private fun applyVictoryBuildingValue(building: Building, pastValue: Float): Float {
        var value = pastValue
        if (building.isWonder) value += 5f
        if (building.hasUnique(UniqueType.TriggersCulturalVictory)) value += 10f
        if (building.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts)) value += 10f
        return value
    }

    private fun applyMilitaryBuildingValue(building: Building, pastValue: Float): Float {
        var value = pastValue
        var warModifier = if (isAtWar) 2f else 1f
        // If this city is the closest city to another civ, that makes it a likely candidate for attack
        if (civInfo.getKnownCivs()
                    .mapNotNull { NextTurnAutomation.getClosestCities(civInfo, it) }
                    .any { it.city1 == city })
            warModifier *= 2f
        value += warModifier * building.cityHealth.toFloat() / city.getMaxHealth()
        value += warModifier * building.cityStrength.toFloat() / city.getStrength()

        for (experienceUnique in building.getMatchingUniques(UniqueType.UnitStartingExperience, cityState)) {
            var modifier = if (cityIsOverAverageProduction) 1f else 0.2f // You shouldn't be cranking out units anytime soon
            modifier *= personality.modifierFocus(PersonalityValue.Military, 0.3f)
            value += modifier
        }
        if (building.hasUnique(UniqueType.EnablesNuclearWeapons) && !civInfo.hasUnique(UniqueType.EnablesNuclearWeapons))
            value += 4f * personality.modifierFocus(PersonalityValue.Military, 0.3f)
        return value
    }

    private fun applyBuildingStats(building: Building, pastValue: Float): Float {
        val buildingStats = city.cityStats.getStatDifferenceFromBuilding(building.name)
        getBuildingStatsFromUniques(building, buildingStats)

        val surplusFood = city.cityStats.currentCityStats[Stat.Food]
        if (surplusFood < 0) {
            buildingStats.food *= 8 // Starving, need Food, get to 0
        } else if (city.population.population < 5) {
            buildingStats.food *= 3
        }

        if (buildingStats.gold < 0 && civInfo.stats.statsForNextTurn.gold < 10) {
            buildingStats.gold *= 2 // We have a gold problem and this isn't helping
        }

        if (civInfo.getHappiness() < 5)
            buildingStats.happiness * 3
        else if (civInfo.getHappiness() < 10 || civInfo.getHappiness() < civInfo.cities.size)
            buildingStats.happiness * 2

        if (city.cityStats.currentCityStats.culture < 1) {
            buildingStats.culture *= 2 // We need to start growing borders
        }
        else if (city.tiles.size < 12 && city.population.population < 5) {
            buildingStats.culture *= 2
        }

        for (stat in Stat.values()) {
            if (civInfo.wantsToFocusOn(stat))
                buildingStats[stat] *= 2f

            buildingStats[stat] *= personality.scaledFocus(PersonalityValue[stat])
        }

        return pastValue + Automation.rankStatsValue(civInfo.getPersonality().scaleStats(buildingStats.clone(), .3f), civInfo)
    }

    private fun getBuildingStatsFromUniques(building: Building, buildingStats: Stats) {
        for (unique in building.getMatchingUniques(UniqueType.StatPercentBonusCities, cityState)) {
            val statType = Stat.valueOf(unique.params[1])
            val relativeAmount = unique.params[0].toFloat() / 100f
            val amount = civInfo.stats.statsForNextTurn[statType] * relativeAmount
            buildingStats[statType] += amount
        }

        for (unique in building.getMatchingUniques(UniqueType.CarryOverFood, cityState)) {
            if (city.matchesFilter(unique.params[1]) && unique.params[0].toInt() != 0) {
                val foodGain = cityStats.currentCityStats.food + buildingStats.food
                val relativeAmount = unique.params[0].toFloat() / 100f
                buildingStats[Stat.Food] += foodGain * relativeAmount // Essentialy gives us the food per turn this unique saves us
            }
        }
    }
}
