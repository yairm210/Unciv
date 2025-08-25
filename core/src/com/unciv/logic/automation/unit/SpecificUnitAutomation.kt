package com.unciv.logic.automation.unit

import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.unit.CivilianUnitAutomation.tryRunAwayIfNeccessary
import com.unciv.logic.battle.GreatGeneralImplementation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsFromUniques
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

object SpecificUnitAutomation {

    fun automateGreatGeneral(unit: MapUnit): Boolean {
        //try to follow nearby units. Do not garrison in city if possible
        val maxAffectedTroopsTile = GreatGeneralImplementation.getBestAffectedTroopsTile(unit)
            ?: return false

        unit.movement.headTowards(maxAffectedTroopsTile)
        return true
    }

    fun automateCitadelPlacer(unit: MapUnit): Boolean {
        // Keep at least 2 generals alive
        if (unit.hasUnique(UniqueType.StrengthBonusInRadius) 
                && unit.civ.units.getCivUnits().count { it.hasUnique(UniqueType.StrengthBonusInRadius) } < 3) 
            return false
        // try to revenge and capture their tiles
        val enemyCities = unit.civ.getKnownCivs()
                .filter { unit.civ.getDiplomacyManager(it)!!.hasModifier(DiplomaticModifiers.StealingTerritory) }
                .flatMap { it.cities }
        // find the suitable tiles (or their neighbours)
        val tileToSteal = enemyCities.flatMap { it.getTiles() } // City tiles
                .filter { it.neighbors.any { tile -> tile.getOwner() != unit.civ } } // Edge city tiles
                .flatMap { it.neighbors } // Neighbors of edge city tiles
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
                        distance - owner.getWorkerAutomation().getBasePriority(it, unit).roundToInt()
                    else distance
                }
                .firstOrNull { unit.movement.canReach(it) } // canReach is performance-heavy and always a last resort
        // if there is a good tile to steal - go there
        if (tileToSteal != null) {
            unit.movement.headTowards(tileToSteal)
            if (unit.hasMovement() && unit.currentTile == tileToSteal)
                UnitActionsFromUniques.getImprovementConstructionActionsFromGeneralUnique(unit, unit.currentTile).firstOrNull()?.action?.invoke()
            return true
        }

