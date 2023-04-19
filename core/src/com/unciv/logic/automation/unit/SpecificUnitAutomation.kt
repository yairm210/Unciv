package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.GreatGeneralImplementation
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsReligion

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

    fun automateSettlerActions(unit: MapUnit) {
        if (unit.civ.gameInfo.turns == 0) {   // Special case, we want AI to settle in place on turn 1.
            val foundCityAction = UnitActions.getFoundCityAction(unit, unit.getTile())
            // Depending on era and difficulty we might start with more than one settler. In that case settle the one with the best location
            val otherSettlers = unit.civ.units.getCivUnits().filter { it.currentMovement > 0 && it.baseUnit == unit.baseUnit }
            if(foundCityAction?.action != null &&
                    otherSettlers.none {
                        CityLocationTileRanker.rankTileAsCityCenter(
                            it.getTile(), unit.civ
                        ) > CityLocationTileRanker.rankTileAsCityCenter(
                            unit.getTile(), unit.civ
                        )
                    }
            ) {
                foundCityAction.action.invoke()
                return
            }
        }

        if (unit.getTile().militaryUnit == null     // Don't move until you're accompanied by a military unit
            && !unit.civ.isCityState()          // ..unless you're a city state that was unable to settle its city on turn 1
            && unit.getDamageFromTerrain() < unit.health) return    // Also make sure we won't die waiting

        // It's possible that we'll see a tile "over the sea" that's better than the tiles close by, but that's not a reason to abandon the close tiles!
        // Also this lead to some routing problems, see https://github.com/yairm210/Unciv/issues/3653
        val bestCityLocation: Tile? =
                CityLocationTileRanker.getBestTilesToFoundCity(unit).firstOrNull {
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

    /** @return whether there was any progress in placing the improvement. A return value of `false`
     * can be interpreted as: the unit doesn't know where to place the improvement or is stuck. */
    fun automateImprovementPlacer(unit: MapUnit) : Boolean {
        val improvementBuildingUniques = unit.getMatchingUniques(UniqueType.ConstructImprovementConsumingUnit) +
                unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly)

        val improvementName = improvementBuildingUniques.first().params[0]
        val improvement = unit.civ.gameInfo.ruleset.tileImprovements[improvementName]
            ?: return false
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
                // Radius 5 is quite arbitrary. Few units have such a high movement radius although
                // streets might modify it. Also there might be invisible units, so this is just an
                // approximation for relative safety and simplicity.
                val enemyUnitsNearby = unit.getTile().getTilesInDistance(5).any { tileNearby ->
                    tileNearby.getUnits().any { unitOnTileNearby ->
                        unitOnTileNearby.isMilitary() && unitOnTileNearby.civ.isAtWarWith(unit.civ)
                    }
                }
                // Don't move until you're accompanied by a military unit if there are enemies nearby.
                if (unit.getTile().militaryUnit == null && enemyUnitsNearby) return true
                unit.movement.headTowards(city.getCenterTile())
                return true
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

            val unitTileBeforeMovement = unit.currentTile
            unit.movement.headTowards(chosenTile)
            if (unit.currentTile == chosenTile) {
                if (unit.currentTile.isPillaged())
                    UnitActions.getRepairAction(unit).invoke()
                else
                    UnitActions.getImprovementConstructionActions(unit, unit.currentTile)
                        .firstOrNull()?.action?.invoke()
                return true
            }
            return unitTileBeforeMovement != unit.currentTile
        }
        // No city needs this improvement.
        return false
    }

    /** @return whether there was any progress in conducting the trade mission. A return value of
     * `false` can be interpreted as: the unit doesn't know where to go or there are no city
     * states. */
    fun conductTradeMission(unit: MapUnit): Boolean {
        val closestCityStateTile =
                unit.civ.gameInfo.civilizations
                    .filter {
                        !unit.civ.isAtWarWith(it) && it.isCityState() && it.cities.isNotEmpty()
                    }
                    .flatMap { it.cities[0].getTiles() }
                    .filter { unit.civ.hasExplored(it) }
                    .mapNotNull { tile ->
                        val path = unit.movement.getShortestPath(tile)
                        if (path.size <= 10) tile to path.size else null
                    }
                    .minByOrNull { it.second }?.first
                    ?: return false

        val conductTradeMissionAction = UnitActions.getUnitActions(unit)
            .firstOrNull { it.type == UnitActionType.ConductTradeMission }
        if (conductTradeMissionAction?.action != null) {
            conductTradeMissionAction.action.invoke()
            return true
        }

        val unitTileBeforeMovement = unit.currentTile
        unit.movement.headTowards(closestCityStateTile)

        return unitTileBeforeMovement != unit.currentTile
    }

    /**
     * If there's a city nearby that can construct a wonder, walk there an get it built. Typically I
     * like to build all wonders in the same city to have the boni accumulate (and it typically ends
     * up being my capital), but that would need too much logic (e.g. how far away is the capital,
     * is the wonder likely still available by the time I'm there, is this particular wonder even
     * buildable in the capital, etc.)
     *
     * @return whether there was any progress in speeding up a wonder construction. A return value
     * of `false` can be interpreted as: the unit doesn't know where to go or is stuck. */
    fun speedupWonderConstruction(unit: MapUnit): Boolean {
        val nearbyCityWithAvailableWonders = unit.civ.cities.filter { city ->
            // Maybe it would be nice to make space in the city if there's already some
            // other civilian unit in there for whatever reason, but again that seems a lot of
            // additional complexity for questionable gain.
            (unit.movement.canMoveTo(city.getCenterTile()) || unit.currentTile == city.getCenterTile())
                    // Don't speed up construction in small cities. There's a risk the great
                    // engineer can't get it done entirely and then it takes forever for the small
                    // city to finish the rest.
                    && city.population.population >= 3
                    && getWonderThatWouldBenefitFromBeingSpedUp(city) != null
        }.mapNotNull { city ->
            val path = unit.movement.getShortestPath(city.getCenterTile())
            if (path.size <= 5) city to path.size else null
        }.minByOrNull { it.second }?.first

        if (nearbyCityWithAvailableWonders == null) {
            return false
        }

        if (unit.currentTile == nearbyCityWithAvailableWonders.getCenterTile()) {
            val wonderToHurry =
                    getWonderThatWouldBenefitFromBeingSpedUp(nearbyCityWithAvailableWonders)!!
            nearbyCityWithAvailableWonders.cityConstructions.constructionQueue.add(
                0,
                wonderToHurry.name
            )
            UnitActions.getUnitActions(unit)
                .first {
                    it.type == UnitActionType.HurryBuilding
                            || it.type == UnitActionType.HurryWonder }
                .action!!.invoke()
            return true
        }

        // Walk towards the city.
        val tileBeforeMoving = unit.getTile()
        unit.movement.headTowards(nearbyCityWithAvailableWonders.getCenterTile())
        return tileBeforeMoving != unit.currentTile
    }

    private fun getWonderThatWouldBenefitFromBeingSpedUp(city: City): Building? {
        return city.cityConstructions.getBuildableBuildings().filter { building ->
            building.isWonder && !building.hasUnique(UniqueType.CannotBeHurried)
                    && city.cityConstructions.turnsToConstruction(building.name) >= 5
        }.sortedBy { -city.cityConstructions.getRemainingWork(it.name) }.firstOrNull()
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
