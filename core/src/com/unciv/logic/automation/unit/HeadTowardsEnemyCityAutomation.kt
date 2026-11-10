package com.unciv.logic.automation.unit

import com.unciv.logic.automation.Timers.Companion.timeThis
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.view.ForeignCityView
import com.unciv.view.ForeignMapUnitView
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import com.unciv.view.getCapital
import com.unciv.view.isMilitary
import yairm210.purity.annotations.Readonly

object HeadTowardsEnemyCityAutomation {

    /** @returns whether the unit has taken this action */
    fun tryHeadTowardsEnemyCity(unitView: MapUnitView): Boolean = timeThis("tryHeadTowardsEnemyCity") {
        if (unitView.civ().cities().isEmpty()) return false

        // Naval units cannot enter city centers — path to water tiles they can attack from instead
        if (unitView.getBaseUnit().isWaterUnit) return tryHeadTowardsEnemyNaval(unitView)

        // only focus on *attacking* 1 enemy at a time otherwise you'll lose on both fronts
        val closestReachableEnemyCity = getEnemyCitiesByPriority(unitView)
            .firstOrNull { unitView.canReach(it.getCenterTile()) }
            ?: return false // No enemy city reachable

        return headTowardsEnemyCity(
            unitView,
            closestReachableEnemyCity.getCenterTile(),
            // This should be cached after the `canReach` call above.
            unitView.getShortestPath(closestReachableEnemyCity.getCenterTile())
        )
    }

    /**
     * Naval AI: steam toward the nearest water tile from which we can attack an enemy city or military unit.
     * Picks cities and units by aerial distance so nearer fleets/cities win over distant capitals.
     * Station selection uses canMoveTo/canReach, so coastal-only ships never path through ocean they cannot enter.
     */
    private fun tryHeadTowardsEnemyNaval(unitView: MapUnitView): Boolean {
        if (unitView.hasUnique(UniqueType.CannotAttack)) return false

        val waterOnlyAttacks = unitView.getMatchingUniques(UniqueType.CanOnlyAttackTiles)
            .any { it.params[0] == "Water" }

        var bestDest: TileView? = null
        var bestAerial = Int.MAX_VALUE
        val unitTile = unitView.getTile()

        fun considerTarget(targetTile: TileView) {
            val station = findReachableNavalAttackStation(unitView, targetTile) ?: return
            val aerial = station.aerialDistanceTo(unitTile)
            if (aerial < bestAerial) {
                bestAerial = aerial
                bestDest = station
            }
        }

        if (!waterOnlyAttacks) {
            for (city in getEnemyCitiesByPriority(unitView).take(8)) {
                considerTarget(city.getCenterTile())
            }
        }

        val civ = unitView.civ()
        val enemyUnits = civ.getKnownCivs().asSequence()
            .filter { civ.isAtWarWith(it) }
            .flatMap { it.getCiv().units.getCivUnits().asSequence() }
            .map { unitView.gameView.getForeignMapUnitView(it) }
            .filter { it.isMilitary() }
            .sortedBy { it.getTile().aerialDistanceTo(unitTile) }
            .take(15)

        for (enemy in enemyUnits) {
            considerTarget(enemy.getTile())
        }

        val dest = bestDest ?: return false
        if (dest == unitTile) return true
        unitView.tryHeadTowards(dest)
        return true
    }

    /** Water tile this naval unit can stand on to attack [targetTile], preferring nearer stations. */
    @Readonly
    private fun findReachableNavalAttackStation(unitView: MapUnitView, targetTile: TileView): TileView? {
        val melee = unitView.getBaseUnit().isMelee()
        val range = if (melee) 1 else unitView.getRange().coerceAtLeast(1)
        val unitTile = unitView.getTile()
        val candidates = (if (melee) targetTile.getNeighbors() else targetTile.getExploredTilesInDistance(range))
            .filter { it.isWater && (unitView.canMoveTo(it) || it == unitTile) }
            .sortedBy { it.aerialDistanceTo(unitTile) }
            .take(8)
        return candidates.firstOrNull { unitView.canReach(it) }
    }

    @Readonly
    fun getEnemyCitiesByPriority(unitView: MapUnitView): Sequence<ForeignCityView> {
        val civ = unitView.civ()
        val enemies = civ.getKnownCivs()
            .filter { civ.isAtWarWith(it) && it.cities().isNotEmpty() }

        val capitalTile = civ.getCapital()?.getCenterTile()
        val closestEnemyCity = (if (capitalTile == null) null else enemies
            .mapNotNull { enemy -> enemy.cities().minByOrNull { it.getCenterTile().aerialDistanceTo(capitalTile) } }
            .minByOrNull { it.getCenterTile().aerialDistanceTo(capitalTile) })
            ?: return emptySequence() // no attackable cities found

        // Our main attack target is the closest city, but we're fine with deviating from that a bit
        var enemyCitiesByPriority = closestEnemyCity.owningCiv().cities()
            .associateWith { it.getCenterTile().aerialDistanceTo(closestEnemyCity.getCenterTile()) }
            .asSequence().filterNot { it.value > 10 } // anything 10 tiles away from the target is irrelevant
            .sortedBy { it.value }.map { it.key } // sort the list by closeness to target - least is best!

        if (unitView.getBaseUnit().isRanged()) // ranged units don't harm capturable cities, waste of a turn
            enemyCitiesByPriority = enemyCitiesByPriority.filterNot { it.getHealth() == 1 }

        return enemyCitiesByPriority
    }