        // try to build a citadel for defensive purposes
        if (unit.civ.getWorkerAutomation().evaluateFortPlacement(unit.currentTile, true)) {
            UnitActionsFromUniques.getImprovementConstructionActionsFromGeneralUnique(unit, unit.currentTile).firstOrNull()?.action?.invoke()
            return true
        }
        return false
    }

    fun automateSettlerActions(unit: MapUnit, dangerousTiles: HashSet<Tile>) {
        // If we don't have any cities, we are probably at the start of the game with only one settler
        // If we are at the start of the game lets spend a maximum of 3 turns to settle our first city
        // As our turns progress lets shrink the area that we look at to make sure that we stay on target
        // If we have gone more than 3 turns without founding a city lets search a wider area
        // TODO: Figure out a way to not use turns as it might not be the best metric, what if we are a civ starting in the middle of a game?
        val rangeToSearch = if (unit.civ.cities.isEmpty() && unit.civ.gameInfo.turns < 4) (3 - unit.civ.gameInfo.turns).coerceAtLeast(1) else null

        // It's possible that we'll see a tile "over the sea" that's better than the tiles close by, but that's not a reason to abandon the close tiles!
        // Also this lead to some routing problems, see https://github.com/yairm210/Unciv/issues/3653
        val bestTilesInfo = CityLocationTileRanker.getBestTilesToFoundCity(unit, rangeToSearch, 20f)
        var bestCityLocation: Tile? = null

        if (unit.civ.gameInfo.turns == 0 && unit.civ.cities.isEmpty() && bestTilesInfo.tileRankMap.containsKey(unit.getTile())) {   // Special case, we want AI to settle in place on turn 1.
            val foundCityAction = UnitActionsFromUniques.getFoundCityAction(unit, unit.getTile())
            // Depending on era and difficulty we might start with more than one settler. In that case settle the one with the best location
            val allUnsettledSettlers = unit.civ.units.getCivUnits().filter { it.hasMovement() && it.baseUnit == unit.baseUnit }

            // Don't settle immediately if we only have one settler, look for a better location
            val bestSettlerInRange = allUnsettledSettlers.maxByOrNull {
                if (bestTilesInfo.tileRankMap.containsKey(it.getTile()))
                    bestTilesInfo.tileRankMap[it.getTile()]!!
                else -1f
            }
            if (bestSettlerInRange == unit && foundCityAction?.action != null) {
                foundCityAction.action.invoke()
                return
            }
            // Since this settler is not in the best location, lets assume the best settler will found their city where they are
            if (bestSettlerInRange != null)
                bestTilesInfo.tileRankMap = HashMap(bestTilesInfo.tileRankMap.filter { it.key.aerialDistanceTo(bestSettlerInRange.getTile()) > 4 })
        }


        // If the tile we are currently on is close to the best tile, then lets just settle here instead
        if (bestTilesInfo.tileRankMap.containsKey(unit.getTile())
                && (bestTilesInfo.bestTile == null || bestTilesInfo.tileRankMap[unit.getTile()]!! >= bestTilesInfo.bestTileRank - 2)) {
                bestCityLocation = unit.getTile()
        }

        //Shortcut, if the best tile is nearby than lets just take it
        if (bestCityLocation == null && bestTilesInfo.bestTile != null && unit.movement.getShortestPath(bestTilesInfo.bestTile!!).size in 1..3) {
            bestCityLocation = bestTilesInfo.bestTile
        }

        if (bestCityLocation == null) {
            // Find the best tile that is within
            @Readonly
            fun isTileRankOK(it: Map.Entry<Tile, Float>): Boolean {
                if (it.key in dangerousTiles && it.key != unit.getTile()) return false
                val pathSize = unit.movement.getShortestPath(it.key).size
                return pathSize in 1..3
            }

            bestCityLocation = bestTilesInfo.tileRankMap.entries.asSequence()
                .filter { bestTilesInfo.bestTile == null || it.value >= bestTilesInfo.bestTileRank - 5 }
                .sortedByDescending { it.value }
                .firstOrNull(::isTileRankOK)
                ?.key
        }

        // We still haven't found a best city tile within 3 turns so lets just head to the best tile
        // Note that we must check that the shortest path exists or else an error will be thrown
        if (bestCityLocation == null && bestTilesInfo.bestTile != null && unit.movement.getShortestPath(bestTilesInfo.bestTile!!).size in 1..8) {
            bestCityLocation = bestTilesInfo.bestTile
        }

        if (bestCityLocation == null) { // We got a badass over here, all tiles within 5 are taken?
            // Try to move towards the frontier

            /** @return the number of tiles 4 (un-modded) out from this city that could hold a city, ie how lonely this city is */
            @Readonly
            fun getFrontierScore(city: City) = city.getCenterTile()
                .getTilesAtDistance(city.civ.gameInfo.ruleset.modOptions.constants.minimalCityDistance + 1)
                .count { it.canBeSettled() && (it.getOwner() == null || it.getOwner() == city.civ ) }

            val frontierCity = unit.civ.cities.maxByOrNull { getFrontierScore(it) }
            if (frontierCity != null && getFrontierScore(frontierCity) > 0  && unit.movement.canReach(frontierCity.getCenterTile()))
                unit.movement.headTowards(frontierCity.getCenterTile())
            if (UnitAutomation.tryExplore(unit)) return // try to find new areas
            UnitAutomation.wander(unit, tilesToAvoid = dangerousTiles) // go around aimlessly
            return
        }

        val foundCityAction = UnitActionsFromUniques.getFoundCityAction(unit, bestCityLocation)
        if (foundCityAction?.action == null) { // this means either currentMove == 0 or city within 3 tiles
            if (unit.hasMovement() && !unit.civ.isOneCityChallenger()) // therefore, city within 3 tiles
                throw Exception("City within distance")
            return
        }

        val shouldSettle = (unit.getTile() == bestCityLocation && unit.hasMovement())
        if (shouldSettle) return foundCityAction.action.invoke()
        //Settle if we're already on the best tile, before looking if we should retreat from barbarians
        if (tryRunAwayIfNeccessary(unit)) return 
        unit.movement.headTowards(bestCityLocation)
        val shouldSettleNow = (unit.getTile() == bestCityLocation && unit.hasMovement())
        if (shouldSettleNow) foundCityAction.action.invoke() 
    }

    /** @return whether there was any progress in placing the improvement. A return value of `false`
     * can be interpreted as: the unit doesn't know where to place the improvement or is stuck. */
    fun automateImprovementPlacer(unit: MapUnit) : Boolean {
        val improvementBuildingUnique = unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly).firstOrNull()
            ?: return false

        val improvementName = improvementBuildingUnique.params[0]
        val improvement = unit.civ.gameInfo.ruleset.tileImprovements[improvementName]
            ?: return false
        val relatedStat = improvement.maxByOrNull { it.value }?.key ?: Stat.Culture

        val citiesByStatBoost = unit.civ.cities.sortedByDescending {
            it.cityStats.statPercentBonusTree.totalStats[relatedStat]
        }

        val averageTerrainStatsValue = unit.civ.gameInfo.ruleset.terrains.values.asSequence()
            .filter { it.type == TerrainType.Land }
            .map { Automation.rankStatsValue(it, unit.civ) }
            .average()

        val localUniqueCache = LocalUniqueCache()
        for (city in citiesByStatBoost) {
            val applicableTiles = city.getWorkableTiles().filter {
                it.isLand && it.resource == null && !it.isCityCenter()
                        && (unit.currentTile == it || unit.movement.canMoveTo(it))
                        && it.improvement == null
                        && it.improvementFunctions.canBuildImprovement(improvement, unit.cache.state)
                        && Automation.rankTile(it, unit.civ, localUniqueCache) > averageTerrainStatsValue
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
                    unit.civ,
                    localUniqueCache
                )
            }
                .firstOrNull { unit.movement.canReach(it) }
                ?: continue // to another city

            val unitTileBeforeMovement = unit.currentTile
            unit.movement.headTowards(chosenTile)
            if (unit.currentTile == chosenTile) {
                if (unit.currentTile.isPillaged())
                    UnitActions.invokeUnitAction(unit, UnitActionType.Repair)
                else UnitActions.invokeUnitAction(unit, UnitActionType.CreateImprovement)
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
                        it != unit.civ
                            && !unit.civ.isAtWarWith(it)
                            && it.isCityState
                            && it.cities.isNotEmpty()
                    }
                    .flatMap { it.cities[0].getTiles() }
                    .filter { unit.civ.hasExplored(it) }
                    .mapNotNull { tile ->
                        val path = unit.movement.getShortestPath(tile)
                        // 0 is unreachable, 10 is too far away
                        if (path.size in 1..10) tile to path.size else null
                    }
                    .minByOrNull { it.second }?.first
                    ?: return false

        val conductedTradeMission = UnitActions.invokeUnitAction(unit, UnitActionType.ConductTradeMission)
        if (conductedTradeMission) return true

        val unitTileBeforeMovement = unit.currentTile
        unit.movement.headTowards(closestCityStateTile)

        return unitTileBeforeMovement != unit.currentTile
    }

    /**
     * If there's a city nearby that can construct a wonder, walk there an get it built. Typically I
     * like to build all wonders in the same city to have the bonuses accumulate (and it typically ends
     * up being my capital), but that would need too much logic (e.g. how far away is the capital,
     * is the wonder likely still available by the time I'm there, is this particular wonder even
     * buildable in the capital, etc.)
     *
     * @return whether there was any progress in speeding up a wonder construction. A return value
     * of `false` can be interpreted as: the unit doesn't know where to go or is stuck. */
    fun speedupWonderConstruction(unit: MapUnit): Boolean {
        val nearbyCityWithAvailableWonders = unit.civ.cities.filter { city ->
            // Don't speed up construction in small cities. There's a risk the great
            // engineer can't get it done entirely and then it takes forever for the small
            // city to finish the rest.
            city.population.population >= 3 &&
            (unit.movement.canMoveTo(city.getCenterTile()) || unit.currentTile == city.getCenterTile())
                    && getWonderThatWouldBenefitFromBeingSpedUp(city) != null
        }.mapNotNull { city ->
            val path = unit.movement.getShortestPath(city.getCenterTile())
            if (path.any() && path.size <= 5) city to path.size else null
        }.minByOrNull { it.second }
            ?.first ?: return false

        if (unit.currentTile == nearbyCityWithAvailableWonders.getCenterTile()) {
            val wonderToHurry =
                    getWonderThatWouldBenefitFromBeingSpedUp(nearbyCityWithAvailableWonders)!!
            nearbyCityWithAvailableWonders.cityConstructions.constructionQueue.add(
                0,
                wonderToHurry.name
            )
            return UnitActions.invokeUnitAction(unit, UnitActionType.HurryBuilding)
                || UnitActions.invokeUnitAction(unit, UnitActionType.HurryWonder)
        }

        // Walk towards the city.
        val tileBeforeMoving = unit.getTile()
        unit.movement.headTowards(nearbyCityWithAvailableWonders.getCenterTile())
        return tileBeforeMoving != unit.currentTile
    }

    @Readonly
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
        if (unit.movement.canUnitSwapTo(capitalTile))
            unit.movement.swapMoveToTile(capitalTile)
        if (unit.getTile() == capitalTile) {
            UnitActions.invokeUnitAction(unit, UnitActionType.AddInCapital)
        }
    }

}
