package com.unciv.logic.automation

import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.unit.UnitActions

class UnitAutomation{

    fun healUnit(unit:MapUnit) {
        // If we're low on health then heal
        // todo: go to a more defensible place if there is one
        val tilesInDistance = unit.getDistanceToTiles().keys
        val unitTile = unit.getTile()

        // Go to friendly tile if within distance - better healing!
        val friendlyTile = tilesInDistance.firstOrNull { it.getOwner()?.civName == unit.owner && unit.canMoveTo(it) }
        if (unitTile.getOwner()?.civName != unit.owner && friendlyTile != null) {
            unit.moveToTile(friendlyTile)
            return
        }

        // Or at least get out of enemy territory yaknow
        val neutralTile = tilesInDistance.firstOrNull { it.getOwner() == null && unit.canMoveTo(it)}
        if (unitTile.getOwner()?.civName != unit.owner && unitTile.getOwner() != null && neutralTile != null) {
            unit.moveToTile(neutralTile)
            return
        }
    }

    fun containsAttackableEnemy(tile: TileInfo, civInfo: CivilizationInfo): Boolean {
        val tileCombatant = Battle().getMapCombatantOfTile(tile)
        if(tileCombatant==null) return false
        return tileCombatant.getCivilization()!=civInfo
    }

    class AttackableTile(val tileToAttackFrom:TileInfo, val tileToAttack:TileInfo)

    fun getAttackableEnemies(unit: MapUnit): ArrayList<AttackableTile> {
        val tilesWithEnemies = unit.civInfo.getViewableTiles()
                .filter { containsAttackableEnemy(it,unit.civInfo) }

        val distanceToTiles = unit.getDistanceToTiles()
        val rangeOfAttack = if(MapUnitCombatant(unit).isMelee()) 1 else unit.getBaseUnit().range

        val attackableTiles = ArrayList<AttackableTile>()
        // The >0.1 (instead of >0) solves a bug where you've moved 2/3 road tiles,
        // you come to move a third (distance is less that remaining movements),
        // and then later we round it off to a whole.
        // So the poor unit thought it could attack from the tile, but when it comes to do so it has no moveement points!
        // Silly floats, basically
        val tilesToAttackFrom = distanceToTiles.filter { unit.currentMovement - it.value > 0.1 }
                .map { it.key }
                .filter { unit.canMoveTo(it) || it==unit.getTile() }
        for(reachableTile in tilesToAttackFrom){  // tiles we'll still have energy after we reach there
            attackableTiles += reachableTile.getTilesInDistance(rangeOfAttack).filter { it in tilesWithEnemies }
                    .map { AttackableTile(reachableTile,it) }
        }
        return attackableTiles
    }

    fun automateUnitMoves(unit: MapUnit) {

        if (unit.name == "Settler") {
            automateSettlerActions(unit)
            return
        }

        if (unit.name == "Worker") {
            WorkerAutomation(unit).automateWorkerAction()
            return
        }

        if(unit.name.startsWith("Great")) return // I don't know what to do with you yet.

        val unitActions = UnitActions().getUnitActions(unit,UnCivGame.Current.worldScreen)

        if(unit.getBaseUnit().upgradesTo!=null) {
            val upgradedUnit = GameBasics.Units[unit.getBaseUnit().upgradesTo!!]!!
            if (upgradedUnit.isBuildable(unit.civInfo)) {
                val goldCostOfUpgrade = (upgradedUnit.cost - unit.getBaseUnit().cost) * 2 + 10
                val upgradeAction = unitActions.firstOrNull { it.name.startsWith("Upgrade to") }
                if (upgradeAction != null && unit.civInfo.gold > goldCostOfUpgrade) {
                    upgradeAction.action()
                    return
                }
            }
        }

        // Accompany settlers
        val closeTileWithSettler = unit.getDistanceToTiles().keys.firstOrNull{
            it.civilianUnit!=null && it.civilianUnit!!.name=="Settler"}
        if(closeTileWithSettler != null && unit.canMoveTo(closeTileWithSettler)){
            unit.movementAlgs().headTowards(closeTileWithSettler)
            return
        }

        if (unit.health < 50) {
            healUnit(unit)
            return
        } // do nothing but heal

        // if there is an attackable unit in the vicinity, attack!
        val enemyTileToAttack = getAttackableEnemies(unit)
                // Only take enemies we can fight without dying
                .filter { BattleDamage().calculateDamageToAttacker(MapUnitCombatant(unit),
                        Battle().getMapCombatantOfTile(it.tileToAttack)!!) < unit.health }
                .firstOrNull()

        if (enemyTileToAttack != null) {
            val enemy = Battle().getMapCombatantOfTile(enemyTileToAttack.tileToAttack)!!
            unit.moveToTile(enemyTileToAttack.tileToAttackFrom)
            val setupAction = UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen)
                    .firstOrNull{ it.name == "Set up" }
            if(setupAction!=null) setupAction.action()
            if(unit.currentMovement>0) // This can be 0, if the set up action took away what action points we had left...
                Battle(unit.civInfo.gameInfo).attack(MapUnitCombatant(unit), enemy)
            return
        }

