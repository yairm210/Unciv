package com.unciv.logic.automation.unit

import com.unciv.view.getAirUnitMovementRange
import com.unciv.view.hasMovement
import com.unciv.view.isAirUnit
import com.unciv.logic.automation.civilization.ThreatManager
import com.unciv.logic.automation.civilization.AutomationCache
import com.unciv.view.isAtWar
import com.unciv.view.ForeignCivView
import com.unciv.view.getNukeBlastRadius
import com.unciv.view.isCivilian
import com.unciv.view.isMilitary
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

object AirUnitAutomation {

    fun automateFighter(unitView: MapUnitView, cache: AutomationCache) {
        if (unitView.unitHealth < 75) return // Wait and heal

        val tilesWithEnemyUnitsInRange = ThreatManager.getTilesWithEnemyUnitsInDistance(unitView.civ(), cache, unitView.getTile(), unitView.getRange())
        // TODO: Optimize [friendlyAirUnitsInRange] by creating an alternate [ThreatManager.getTilesWithEnemyUnitsInDistance] that handles only friendly units
        val friendlyAirUnitsInRange = unitView.getTile().getExploredTilesInDistance(unitView.getRange())
            .flatMap { it.getVisibleUnits() }.filter { it.isAirUnit() && it.civ() == unitView.civ() }.toList()
        // Find all visible enemy air units
        val enemyAirUnitsInRange = tilesWithEnemyUnitsInRange
            .flatMap { it.getVisibleUnits() }.filter { it.isAirUnit() && it.civ().isAtWarWith(unitView.civ()) }
        val enemyFighters = enemyAirUnitsInRange.size / 2 // Assume half the planes are fighters
        val friendlyUnusedFighterCount = friendlyAirUnitsInRange.count { it.canAttack() }
        val friendlyUsedFighterCount = friendlyAirUnitsInRange.count { !it.canAttack() }

        // We need to be on standby in case they attack
        if (friendlyUnusedFighterCount < enemyFighters) return

        if (friendlyUsedFighterCount <= enemyFighters) {
            @Readonly fun airSweepDamagePercentBonus(): Int =
                unitView.getMatchingUniques(UniqueType.StrengthWhenAirsweep).sumOf { it.params[0].toInt() }

            // If we are outnumbered, don't heal after attacking and don't have an Air Sweep bonus
            // Then we shouldn't speed the air battle by killing our fighters, instead, focus on defending
            if (friendlyUsedFighterCount + friendlyUnusedFighterCount < enemyFighters
                && !unitView.hasUnique(UniqueType.HealsEvenAfterAction)
                && airSweepDamagePercentBonus() <= 0) {
                return
            } else {
                if (tryAirSweep(unitView, tilesWithEnemyUnitsInRange)) return
            }
        }

        if (unitView.unitHealth < 80) {
            return // Wait and heal up, no point in moving closer to battle if we aren't healed
        }

        if (BattleHelper.tryAttackNearbyEnemy(unitView)) return

        if (unitView.cannotMove()) return // from here on it's all "try to move somewhere else"

        if (tryRelocateToCitiesWithEnemyNearBy(unitView)) return

        val pathsToCities = getAerialPathsToCities(unitView)
        if (pathsToCities.isEmpty()) return // can't actually move anywhere else

        val citiesByNearbyAirUnits = pathsToCities.keys
            .groupBy { key ->
                key.getExploredTilesInDistance(unitView.getAirUnitMovementRange()!!).count { tile ->
                    val firstAirUnit = tile.getVisibleUnits().firstOrNull { it.isAirUnit() }
                    firstAirUnit != null && firstAirUnit.civ().isAtWarWith(unitView.civ())
                }
            }

        if (citiesByNearbyAirUnits.keys.any { it != 0 }) {
            val citiesWithMostNeedOfAirUnits = citiesByNearbyAirUnits.maxByOrNull { it.key }!!.value
            //todo: maybe group by size and choose highest priority within the same size turns
            val chosenCity = citiesWithMostNeedOfAirUnits.minByOrNull { pathsToCities.getValue(it).size }!! // city with min path = least turns to get there
            val firstStepInPath = pathsToCities.getValue(chosenCity).first()
            unitView.tryMoveToTile(firstStepInPath)
            return
        }

        // no city needs fighters to defend, so let's attack stuff from the closest possible location
        tryMoveToCitiesToAerialAttackFrom(pathsToCities, unitView)
    }

    private fun tryAirSweep(unitView: MapUnitView, tilesWithEnemyUnitsInRange: List<TileView>): Boolean {
        val unitCiv = unitView.civ()
        val targetTile = tilesWithEnemyUnitsInRange.filter { tile ->
            tile.getVisibleUnits().any { it.civ().isAtWarWith(unitCiv) }
                || tile.getCity()?.owningCiv()?.isAtWarWith(unitCiv) == true
            }.minByOrNull { it.aerialDistanceTo(unitView.getTile()) } ?: return false
        unitView.tryAirSweep(targetTile)
        return !unitView.hasMovement()
    }

