﻿package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.GreatGeneralImplementation
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsReligion
import kotlin.math.max
import kotlin.math.min

object SpecificUnitAutomation {

    private fun hasWorkableSeaResource(tile: Tile, civInfo: Civilization): Boolean =
            tile.isWater && tile.improvement == null && tile.hasViewableResource(civInfo)

    fun automateWorkBoats(unit: MapUnit) {
        val closestReachableResource = unit.civ.cities.asSequence()
                .flatMap { city -> city.getWorkableTiles() }
                .filter {
                    hasWorkableSeaResource(it, unit.civ)
                            && (unit.currentTile == it || unit.movement.canMoveTo(it))
                }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movement.canReach(it) }

        when (closestReachableResource) {
            null -> UnitAutomation.tryExplore(unit)
            else -> {
                unit.movement.headTowards(closestReachableResource)

                // could be either fishing boats or oil well
                val isImprovable = closestReachableResource.tileResource.getImprovements().any()
                if (isImprovable && unit.currentTile == closestReachableResource)
                    UnitActions.getWaterImprovementAction(unit)?.action?.invoke()
            }
        }
    }

    fun automateGreatGeneral(unit: MapUnit): Boolean {
        //try to follow nearby units. Do not garrison in city if possible
        val maxAffectedTroopsTile = GreatGeneralImplementation.getBestAffectedTroopsTile(unit)
            ?: return false

        unit.movement.headTowards(maxAffectedTroopsTile)
        return true
    }

    fun automateCitadelPlacer(unit: MapUnit): Boolean {
        // try to revenge and capture their tiles
        val enemyCities = unit.civ.getKnownCivs()
                .filter { unit.civ.getDiplomacyManager(it).hasModifier(DiplomaticModifiers.StealingTerritory) }
                .flatMap { it.cities }.asSequence()
        // find the suitable tiles (or their neighbours)
        val tileToSteal = enemyCities.flatMap { it.getTiles() } // City tiles
                .filter { it.neighbors.any { tile -> tile.getOwner() != unit.civ } } // Edge city tiles
                .flatMap { it.neighbors.asSequence() } // Neighbors of edge city tiles
                .filter {
                    it in unit.civ.viewableTiles // we can see them
                            && it.neighbors.any { tile -> tile.getOwner() == unit.civ }// they are close to our borders
                }
                .sortedBy {
                    // get closest tiles
                    val distance = it.aerialDistanceTo(unit.currentTile)
                    // ...also get priorities to steal the most valuable for them
                    val owner = it.getOwner()
                    if (owner != null)
                        distance - WorkerAutomation.getPriority(it, owner)
                    else distance
                }
                .firstOrNull { unit.movement.canReach(it) } // canReach is performance-heavy and always a last resort
        // if there is a good tile to steal - go there
        if (tileToSteal != null) {
            unit.movement.headTowards(tileToSteal)
            if (unit.currentMovement > 0 && unit.currentTile == tileToSteal)
                UnitActions.getImprovementConstructionActions(unit, unit.currentTile).firstOrNull()?.action?.invoke()
            return true
        }

        // try to build a citadel for defensive purposes
        if (WorkerAutomation.evaluateFortPlacement(unit.currentTile, unit.civ, true)) {
            UnitActions.getImprovementConstructionActions(unit, unit.currentTile).firstOrNull()?.action?.invoke()
            return true
        }
        return false
    }

    fun automateGreatGeneralFallback(unit: MapUnit) {
        // if no unit to follow, take refuge in city or build citadel there.
        val reachableTest: (Tile) -> Boolean = {
            it.civilianUnit == null &&
                    unit.movement.canMoveTo(it)
                    && unit.movement.canReach(it)
        }
        val cityToGarrison = unit.civ.cities.asSequence().map { it.getCenterTile() }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                .firstOrNull { reachableTest(it) }
            ?: return
        if (!unit.cache.hasCitadelPlacementUnique) {
            unit.movement.headTowards(cityToGarrison)
            return
        }

        // try to find a good place for citadel nearby
        val tileForCitadel = cityToGarrison.getTilesInDistanceRange(3..4)
            .firstOrNull {
                reachableTest(it) &&
                        WorkerAutomation.evaluateFortPlacement(it, unit.civ, true)
            }
        if (tileForCitadel == null) {
            unit.movement.headTowards(cityToGarrison)
            return
        }
        unit.movement.headTowards(tileForCitadel)
        if (unit.currentMovement > 0 && unit.currentTile == tileForCitadel)
            UnitActions.getImprovementConstructionActions(unit, unit.currentTile)
                .firstOrNull()?.action?.invoke()
    }

    private fun rankTileAsCityCenter(tile: Tile, nearbyTileRankings: Map<Tile, Float>,
                                     luxuryResourcesInCivArea: Sequence<TileResource>): Float {
        val bestTilesFromOuterLayer = tile.getTilesAtDistance(2)
                .sortedByDescending { nearbyTileRankings[it] }.take(2)
        val top5Tiles = (tile.neighbors + bestTilesFromOuterLayer)
                .sortedByDescending { nearbyTileRankings[it] }
                .take(5)
        var rank = top5Tiles.map { nearbyTileRankings.getValue(it) }.sum()
        if (tile.isCoastalTile()) rank += 5

        val luxuryResourcesInCityArea = tile.getTilesAtDistance(2).filter { it.resource != null }
                .map { it.tileResource }.filter { it.resourceType == ResourceType.Luxury }.distinct()
        val luxuryResourcesAlreadyInCivArea = luxuryResourcesInCivArea.map { it.name }.toHashSet()
        val luxuryResourcesNotYetInCiv = luxuryResourcesInCityArea
                .count { it.name !in luxuryResourcesAlreadyInCivArea }
        rank += luxuryResourcesNotYetInCiv * 10

        return rank
    }

    fun automateSettlerActions(unit: MapUnit) {
        val modConstants = unit.civ.gameInfo.ruleset.modOptions.constants
        if (unit.getTile().militaryUnit == null     // Don't move until you're accompanied by a military unit
            && !unit.civ.isCityState()          // ..unless you're a city state that was unable to settle its city on turn 1
            && unit.getDamageFromTerrain() < unit.health) return    // Also make sure we won't die waiting

        val tilesNearCities = sequence {
            for (city in unit.civ.gameInfo.getCities()) {
                val center = city.getCenterTile()
                if (unit.civ.knows(city.civ) &&
                    // If the CITY OWNER knows that the UNIT OWNER agreed not to settle near them
                    city.civ.getDiplomacyManager(unit.civ).hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs)
                ) {
                    yieldAll(center.getTilesInDistance(6))
                    continue
                }
                yieldAll(center.getTilesInDistance(modConstants.minimalCityDistance)
                    .filter { it.getContinent() == center.getContinent() }
                )
                yieldAll(center.getTilesInDistance(modConstants.minimalCityDistanceOnDifferentContinents)
                    .filter { it.getContinent() != center.getContinent() }
                )
            }
        }.toSet()

        // This is to improve performance - instead of ranking each tile in the area up to 19 times, do it once.
        val nearbyTileRankings = unit.getTile().getTilesInDistance(7)
                .associateBy({ it }, { Automation.rankTile(it, unit.civ) })

        val distanceFromHome = if (unit.civ.cities.isEmpty()) 0
            else unit.civ.cities.minOf { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
        val range = max(1, min(5, 8 - distanceFromHome)) // Restrict vision when far from home to avoid death marches

        val possibleCityLocations = unit.getTile().getTilesInDistance(range)
                .filter {
                    val tileOwner = it.getOwner()
                    it.isLand && !it.isImpassible() && (tileOwner == null || tileOwner == unit.civ) // don't allow settler to settle inside other civ's territory
                            && (unit.currentTile == it || unit.movement.canMoveTo(it))
                            && it !in tilesNearCities
                }.toList()

        val luxuryResourcesInCivArea = unit.civ.cities.asSequence()
                .flatMap { it.getTiles().asSequence() }.filter { it.resource != null }
                .map { it.tileResource }.filter { it.resourceType == ResourceType.Luxury }
                .distinct()

        if (unit.civ.gameInfo.turns == 0) {   // Special case, we want AI to settle in place on turn 1.
            val foundCityAction = UnitActions.getFoundCityAction(unit, unit.getTile())
            // Depending on era and difficulty we might start with more than one settler. In that case settle the one with the best location
            val otherSettlers = unit.civ.units.getCivUnits().filter { it.currentMovement > 0 && it.baseUnit == unit.baseUnit }
            if(foundCityAction?.action != null &&
                    otherSettlers.none {
                        rankTileAsCityCenter(it.getTile(), nearbyTileRankings, emptySequence()) > rankTileAsCityCenter(unit.getTile(), nearbyTileRankings, emptySequence())
                    } ) {
                foundCityAction.action.invoke()
                return
            }
        }

        val citiesByRanking = possibleCityLocations
                .map { Pair(it, rankTileAsCityCenter(it, nearbyTileRankings, luxuryResourcesInCivArea)) }
                .sortedByDescending { it.second }.toList()

        // It's possible that we'll see a tile "over the sea" that's better than the tiles close by, but that's not a reason to abandon the close tiles!
        // Also this lead to some routing problems, see https://github.com/yairm210/Unciv/issues/3653
        val bestCityLocation: Tile? = citiesByRanking.firstOrNull {
            val pathSize = unit.movement.getShortestPath(it.first).size
            return@firstOrNull pathSize in 1..3
        }?.first

        if (bestCityLocation == null) { // We got a badass over here, all tiles within 5 are taken?
            // Try to move towards the frontier

            /** @return the number of tiles 4 (un-modded) out from this city that could hold a city, ie how lonely this city is */
            fun getFrontierScore(city: City) = city.getCenterTile()
                .getTilesAtDistance(city.civ.gameInfo.ruleset.modOptions.constants.minimalCityDistance + 1)
                .count { it.canBeSettled() && (it.getOwner() == null || it.getOwner() == city.civ ) }

            val frontierCity = unit.civ.cities.maxByOrNull { getFrontierScore(it) }
            if (frontierCity != null && getFrontierScore(frontierCity) > 0  && unit.movement.canReach(frontierCity.getCenterTile()))
                unit.movement.headTowards(frontierCity.getCenterTile())
            if (UnitAutomation.tryExplore(unit)) return // try to find new areas
            UnitAutomation.wander(unit) // go around aimlessly
            return
        }

        val foundCityAction = UnitActions.getFoundCityAction(unit, bestCityLocation)
        if (foundCityAction?.action == null) { // this means either currentMove == 0 or city within 3 tiles
            if (unit.currentMovement > 0) // therefore, city within 3 tiles
                throw Exception("City within distance")
            return
        }

        unit.movement.headTowards(bestCityLocation)
        if (unit.getTile() == bestCityLocation && unit.currentMovement > 0)
            foundCityAction.action.invoke()
    }

    fun automateImprovementPlacer(unit: MapUnit) {
        val improvementBuildingUniques = unit.getMatchingUniques(UniqueType.ConstructImprovementConsumingUnit) +
                unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly)

        val improvementName = improvementBuildingUniques.first().params[0]
        val improvement = unit.civ.gameInfo.ruleset.tileImprovements[improvementName]
            ?: return
        val relatedStat = improvement.maxByOrNull { it.value }?.key ?: Stat.Culture

        val citiesByStatBoost = unit.civ.cities.sortedByDescending {
            it.cityStats.statPercentBonusTree.totalStats[relatedStat]
        }


        for (city in citiesByStatBoost) {
            val applicableTiles = city.getWorkableTiles().filter {
                it.isLand && it.resource == null && !it.isCityCenter()
                        && (unit.currentTile == it || unit.movement.canMoveTo(it))
                        && !it.containsGreatImprovement() && it.improvementFunctions.canBuildImprovement(improvement, unit.civ)
            }
            if (applicableTiles.none()) continue

            val pathToCity = unit.movement.getShortestPath(city.getCenterTile())

            if (pathToCity.isEmpty()) continue
            if (pathToCity.size > 2 && unit.getTile().getCity() != city) {
                if (unit.getTile().militaryUnit == null) return // Don't move until you're accompanied by a military unit
                unit.movement.headTowards(city.getCenterTile())
                return
            }

            // if we got here, we're pretty close, start looking!
            val chosenTile = applicableTiles.sortedByDescending {
                Automation.rankTile(
                    it,
                    unit.civ
                )
            }
                .firstOrNull { unit.movement.canReach(it) }
                ?: continue // to another city

            unit.movement.headTowards(chosenTile)
            if (unit.currentTile == chosenTile)
                if (unit.currentTile.isPillaged())
                    UnitActions.getRepairAction(unit).invoke()
                else
                    UnitActions.getImprovementConstructionActions(unit, unit.currentTile).firstOrNull()?.action?.invoke()
            return
        }
    }

    fun automateAddInCapital(unit: MapUnit) {
        if (unit.civ.getCapital() == null) return // safeguard
        val capitalTile = unit.civ.getCapital()!!.getCenterTile()
        if (unit.movement.canReach(capitalTile))
            unit.movement.headTowards(capitalTile)
        if (unit.getTile() == capitalTile) {
            UnitActions.getAddInCapitalAction(unit, capitalTile).action!!()
            return
        }
    }

    fun automateMissionary(unit: MapUnit) {
        if (unit.religion != unit.civ.religionManager.religion?.name || unit.religion == null)
            return unit.disband()

        val ourCitiesWithoutReligion = unit.civ.cities.filter {
            it.religion.getMajorityReligion() != unit.civ.religionManager.religion
        }

        val city =
            if (ourCitiesWithoutReligion.any())
                ourCitiesWithoutReligion.minByOrNull { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            else unit.civ.gameInfo.getCities().asSequence()
                .filter { it.religion.getMajorityReligion() != unit.civ.religionManager.religion }
                .filter { it.civ.knows(unit.civ) && !it.civ.isAtWarWith(unit.civ) }
                .filterNot { it.religion.isProtectedByInquisitor(unit.religion) }
                .minByOrNull { it.getCenterTile().aerialDistanceTo(unit.getTile()) }

        if (city == null) return
        val destination = city.getTiles().asSequence()
            .filter { unit.movement.canMoveTo(it) || it == unit.getTile() }
            .sortedBy { it.aerialDistanceTo(unit.getTile()) }
            .firstOrNull { unit.movement.canReach(it) } ?: return

        unit.movement.headTowards(destination)

        if (unit.getTile() in city.getTiles() && unit.civ.religionManager.maySpreadReligionNow(unit)) {
            doReligiousAction(unit, unit.getTile())
        }
    }

    fun automateInquisitor(unit: MapUnit) {
        val civReligion = unit.civ.religionManager.religion

        if (unit.religion != civReligion?.name || unit.religion == null)
            return unit.disband() // No need to keep a unit we can't use, as it only blocks religion spreads of religions other that its own

        val holyCity = unit.civ.religionManager.getHolyCity()
        val cityToConvert = determineBestInquisitorCityToConvert(unit) // Also returns null if the inquisitor can't convert cities
        val pressureDeficit =
            if (cityToConvert == null) 0
            else cityToConvert.religion.getPressureDeficit(civReligion?.name)

        val citiesToProtect = unit.civ.cities.asSequence()
            .filter { it.religion.getMajorityReligion() == civReligion }
            // We only look at cities that are not currently protected or are protected by us
            .filter { !it.religion.isProtectedByInquisitor() || unit.getTile() in it.getCenterTile().getTilesInDistance(1) }

        // cities with most populations will be prioritized by the AI
        val cityToProtect = citiesToProtect.maxByOrNull { it.population.population }

        var destination: Tile?

        destination = when {
            cityToConvert != null
            && (cityToConvert == holyCity
                || pressureDeficit > Constants.aiPreferInquisitorOverMissionaryPressureDifference
                || cityToConvert.religion.isBlockedHolyCity && cityToConvert.religion.religionThisIsTheHolyCityOf == civReligion?.name
            ) && unit.canDoLimitedAction(Constants.removeHeresy) -> {
                cityToConvert.getCenterTile()
            }
            cityToProtect != null && unit.hasUnique(UniqueType.PreventSpreadingReligion) -> {
                if (holyCity != null && !holyCity.religion.isProtectedByInquisitor())
                    holyCity.getCenterTile()
                else cityToProtect.getCenterTile()
            }
            cityToConvert != null -> cityToConvert.getCenterTile()
            else -> null
        }

        if (destination == null) return

        if (!unit.movement.canReach(destination)) {
            destination = destination.neighbors.asSequence()
                .filter { unit.movement.canMoveTo(it) || it == unit.getTile() }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movement.canReach(it) }
                ?: return
        }

        unit.movement.headTowards(destination)

        if (cityToConvert != null && unit.getTile().getCity() == destination.getCity()) {
            doReligiousAction(unit, destination)
        }
    }

    private fun determineBestInquisitorCityToConvert(
        unit: MapUnit,
    ): City? {
        if (unit.religion != unit.civ.religionManager.religion?.name || !unit.canDoLimitedAction(Constants.removeHeresy))
            return null

        val holyCity = unit.civ.religionManager.getHolyCity()
        if (holyCity != null && holyCity.religion.getMajorityReligion() != unit.civ.religionManager.religion!!)
            return holyCity

        val blockedHolyCity = unit.civ.cities.firstOrNull { it.religion.isBlockedHolyCity && it.religion.religionThisIsTheHolyCityOf == unit.religion }
        if (blockedHolyCity != null)
            return blockedHolyCity

        return unit.civ.cities.asSequence()
            .filter { it.religion.getMajorityReligion() != null }
            .filter { it.religion.getMajorityReligion()!! != unit.civ.religionManager.religion }
            // Don't go if it takes too long
            .filter { it.getCenterTile().aerialDistanceTo(unit.currentTile) <= 20 }
            .maxByOrNull { it.religion.getPressureDeficit(unit.civ.religionManager.religion?.name) }
    }

    fun automateFighter(unit: MapUnit) {
        val tilesInRange = unit.currentTile.getTilesInDistance(unit.getRange())
        val enemyAirUnitsInRange = tilesInRange
                .flatMap { it.airUnits.asSequence() }.filter { it.civ.isAtWarWith(unit.civ) }

        if (enemyAirUnitsInRange.any()) return // we need to be on standby in case they attack

        if (BattleHelper.tryAttackNearbyEnemy(unit)) return

        if (tryRelocateToCitiesWithEnemyNearBy(unit)) return

        val pathsToCities = unit.movement.getAerialPathsToCities()
        if (pathsToCities.isEmpty()) return // can't actually move anywhere else

        val citiesByNearbyAirUnits = pathsToCities.keys
                .groupBy { key ->
                    key.getTilesInDistance(unit.getMaxMovementForAirUnits())
                            .count {
                                val firstAirUnit = it.airUnits.firstOrNull()
                                firstAirUnit != null && firstAirUnit.civ.isAtWarWith(unit.civ)
                            }
                }

        if (citiesByNearbyAirUnits.keys.any { it != 0 }) {
            val citiesWithMostNeedOfAirUnits = citiesByNearbyAirUnits.maxByOrNull { it.key }!!.value
            //todo: maybe group by size and choose highest priority within the same size turns
            val chosenCity = citiesWithMostNeedOfAirUnits.minByOrNull { pathsToCities.getValue(it).size }!! // city with min path = least turns to get there
            val firstStepInPath = pathsToCities.getValue(chosenCity).first()
            unit.movement.moveToTile(firstStepInPath)
            return
        }

        // no city needs fighters to defend, so let's attack stuff from the closest possible location
        tryMoveToCitiesToAerialAttackFrom(pathsToCities, unit)

    }

    fun automateBomber(unit: MapUnit) {
        if (BattleHelper.tryAttackNearbyEnemy(unit)) return

        if (tryRelocateToCitiesWithEnemyNearBy(unit)) return

        val pathsToCities = unit.movement.getAerialPathsToCities()
        if (pathsToCities.isEmpty()) return // can't actually move anywhere else
        tryMoveToCitiesToAerialAttackFrom(pathsToCities, unit)
    }

    private fun tryMoveToCitiesToAerialAttackFrom(pathsToCities: HashMap<Tile, ArrayList<Tile>>, airUnit: MapUnit) {
        val citiesThatCanAttackFrom = pathsToCities.keys
            .filter { destinationCity ->
                destinationCity != airUnit.currentTile
                        && destinationCity.getTilesInDistance(airUnit.getRange())
                    .any { BattleHelper.containsAttackableEnemy(it, MapUnitCombatant(airUnit)) }
            }
        if (citiesThatCanAttackFrom.isEmpty()) return

        //todo: this logic looks similar to some parts of automateFighter, maybe pull out common code
        //todo: maybe group by size and choose highest priority within the same size turns
        val closestCityThatCanAttackFrom =
            citiesThatCanAttackFrom.minByOrNull { pathsToCities[it]!!.size }!!
        val firstStepInPath = pathsToCities[closestCityThatCanAttackFrom]!!.first()
        airUnit.movement.moveToTile(firstStepInPath)
    }

    fun automateNukes(unit: MapUnit) {
        val tilesInRange = unit.currentTile.getTilesInDistance(unit.getRange())
        for (tile in tilesInRange) {
            // For now AI will only use nukes against cities because in all honesty that's the best use for them.
            if (tile.isCityCenter()
                    && tile.getOwner()!!.isAtWarWith(unit.civ)
                    && tile.getCity()!!.health > tile.getCity()!!.getMaxHealth() / 2
                    && Battle.mayUseNuke(MapUnitCombatant(unit), tile)) {
                val blastRadius = unit.getMatchingUniques(UniqueType.BlastRadius)
                    .firstOrNull()?.params?.get(0)?.toInt() ?: 2
                val tilesInBlastRadius = tile.getTilesInDistance(blastRadius)
                val civsInBlastRadius = tilesInBlastRadius.mapNotNull { it.getOwner() } +
                        tilesInBlastRadius.mapNotNull { it.getFirstUnit()?.civ }
                // Don't nuke if it means we will be declaring war on someone!
                if (civsInBlastRadius.none { it != unit.civ && !it.isAtWarWith(unit.civ) }) {
                    Battle.NUKE(MapUnitCombatant(unit), tile)
                    return
                }
            }
        }
        tryRelocateToNearbyAttackableCities(unit)
    }

    // This really needs to be changed, to have better targeting for missiles
    fun automateMissile(unit: MapUnit) {
        if (BattleHelper.tryAttackNearbyEnemy(unit)) return
        tryRelocateToNearbyAttackableCities(unit)
    }

    private fun tryRelocateToNearbyAttackableCities(unit: MapUnit) {
        val tilesInRange = unit.currentTile.getTilesInDistance(unit.getRange())
        val immediatelyReachableCities = tilesInRange
            .filter { unit.movement.canMoveTo(it) }

        for (city in immediatelyReachableCities) if (city.getTilesInDistance(unit.getRange())
                .any { it.isCityCenter() && it.getOwner()!!.isAtWarWith(unit.civ) }
        ) {
            unit.movement.moveToTile(city)
            return
        }

        if (unit.baseUnit.isAirUnit()) {
            val pathsToCities = unit.movement.getAerialPathsToCities()
            if (pathsToCities.isEmpty()) return // can't actually move anywhere else
            tryMoveToCitiesToAerialAttackFrom(pathsToCities, unit)
        } else UnitAutomation.tryHeadTowardsEnemyCity(unit)
    }

    private fun tryRelocateToCitiesWithEnemyNearBy(unit: MapUnit): Boolean {
        val immediatelyReachableCitiesAndCarriers = unit.currentTile
                .getTilesInDistance(unit.getMaxMovementForAirUnits()).filter { unit.movement.canMoveTo(it) }

        for (city in immediatelyReachableCitiesAndCarriers) {
            if (city.getTilesInDistance(unit.getRange())
                            .any {
                                BattleHelper.containsAttackableEnemy(
                                    it,
                                    MapUnitCombatant(unit)
                                )
                            }) {
                unit.movement.moveToTile(city)
                return true
            }
        }
        return false
    }

    fun foundReligion(unit: MapUnit) {
        val cityToFoundReligionAt =
            if (unit.getTile().isCityCenter() && !unit.getTile().owningCity!!.isHolyCity()) unit.getTile().owningCity
            else unit.civ.cities.firstOrNull {
                !it.isHolyCity()
                && unit.movement.canMoveTo(it.getCenterTile())
                && unit.movement.canReach(it.getCenterTile())
            }
        if (cityToFoundReligionAt == null) return
        if (unit.getTile() != cityToFoundReligionAt.getCenterTile()) {
            unit.movement.headTowards(cityToFoundReligionAt.getCenterTile())
            return
        }

        UnitActionsReligion.getFoundReligionAction(unit)()
    }

    fun enhanceReligion(unit: MapUnit) {
        // Try go to a nearby city
        if (!unit.getTile().isCityCenter())
            UnitAutomation.tryEnterOwnClosestCity(unit)

        // If we were unable to go there this turn, unable to do anything else
        if (!unit.getTile().isCityCenter())
            return

        UnitActionsReligion.getEnhanceReligionAction(unit)()
    }

    private fun doReligiousAction(unit: MapUnit, destination: Tile) {
        val religiousActions = ArrayList<UnitAction>()
        UnitActionsReligion.addActionsWithLimitedUses(unit, religiousActions, destination)
        if (religiousActions.firstOrNull()?.action == null) return
        religiousActions.first().action!!.invoke()
    }
}