        if(unit.getTile().isCityCenter()) return // It's always good to have a unit in the city center, so if you havn't found annyonw aroud to attack, forget it.

        val reachableCitiesWithoutUnits = unit.civInfo.cities.filter {
            unit.canMoveTo(it.getCenterTile())
                && unit.movementAlgs().canReach(it.getCenterTile())  }
        if(reachableCitiesWithoutUnits.isNotEmpty()){
            val closestCityWithoutUnit = reachableCitiesWithoutUnits
                    .minBy { unit.movementAlgs().getShortestPath(it.getCenterTile()).size }!!
            unit.movementAlgs().headTowards(closestCityWithoutUnit.getCenterTile())
            return
        }

        if (unit.health < 80) {
            healUnit(unit)
            return
        } // do nothing but heal until 80 health


        // else, if there is a reachable spot from which we can attack this turn
        // (say we're an archer and there's a unit 3 tiles away), go there and attack
        // todo

        // else, find the closest enemy unit that we know of within 5 spaces and advance towards it
        val closestEnemy = unit.getTile().getTilesInDistance(5)
                .firstOrNull { containsAttackableEnemy(it,unit.civInfo) && unit.movementAlgs().canReach(it) }

        if (closestEnemy != null) {
            unit.movementAlgs().headTowards(closestEnemy)
            return
        }

        if (unit.health < 100) {
            healUnit(unit)
            return
        }

        // Focus all units without a specific target on the enemy city closest to one of our cities
        val enemyCities = unit.civInfo.exploredTiles.map { unit.civInfo.gameInfo.tileMap[it] }
                .filter { it.isCityCenter() && it.getOwner()!=unit.civInfo }
        if(enemyCities.isNotEmpty() && unit.civInfo.cities.isNotEmpty()) {
            val closestReachableEnemyCity = enemyCities
                    .filter { unit.movementAlgs().canReach(it) }
                    .minBy { city ->
                        unit.civInfo.cities.map { HexMath().getDistance(city.position, it.getCenterTile().position) }.min()!!
                    }
            if (closestReachableEnemyCity != null) {
                unit.movementAlgs().headTowards(closestReachableEnemyCity)
                return
            }
        }


        // else, go to a random space
        randomWalk(unit)
        // if both failed, then... there aren't any reachable tiles. Which is possible.
    }

    private fun randomWalk(unit: MapUnit) {
        val reachableTiles = unit.getDistanceToTiles()
                .filter { unit.canMoveTo(it.key) }
        val reachableTilesMaxWalkingDistance = reachableTiles.filter { it.value == unit.currentMovement }
        if (reachableTilesMaxWalkingDistance.any()) unit.moveToTile(reachableTilesMaxWalkingDistance.toList().getRandom().first)
        else if (reachableTiles.any()) unit.moveToTile(reachableTiles.toList().getRandom().first)
    }

    fun rankTileAsCityCenter(tileInfo: TileInfo, nearbyTileRankings: Map<TileInfo, Float>): Float {
        val bestTilesFromOuterLayer = tileInfo.tileMap.getTilesAtDistance(tileInfo.position,2)
                .sortedByDescending { nearbyTileRankings[it] }.take(2)
        val top5Tiles = tileInfo.neighbors.union(bestTilesFromOuterLayer)
                .sortedByDescending { nearbyTileRankings[it] }
                .take(5)
        return top5Tiles.map { nearbyTileRankings[it]!! }.sum()
    }

    private fun automateSettlerActions(unit: MapUnit) {
        if(unit.getTile().militaryUnit==null) return // Don;t move until you're accompanied by a military unit

        // find best city location within 5 tiles
        val tilesNearCities = unit.civInfo.gameInfo.civilizations.flatMap { it.cities }
                .flatMap { it.getCenterTile().getTilesInDistance(2) }

        // This is to improve performance - instead of ranking each tile in the area up to 19 times, do it once.
        val nearbyTileRankings = unit.getTile().getTilesInDistance(7)
                .associateBy ( {it},{ Automation().rankTile(it,unit.civInfo) })

        val possibleTiles =  unit.getTile().getTilesInDistance(5)
                .minus(tilesNearCities)

        val bestCityLocation: TileInfo? = possibleTiles
                .sortedByDescending { rankTileAsCityCenter(it, nearbyTileRankings) }
                .firstOrNull { unit.movementAlgs().canReach(it) }

        if(bestCityLocation==null) // We got a badass over here, all tiles within 5 are taken? Screw it, random walk.
        {
            randomWalk(unit)
            return
        }

        if (unit.getTile() == bestCityLocation) // already there!
            UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen).first { it.name == "Found city" }.action()
        else {
            unit.movementAlgs().headTowards(bestCityLocation)
            if (unit.currentMovement > 0 && unit.getTile() == bestCityLocation)
                UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen).first { it.name == "Found city" }.action()
        }
    }

}