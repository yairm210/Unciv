package com.unciv.logic.automation

import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.DiplomaticStatus
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.unit.UnitAction
import com.unciv.ui.worldscreen.unit.UnitActions

class UnitAutomation{

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

        if (tryUpgradeUnit(unit, unitActions)) return

        // Accompany settlers
        val unitDistanceToTiles = unit.getDistanceToTiles()
        if (tryAccompanySettler(unit,unitDistanceToTiles)) return

        if (unit.health < 50) {
            healUnit(unit,unitDistanceToTiles)
            return
        } // do nothing but heal

        // if there is an attackable unit in the vicinity, attack!
        if (tryAttackNearbyEnemy(unit,unitDistanceToTiles)) return

        if (tryGarrisoningUnit(unit)) return

        if (unit.health < 80) {
            healUnit(unit, unitDistanceToTiles)
            return
        } // do nothing but heal until 80 health

        // find the closest enemy unit that we know of within 5 spaces and advance towards it
        if (tryAdvanceTowardsCloseEnemy(unit)) return

        if (unit.health < 100) {
            healUnit(unit, unitDistanceToTiles)
            return
        }

        // Focus all units without a specific target on the enemy city closest to one of our cities
        if (tryHeadTowardsEnemyCity(unit)) return

        // else, go to a random space
        randomWalk(unit,unitDistanceToTiles)
        // if both failed, then... there aren't any reachable tiles. Which is possible.
    }

    fun rankTileForHealing(tileInfo: TileInfo, unit: MapUnit): Int {
        val tileOwner = tileInfo.getOwner()
        when{
            tileInfo.isCityCenter() -> return 3
            tileOwner!=null && !unit.civInfo.isAtWarWith(tileOwner)-> return 2
            tileOwner==null -> return 1
            else -> return 0
        }
    }

    fun healUnit(unit: MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>) {
        val tilesInDistance = unitDistanceToTiles.keys.filter { unit.canMoveTo(it) }
        val unitTile = unit.getTile()

        val tilesByHealingRate = tilesInDistance.groupBy { rankTileForHealing(it,unit) }
        if(tilesByHealingRate.isEmpty()) return
        val bestTilesForHealing = tilesByHealingRate.maxBy { it.key }!!.value
        // within the tiles with best healing rate, we'll prefer one which has defensive bonuses
        val bestTileForHealing = bestTilesForHealing.maxBy { it.getDefensiveBonus() }!!
        if(unitTile!=bestTileForHealing && rankTileForHealing(bestTileForHealing,unit)>rankTileForHealing(unitTile,unit))
            unit.moveToTile(bestTileForHealing)
        if(unit.currentMovement>0 && !unit.hasUnique("No defensive terrain bonus") && !unit.isFortified() ){
            unit.action="Fortify 0"
        }
    }

    fun containsAttackableEnemy(tile: TileInfo, civInfo: CivilizationInfo): Boolean {
        val tileCombatant = Battle(civInfo.gameInfo).getMapCombatantOfTile(tile)
        if(tileCombatant==null) return false
        return tileCombatant.getCivilization()!=civInfo && civInfo.isAtWarWith(tileCombatant.getCivilization())
    }

    class AttackableTile(val tileToAttackFrom:TileInfo, val tileToAttack:TileInfo)

    fun getAttackableEnemies(unit: MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>): ArrayList<AttackableTile> {
        val tilesWithEnemies = unit.civInfo.getViewableTiles()
                .filter { containsAttackableEnemy(it,unit.civInfo) }

        val rangeOfAttack = unit.getRange()

        val attackableTiles = ArrayList<AttackableTile>()
        // The >0.1 (instead of >0) solves a bug where you've moved 2/3 road tiles,
        // you come to move a third (distance is less that remaining movements),
        // and then later we round it off to a whole.
        // So the poor unit thought it could attack from the tile, but when it comes to do so it has no movement points!
        // Silly floats, basically
        val tilesToAttackFrom = unitDistanceToTiles.filter { unit.currentMovement - it.value > 0.1 }
                .map { it.key }
                .filter { unit.canMoveTo(it) || it==unit.getTile() }
        for(reachableTile in tilesToAttackFrom){  // tiles we'll still have energy after we reach there
            val tilesInAttackRange = if (unit.hasUnique("Indirect fire")) reachableTile.getTilesInDistance(rangeOfAttack)
                else reachableTile.getViewableTiles(rangeOfAttack)
            attackableTiles += tilesInAttackRange.filter { it in tilesWithEnemies }
                    .map { AttackableTile(reachableTile,it) }
        }
        return attackableTiles
    }

    private fun tryAdvanceTowardsCloseEnemy(unit: MapUnit): Boolean {
        // this can be sped up if we check each layer separately
        var closeEnemies = unit.getTile().getTilesInDistance(5)
                .filter{ containsAttackableEnemy(it, unit.civInfo) && unit.movementAlgs().canReach(it)}
        if(unit.baseUnit().unitType.isRanged())
            closeEnemies = closeEnemies.filterNot { it.isCityCenter() && it.getCity()!!.health==1 }

        val closestEnemy = closeEnemies.minBy { it.arialDistanceTo(unit.getTile()) }

        if (closestEnemy != null) {
            unit.movementAlgs().headTowards(closestEnemy)
            return true
        }
        return false
    }

    private fun tryAccompanySettler(unit: MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>): Boolean {
        val closeTileWithSettler = unitDistanceToTiles.keys.firstOrNull {
            it.civilianUnit != null && it.civilianUnit!!.name == "Settler"
        }
        if (closeTileWithSettler != null && unit.canMoveTo(closeTileWithSettler)) {
            unit.movementAlgs().headTowards(closeTileWithSettler)
            return true
        }
        return false
    }

    private fun tryUpgradeUnit(unit: MapUnit, unitActions: List<UnitAction>): Boolean {
        if (unit.baseUnit().upgradesTo != null) {
            val upgradedUnit = GameBasics.Units[unit.baseUnit().upgradesTo!!]!!
            if (upgradedUnit.isBuildable(unit.civInfo)) {
                val goldCostOfUpgrade = (upgradedUnit.cost - unit.baseUnit().cost) * 2 + 10
                val upgradeAction = unitActions.firstOrNull { it.name.startsWith("Upgrade to") }
                if (upgradeAction != null && unit.civInfo.gold > goldCostOfUpgrade) {
                    upgradeAction.action()
                    return true
                }
            }
        }
        return false
    }

    private fun tryHeadTowardsEnemyCity(unit: MapUnit): Boolean {
        if(unit.civInfo.cities.isEmpty()) return false
        var enemyCities = unit.civInfo.gameInfo.civilizations.filter { unit.civInfo.isAtWarWith(it) }
                .flatMap { it.cities }.filter { it.location in unit.civInfo.exploredTiles }.map { it.getCenterTile() }
        if(unit.baseUnit().unitType.isRanged())
            enemyCities = enemyCities.filterNot { it.getCity()!!.health==1 }

        val closestReachableEnemyCity = enemyCities
                .filter { unit.movementAlgs().canReach(it) }
                .minBy { city ->
                    unit.civInfo.cities.map { HexMath().getDistance(city.position, it.getCenterTile().position) }.min()!!
                }
        if (closestReachableEnemyCity != null) {
            unit.movementAlgs().headTowards(closestReachableEnemyCity)
            return true
        }
        return false
    }

    private fun tryAttackNearbyEnemy(unit: MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>): Boolean {
        val attackableEnemies = getAttackableEnemies(unit,unitDistanceToTiles)
                // Only take enemies we can fight without dying
                .filter {
                    BattleDamage().calculateDamageToAttacker(MapUnitCombatant(unit),
                            Battle(unit.civInfo.gameInfo).getMapCombatantOfTile(it.tileToAttack)!!) < unit.health
                }

        val cityTilesToAttack = attackableEnemies.filter { it.tileToAttack.isCityCenter() }
        val nonCityTilesToAttack = attackableEnemies.filter { !it.tileToAttack.isCityCenter() }

        var enemyTileToAttack: AttackableTile? = null
        val capturableCity = cityTilesToAttack.firstOrNull{it.tileToAttack.getCity()!!.health == 1}
        val cityWithHealthLeft = cityTilesToAttack.filter { it.tileToAttack.getCity()!!.health != 1 } // don't want ranged units to attack defeated cities
                .minBy { it.tileToAttack.getCity()!!.health  }

        if (unit.baseUnit().unitType.isMelee() && capturableCity!=null)
            enemyTileToAttack = capturableCity // enter it quickly, top priority!

        else if (nonCityTilesToAttack.isNotEmpty()) // second priority, units
            enemyTileToAttack = nonCityTilesToAttack.minBy { Battle(unit.civInfo.gameInfo).getMapCombatantOfTile(it.tileToAttack)!!.getHealth() }
        else if (cityWithHealthLeft!=null) enemyTileToAttack = cityWithHealthLeft// third priority, city

        if (enemyTileToAttack != null) {
            val enemy = Battle(unit.civInfo.gameInfo).getMapCombatantOfTile(enemyTileToAttack.tileToAttack)!!
            unit.moveToTile(enemyTileToAttack.tileToAttackFrom)
            val setupAction = UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen)
                    .firstOrNull { it.name == "Set up" }
            if (setupAction != null) setupAction.action()
            if (unit.currentMovement > 0) // This can be 0, if the set up action took away what action points we had left...
                Battle(unit.civInfo.gameInfo).attack(MapUnitCombatant(unit), enemy)
            return true
        }
        return false
    }

    private fun tryGarrisoningUnit(unit: MapUnit): Boolean {
        if(unit.baseUnit().unitType.isMelee()) return false // don't garrison melee units, they're not that good at it
        val reachableCitiesWithoutUnits = unit.civInfo.cities.filter {
            val centerTile = it.getCenterTile()
            unit.canMoveTo(centerTile)
                    && unit.movementAlgs().canReach(centerTile)
        }

        fun cityThatNeedsDefendingInWartime(city: CityInfo): Boolean {
            if (city.health < city.getMaxHealth()) return true // this city is under attack!
            for (enemyCivCity in unit.civInfo.diplomacy.values.filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() }.flatMap { it.cities })
                if (city.getCenterTile().arialDistanceTo(enemyCivCity.getCenterTile()) <= 5) return true// this is an edge city that needs defending
            return false
        }

        if (!unit.civInfo.isAtWar()) {
            if (unit.getTile().isCityCenter()) return true // It's always good to have a unit in the city center, so if you haven't found anyone around to attack, forget it.
            if (reachableCitiesWithoutUnits.isNotEmpty()) {
            }
        } else {
            if (unit.getTile().isCityCenter() &&
                    cityThatNeedsDefendingInWartime(unit.getTile().getCity()!!)) return true
            val citiesThatCanBeDefended = reachableCitiesWithoutUnits.filter { cityThatNeedsDefendingInWartime(it) }
            if (citiesThatCanBeDefended.isNotEmpty()) {
                val closestCityWithoutUnit = citiesThatCanBeDefended
                        .minBy { unit.movementAlgs().getShortestPath(it.getCenterTile()).size }!!
                unit.movementAlgs().headTowards(closestCityWithoutUnit.getCenterTile())
                return true
            }
        }
        return false
    }

    private fun randomWalk(unit: MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>) {
        val reachableTiles = unitDistanceToTiles
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
            randomWalk(unit, unit.getDistanceToTiles())
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