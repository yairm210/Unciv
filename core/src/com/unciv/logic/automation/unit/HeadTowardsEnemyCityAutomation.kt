package com.unciv.logic.automation.unit

import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.PathsToTilesWithinTurn
import com.unciv.logic.map.tile.Tile

object HeadTowardsEnemyCityAutomation {

    /** @returns whether the unit has taken this action */
    fun tryHeadTowardsEnemyCity(unit: MapUnit): Boolean {
        if (unit.civ.cities.isEmpty()) return false

        // only focus on *attacking* 1 enemy at a time otherwise you'll lose on both fronts
        val closestReachableEnemyCity = getEnemyCitiesByPriority(unit)
            .firstOrNull { unit.movement.canReach(it.getCenterTile()) }
            ?: return false // No enemy city reachable

        return headTowardsEnemyCity(
            unit,
            closestReachableEnemyCity.getCenterTile(),
            // This should be cached after the `canReach` call above.
            unit.movement.getShortestPath(closestReachableEnemyCity.getCenterTile())
        )
    }

    private fun getEnemyCitiesByPriority(unit: MapUnit): Sequence<City> {
        val enemies = unit.civ.getKnownCivs()
            .filter { unit.civ.isAtWarWith(it) && it.cities.isNotEmpty() }

        val closestEnemyCity = enemies
            .mapNotNull { NextTurnAutomation.getClosestCities(unit.civ, it) }
            .minByOrNull { it.aerialDistance }?.city2
            ?: return emptySequence() // no attackable cities found

        // Our main attack target is the closest city, but we're fine with deviating from that a bit
        var enemyCitiesByPriority = closestEnemyCity.civ.cities
            .associateWith { it.getCenterTile().aerialDistanceTo(closestEnemyCity.getCenterTile()) }
            .asSequence().filterNot { it.value > 10 } // anything 10 tiles away from the target is irrelevant
            .sortedBy { it.value }.map { it.key } // sort the list by closeness to target - least is best!

        if (unit.baseUnit.isRanged()) // ranged units don't harm capturable cities, waste of a turn
            enemyCitiesByPriority = enemyCitiesByPriority.filterNot { it.health == 1 }

        return enemyCitiesByPriority
    }


    private const val maxDistanceFromCityToConsiderForLandingArea = 5
    private const val minDistanceFromCityToConsiderForLandingArea = 3

    /** @returns whether the unit has taken this action */
    fun headTowardsEnemyCity(
        unit: MapUnit,
        closestReachableEnemyCity: Tile,
        shortestPath: List<Tile>
    ): Boolean {
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()

        val unitRange = unit.getRange()
        if (unitRange > 2) { // long-ranged unit, should never be in a bombardable position
            return headTowardsEnemyCityLongRange(closestReachableEnemyCity, unitDistanceToTiles, unitRange, unit)
        }

        val nextTileInPath = shortestPath[0]

        // None of the stuff below is relevant if we're still quite far away from the city, so we
        // short-circuit here for performance reasons.
        if (unit.currentTile.aerialDistanceTo(closestReachableEnemyCity) > maxDistanceFromCityToConsiderForLandingArea
            // Even in the worst case of only being able to move 1 tile per turn, we would still
            // not overshoot.
            && shortestPath.size > minDistanceFromCityToConsiderForLandingArea ) {
            unit.movement.moveToTile(nextTileInPath)
            return true
        }

        val ourUnitsAroundEnemyCity = closestReachableEnemyCity.getTilesInDistance(6)
            .flatMap { it.getUnits() }
            .filter { it.isMilitary() && it.civ == unit.civ }

        val city = closestReachableEnemyCity.getCity()!!

        if (cannotTakeCitySoon(ourUnitsAroundEnemyCity, city)) {
            return headToLandingGrounds(closestReachableEnemyCity, unit)
        }

        unit.movement.moveToTile(nextTileInPath) // go for it!

        return true
    }

    /** Cannot take within 5 turns */
    private fun cannotTakeCitySoon(
        ourUnitsAroundEnemyCity: Sequence<MapUnit>,
        city: City
    ): Boolean {
        val cityCombatant = CityCombatant(city)
        val expectedDamagePerTurn = ourUnitsAroundEnemyCity
            .sumOf { BattleDamage.calculateDamageToDefender(MapUnitCombatant(it), cityCombatant) }

        val cityHealingPerTurn = 20
        return expectedDamagePerTurn < city.health && // Cannot take immediately
            (expectedDamagePerTurn <= cityHealingPerTurn // No lasting damage
                || city.health / (expectedDamagePerTurn - cityHealingPerTurn) > 5) // Can damage, but will take more than 5 turns
    }

    private fun headToLandingGrounds(closestReachableEnemyCity: Tile, unit: MapUnit): Boolean {
        // don't head straight to the city, try to head to landing grounds -
        // this is against tha AI's brilliant plan of having everyone embarked and attacking via sea when unnecessary.
        val tileToHeadTo = closestReachableEnemyCity.getTilesInDistanceRange(minDistanceFromCityToConsiderForLandingArea..maxDistanceFromCityToConsiderForLandingArea)
            .filter { it.isLand && unit.getDamageFromTerrain(it) <= 0 } // Don't head for hurty terrain
            .sortedBy { it.aerialDistanceTo(unit.currentTile) }
            .firstOrNull { (unit.movement.canMoveTo(it) || it == unit.currentTile) && unit.movement.canReach(it) }

        if (tileToHeadTo != null) { // no need to worry, keep going as the movement alg. says
            unit.movement.headTowards(tileToHeadTo)
        }
        return true
    }

    private fun headTowardsEnemyCityLongRange(
        closestReachableEnemyCity: Tile,
        unitDistanceToTiles: PathsToTilesWithinTurn,
        unitRange: Int,
        unit: MapUnit
    ): Boolean {
        val tilesInBombardRange = closestReachableEnemyCity.getTilesInDistance(2).toSet()
        val tileToMoveTo =
            unitDistanceToTiles.asSequence()
                .filter {
                    it.key.aerialDistanceTo(closestReachableEnemyCity) > unitRange 
                        && it.key !in tilesInBombardRange
                        && unit.getDamageFromTerrain(it.key) <= 0 // Don't set up on a mountain
                        // Avoid mountains in path because unitDistanceToTiles parameter doesn't exclude them due to getMovementToTilesAtPosition
                        && unit.movement.canMoveTo(it.value.tile)
                }.minByOrNull { it.value.tile.aerialDistanceTo(closestReachableEnemyCity) }?.key ?: return false // return false if no tile to move to

        // move into position far away enough that the bombard doesn't hurt
        unit.movement.headTowards(tileToMoveTo)
        return true
    }
}