    fun automateBomber(unitView: MapUnitView) {
        if (unitView.unitHealth < 75) return // Wait and heal

        if (BattleHelper.tryAttackNearbyEnemy(unitView)) return

        if (unitView.unitHealth <= 90 || (unitView.unitHealth < 100 && !unitView.civ().isAtWar())) {
            return // Wait and heal
        }

        if (unitView.cannotMove()) return // from here on it's all "try to move somewhere else"

        if (tryRelocateToCitiesWithEnemyNearBy(unitView)) return

        val pathsToCities = getAerialPathsToCities(unitView)
        if (pathsToCities.isEmpty()) return // can't actually move anywhere else
        tryMoveToCitiesToAerialAttackFrom(pathsToCities, unitView)
    }

    private fun tryMoveToCitiesToAerialAttackFrom(pathsToCities: Map<TileView, List<TileView>>, airUnitView: MapUnitView) {
        val citiesThatCanAttackFrom = pathsToCities.keys
            .filter { destinationCity ->
                destinationCity != airUnitView.getTile()
                    && destinationCity.getExploredTilesInDistance(airUnitView.getRange())
                    .any { airUnitView.containsAttackableEnemy(it) }
            }
        if (citiesThatCanAttackFrom.isEmpty()) return

        //todo: this logic looks similar to some parts of automateFighter, maybe pull out common code
        //todo: maybe group by size and choose highest priority within the same size turns
        val closestCityThatCanAttackFrom =
            citiesThatCanAttackFrom.minByOrNull { pathsToCities[it]!!.size }!!
        val firstStepInPath = pathsToCities[closestCityThatCanAttackFrom]!!.first()
        airUnitView.tryMoveToTile(firstStepInPath)
    }

    fun automateNukes(unitView: MapUnitView) {
        if (!unitView.civ().isAtWar()) return
        // We should *Almost* never want to nuke our own city, so don't consider it
        if (unitView.isAirUnit()) {
            val tilesInRange = (2..unitView.getRange()).asSequence()
                .flatMap { unitView.getTile().getExploredTilesAtDistance(it) }
            val highestTileNukeValue = tilesInRange.map { it to getNukeLocationValue(unitView, it) }
                .maxByOrNull { it.second }
            if (highestTileNukeValue != null && highestTileNukeValue.second > 0)
                unitView.tryNuke(highestTileNukeValue.first)

            tryRelocateMissileToNearbyAttackableCities(unitView)
        } else {
            val attackableTiles = unitView.getAttackableEnemies()
            val highestTileNukeValue = attackableTiles.map { it to getNukeLocationValue(unitView, it.getTileToAttack()) }
                .maxByOrNull { it.second }
            if (highestTileNukeValue != null && highestTileNukeValue.second > 0) {
                val attackableTile = highestTileNukeValue.first
                if (unitView.tryMovePreparingAttack(attackableTile, tryHealPillage = true))
                    unitView.attackOrNuke(attackableTile)
            }
            HeadTowardsEnemyCityAutomation.tryHeadTowardsEnemyCity(unitView)
        }
    }

    /**
     * Ranks the tile to nuke based off of all tiles in it's blast radius
     * By default the value is -500 to prevent inefficient nuking.
     */
    @Readonly
    private fun getNukeLocationValue(nukeView: MapUnitView, tile: TileView): Int {
        val civ = nukeView.civ()
        if (!nukeView.mayUseNuke(tile)) return Int.MIN_VALUE
        val blastRadius = nukeView.getNukeBlastRadius()
        val tilesInBlastRadius = tile.getExploredTilesInDistance(blastRadius).toList()
        val civsInBlastRadius = tilesInBlastRadius.mapNotNull { it.getOwner() } +
            tilesInBlastRadius.mapNotNull { it.getVisibleUnits().firstOrNull()?.civ() }

        // Don't nuke if it means we will be declaring war on someone!
        if (civsInBlastRadius.any { it != civ && !it.isAtWarWith(civ) }) return -100000
        // If there are no enemies to hit, don't nuke
        if (!civsInBlastRadius.any { it.isAtWarWith(civ) }) return -100000

        // Launching a Nuke uses resources, therefore don't launch it by default
        var explosionValue = -500

        // Returns either ourValue or thierValue depending on if the input Civ matches the Nuke's Civ
        @Pure
        fun evaluateCivValue(targetCiv: ForeignCivView, ourValue: Int, theirValue: Int): Int {
            if (targetCiv == civ) // We are nuking something that we own!
                return ourValue
            return theirValue // We are nuking an enemy!
        }
        for (targetTile in tilesInBlastRadius) {
            // We can only account for visible units
            if (targetTile.isVisible()) {
                for (targetUnit in targetTile.getVisibleUnits()) {
                    // If we are nuking a unit at ground zero, it is more likely to be destroyed
                    val tileExplosionValue = if (targetTile == tile) 80 else 50

                    if (targetUnit.isMilitary()) {
                        explosionValue += if (targetTile == tile) evaluateCivValue(targetUnit.civ(), -200, tileExplosionValue)
                        else evaluateCivValue(targetUnit.civ(), -150, 50)
                    } else if (targetUnit.isCivilian()) {
                        explosionValue += evaluateCivValue(targetUnit.civ(), -100, tileExplosionValue / 2)
                    }
                }
            }
            // Never nuke our own Civ, don't nuke single enemy civs as well
            val city = targetTile.getCity()
            val owningCity = targetTile.owningCity()
            if (targetTile.isCityCenter()
                && !(city!!.getHealth() <= 50
                    && targetTile.getNeighbors().any { it.militaryUnit?.civ() == civ })) // Prefer not to nuke cities that we are about to take
                explosionValue += evaluateCivValue(city.owningCiv(), -100000, 250)
            else if (owningCity != null) {
                val owningCiv = owningCity.owningCiv()
                // If there is a tile to add fallout to there is a 50% chance it will get fallout
                if (!(targetTile.isWater || targetTile.isImpassible() || WorkerAutomation.hasFalloutEquivalent(targetTile)))
                    explosionValue += evaluateCivValue(owningCiv, -40, 10)
                // If there is an improvment to pillage
                if (targetTile.improvement != null && !targetTile.improvementIsPillaged)
                    explosionValue += evaluateCivValue(owningCiv, -40, 20)
            }
            // If the value is too low end the search early
            if (explosionValue < -1000) return explosionValue
        }
        return explosionValue
    }