    private const val maxDistanceFromCityToConsiderForLandingArea = 5
    private const val minDistanceFromCityToConsiderForLandingArea = 3

    /**
     * @return whether the unit has taken this action
     * @throws [NullPointerException] when [closestReachableEnemyCity] does not contain a city
     */
    fun headTowardsEnemyCity(
        unitView: MapUnitView,
        closestReachableEnemyCity: TileView,
        shortestPath: List<TileView>
    ): Boolean {
        val unitRange = unitView.getRange()
        val city = closestReachableEnemyCity.getCity()!!
        if (unitRange > city.getBombardRange()) { // long-ranged unit, should never be in a bombardable position
            return headTowardsEnemyCityLongRange(closestReachableEnemyCity, unitRange, unitView)
        }

        val nextTileInPath = shortestPath[0]

        // None of the stuff below is relevant if we're still quite far away from the city, so we
        // short-circuit here for performance reasons.
        if (unitView.getTile().aerialDistanceTo(closestReachableEnemyCity) > maxDistanceFromCityToConsiderForLandingArea
            // Even in the worst case of only being able to move 1 tile per turn, we would still
            // not overshoot.
            && shortestPath.size > minDistanceFromCityToConsiderForLandingArea ) {
            unitView.tryHeadTowards(nextTileInPath)
            return true
        }

        val ourUnitsAroundEnemyCity = closestReachableEnemyCity.getExploredTilesInDistance(6)
            .flatMap { it.getVisibleUnits() }
            .filter { it.isMilitary() && it.civ() == unitView.civ() }

        if (cannotTakeCitySoon(ourUnitsAroundEnemyCity, city)) {
            return headToLandingGrounds(closestReachableEnemyCity, unitView)
        }

        // A multi-turn path waypoint can be outside this turn's reachable tiles.
        unitView.tryHeadTowards(nextTileInPath) // resolve how far we can actually move

        return true
    }

    /** Cannot take within 5 turns */
    @Readonly
    private fun cannotTakeCitySoon(
        ourUnitsAroundEnemyCity: Sequence<ForeignMapUnitView>,
        city: ForeignCityView
    ): Boolean {
        val cityCombatant = city.asCombatant()
        val expectedDamagePerTurn = ourUnitsAroundEnemyCity
            .sumOf { it.asCombatant().calculateDamageToDefender(cityCombatant) }

        val cityHealingPerTurn = 20
        return expectedDamagePerTurn < city.getHealth() && // Cannot take immediately
            (expectedDamagePerTurn <= cityHealingPerTurn // No lasting damage
                || city.getHealth() / (expectedDamagePerTurn - cityHealingPerTurn) > 5) // Can damage, but will take more than 5 turns
    }

    private fun headToLandingGrounds(closestReachableEnemyCity: TileView, unitView: MapUnitView): Boolean {
        val unitTile = unitView.getTile()
        // don't head straight to the city, try to head to landing grounds -
        // this is against tha AI's brilliant plan of having everyone embarked and attacking via sea when unnecessary.
        val tileToHeadTo = (minDistanceFromCityToConsiderForLandingArea..maxDistanceFromCityToConsiderForLandingArea).asSequence()
            .flatMap { closestReachableEnemyCity.getExploredTilesAtDistance(it) }
            .filter { it.isLand && unitView.getDamageFromTerrain(it) <= 0 } // Don't head for hurty terrain
            .sortedBy { it.aerialDistanceTo(unitTile) }
            .firstOrNull { (unitView.canMoveTo(it) || it == unitTile) && unitView.canReach(it) }

        if (tileToHeadTo != null) { // no need to worry, keep going as the movement alg. says
            unitView.tryHeadTowards(tileToHeadTo)
        }
        return true
    }

    private fun headTowardsEnemyCityLongRange(
        closestReachableEnemyCity: TileView,
        unitRange: Int,
        unitView: MapUnitView
    ): Boolean {
        // If we're already in fire range but have no sight, hold position
        // If there IS sight, automateUnitMoves would order attacking instead of heading to city
        if (closestReachableEnemyCity.aerialDistanceTo(unitView.getTile()) <= unitRange)
            return true

        val tilesInBombardRange = closestReachableEnemyCity.getExploredTilesInDistance(2).toSet()
        val candidateTiles = unitView.getDistanceToTiles().asSequence().filter {
            it.key.aerialDistanceTo(closestReachableEnemyCity) >= unitRange
                && it.key !in tilesInBombardRange
                && unitView.getDamageFromTerrain(it.key) <= 0 // Don't set up on a mountain 
                // Avoid mountains in path because unitDistanceToTiles parameter doesn't exclude them due to getMovementToTilesAtPosition
                && unitView.canMoveTo(it.key)
        }

        // Sort by closest distance to target city, then by the least amount of moves needed to get into fire range
        val tileToMoveTo = candidateTiles.sortedWith(compareBy(
            { it.key.aerialDistanceTo(closestReachableEnemyCity) },
            { it.value }
        )).firstOrNull()

        // return false if no tile to move to
        if (tileToMoveTo == null) return false

        // move into position far away enough so that city bombard or enemy units don't hurt
        unitView.tryHeadTowards(tileToMoveTo.key)
        return true
    }
}