    // This really needs to be changed, to have better targeting for missiles
    fun automateMissile(unitView: MapUnitView) {
        if (BattleHelper.tryAttackNearbyEnemy(unitView)) return
        tryRelocateMissileToNearbyAttackableCities(unitView)
    }

    private fun tryRelocateMissileToNearbyAttackableCities(unitView: MapUnitView) {
        val tilesInRange = unitView.getTile().getExploredTilesInDistance(unitView.getRange())
        val immediatelyReachableCities = tilesInRange
            .filter { unitView.canMoveTo(it) }

        for (city in immediatelyReachableCities) if (city.getExploredTilesInDistance(unitView.getRange())
                .any { it.isCityCenter() && it.getOwner()?.isAtWarWith(unitView.civ()) == true }
        ) {
            unitView.tryMoveToTile(city)
            return
        }

        val pathsToCities = getAerialPathsToCities(unitView)
        if (pathsToCities.isEmpty()) return // can't actually move anywhere else
        tryMoveToCitiesToAerialAttackFrom(pathsToCities, unitView)
    }

    private fun tryRelocateToCitiesWithEnemyNearBy(unitView: MapUnitView): Boolean {
        val movementRange = unitView.getAirUnitMovementRange() ?: return false
        val immediatelyReachableCitiesAndCarriers = unitView.getTile()
            .getExploredTilesInDistance(movementRange).filter { unitView.canMoveTo(it) }

        for (city in immediatelyReachableCitiesAndCarriers) {
            if (city.getExploredTilesInDistance(unitView.getRange())
                    .any { it.isVisible() && unitView.containsAttackableEnemy(it) }) {
                unitView.tryMoveToTile(city)
                return true
            }
        }
        return false
    }

    /** Aerial equivalent of [UnitMovement.getMovementToTilesAtPosition]: for each tile the air unit can reach by hopping, the path of tiles to it */
    private fun getAerialPathsToCities(unitView: MapUnitView): Map<TileView, List<TileView>> {
        var tilesToCheck = ArrayList<TileView>()
        /** each tile reached points to its parent tile, where we got to it from */
        val tilesReached = HashMap<TileView, TileView>()

        val startingTile = unitView.getTile()
        tilesToCheck.add(startingTile)
        tilesReached[startingTile] = startingTile

        while (tilesToCheck.isNotEmpty()) {
            val newTilesToCheck = ArrayList<TileView>()
            for (currentTileToCheck in tilesToCheck) {
                for (reachableTile in currentTileToCheck.getExploredTilesInDistance(unitView.getRange()).filter { unitView.canMoveTo(it) }) {
                    if (!tilesReached.containsKey(reachableTile)) {
                        tilesReached[reachableTile] = currentTileToCheck
                        newTilesToCheck.add(reachableTile)
                    }
                }
            }
            tilesToCheck = newTilesToCheck
        }

        val pathsToCities = HashMap<TileView, List<TileView>>()

        for (city in tilesReached.keys) {
            val path = ArrayList<TileView>()
            var currentCity = city
            while (currentCity != startingTile) { // we don't add the "starting tile" to the arraylist
                path.add(currentCity)
                currentCity = tilesReached[currentCity]!! // go to parent
            }
            path.reverse()
            pathsToCities[city] = path
        }

        pathsToCities.remove(startingTile)
        return pathsToCities
    }
}
