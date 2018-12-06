package com.unciv.logic.automation

import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.DiplomaticStatus
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.unit.UnitAction
import com.unciv.ui.worldscreen.unit.UnitActions

class UnitAutomation{

    fun automateUnitMoves(unit: MapUnit) {

        if (unit.name == "Settler") {
            return SpecificUnitAutomation().automateSettlerActions(unit)
        }

        if (unit.name == "Worker") {
            return WorkerAutomation(unit).automateWorkerAction()
        }

        if(unit.name=="Work Boats"){
            return SpecificUnitAutomation().automateWorkBoats(unit)
        }

        if(unit.name.startsWith("Great")
                && unit.name in GreatPersonManager().statToGreatPersonMapping.values){ // So "Great War Infantry" isn't caught here
            return SpecificUnitAutomation().automateGreatPerson(unit)// I don't know what to do with you yet.
        }

        val unitActions = UnitActions().getUnitActions(unit,UnCivGame.Current.worldScreen)
        var unitDistanceToTiles = unit.getDistanceToTiles()

        if(tryGoToRuin(unit,unitDistanceToTiles)){
            if(unit.currentMovement==0f) return
            unitDistanceToTiles = unit.getDistanceToTiles()
        }

        if (tryUpgradeUnit(unit, unitActions)) return

        // Accompany settlers
        if (tryAccompanySettler(unit)) return

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
        explore(unit,unitDistanceToTiles)
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

    fun containsAttackableEnemy(tile: TileInfo, unit: MapUnit): Boolean {
        if(unit.isEmbarked()){
            if(unit.type.isRanged()) return false
            if(tile.isWater()) return false // can't attack water units while embarked, only land
        }
        val tileCombatant = Battle(unit.civInfo.gameInfo).getMapCombatantOfTile(tile)
        if(tileCombatant==null) return false
        if(tileCombatant.getCivilization()==unit.civInfo ) return false
        if(!unit.civInfo.isAtWarWith(tileCombatant.getCivilization())) return false
        return true
    }

    class AttackableTile(val tileToAttackFrom:TileInfo, val tileToAttack:TileInfo)

    fun getAttackableEnemies(unit: MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>): ArrayList<AttackableTile> {
        val tilesWithEnemies = unit.civInfo.viewableTiles
                .filter { containsAttackableEnemy(it,unit) }

        val rangeOfAttack = unit.getRange()

        val attackableTiles = ArrayList<AttackableTile>()
        // The >0.1 (instead of >0) solves a bug where you've moved 2/3 road tiles,
        // you come to move a third (distance is less that remaining movements),
        // and then later we round it off to a whole.
        // So the poor unit thought it could attack from the tile, but when it comes to do so it has no movement points!
        // Silly floats, basically
        var tilesToAttackFrom = unitDistanceToTiles.asSequence()
                .filter { unit.currentMovement - it.value > 0.1 }
                .map { it.key }
                .filter { unit.canMoveTo(it) || it==unit.getTile() }
        if(unit.type.isLandUnit())
            tilesToAttackFrom = tilesToAttackFrom.filter { it.getBaseTerrain().type==TerrainType.Land }

        for(reachableTile in tilesToAttackFrom){  // tiles we'll still have energy after we reach there
            val tilesInAttackRange = if (unit.hasUnique("Indirect fire")) reachableTile.getTilesInDistance(rangeOfAttack)
                else reachableTile.getViewableTiles(rangeOfAttack)
            attackableTiles += tilesInAttackRange.asSequence().filter { it in tilesWithEnemies }
                    .map { AttackableTile(reachableTile,it) }
        }
        return attackableTiles
    }

    private fun tryAdvanceTowardsCloseEnemy(unit: MapUnit): Boolean {
        // this can be sped up if we check each layer separately
        var closeEnemies = unit.getTile().getTilesInDistance(5)
                .filter{ containsAttackableEnemy(it, unit) && unit.movementAlgs().canReach(it)}
        if(unit.type.isRanged())
            closeEnemies = closeEnemies.filterNot { it.isCityCenter() && it.getCity()!!.health==1 }

        val closestEnemy = closeEnemies.minBy { it.arialDistanceTo(unit.getTile()) }

        if (closestEnemy != null) {
            unit.movementAlgs().headTowards(closestEnemy)
            return true
        }
        return false
    }

    private fun tryAccompanySettler(unit: MapUnit): Boolean {
        val settlerToAccompany = unit.civInfo.getCivUnits()
                .firstOrNull { val tile = it.currentTile
                    it.name=="Settler" && tile.militaryUnit==null
                        && unit.canMoveTo(tile) && unit.movementAlgs().canReach(tile) }
        if(settlerToAccompany==null) return false
        unit.movementAlgs().headTowards(settlerToAccompany.currentTile)
        return true
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

        var enemyCities = unit.civInfo.gameInfo.civilizations
                .filter { unit.civInfo.isAtWarWith(it) }
                .flatMap { it.cities }.asSequence()
                .filter { it.location in unit.civInfo.exploredTiles }
                .map { it.getCenterTile() }.toList()

        if(unit.type.isRanged()) // ranged units don't harm capturable cities, waste of a turn
            enemyCities = enemyCities.filterNot { it.getCity()!!.health==1 }

        val closestReachableEnemyCity = enemyCities
                .asSequence()
                .filter { unit.movementAlgs().canReach(it) }
                .minBy { city ->
                    unit.civInfo.cities.asSequence().map { HexMath().getDistance(city.position, it.getCenterTile().position) }.min()!!
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

        if (unit.type.isMelee() && capturableCity!=null)
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
        if(unit.type.isMelee() || unit.type.isWaterUnit()) return false // don't garrison melee units, they're not that good at it
        val citiesWithoutGarrison = unit.civInfo.cities.filter {
            val centerTile = it.getCenterTile()
            centerTile.militaryUnit==null
                && unit.canMoveTo(centerTile)
        }

        fun isCityThatNeedsDefendingInWartime(city: CityInfo): Boolean {
            if (city.health < city.getMaxHealth()) return true // this city is under attack!
            for (enemyCivCity in unit.civInfo.diplomacy.values
                    .filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() }.flatMap { it.cities })
                if (city.getCenterTile().arialDistanceTo(enemyCivCity.getCenterTile()) <= 5) return true// this is an edge city that needs defending
            return false
        }

        if (!unit.civInfo.isAtWar()) {
            if (unit.getTile().isCityCenter()) return true // It's always good to have a unit in the city center, so if you haven't found anyone around to attack, forget it.

            val closestReachableCityWithNoGarrison = citiesWithoutGarrison.asSequence()
                    .sortedBy{ it.getCenterTile().arialDistanceTo(unit.currentTile) }
                    .firstOrNull { unit.movementAlgs().canReach(it.getCenterTile()) }
            if(closestReachableCityWithNoGarrison==null) return false // no
            unit.movementAlgs().headTowards(closestReachableCityWithNoGarrison.getCenterTile())
            return true
        } else {
            if (unit.getTile().isCityCenter() &&
                    isCityThatNeedsDefendingInWartime(unit.getTile().getCity()!!)) return true

            val closestReachableCityNeedsDefending = citiesWithoutGarrison.asSequence()
                    .filter { isCityThatNeedsDefendingInWartime(it) }
                    .sortedBy{ it.getCenterTile().arialDistanceTo(unit.currentTile) }
                    .firstOrNull { unit.movementAlgs().canReach(it.getCenterTile()) }
            if(closestReachableCityNeedsDefending==null) return false
            unit.movementAlgs().headTowards(closestReachableCityNeedsDefending.getCenterTile())
            return true
        }
    }

    fun tryGoToRuin(unit:MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>): Boolean {
        val tileWithRuin = unitDistanceToTiles.keys.firstOrNull{unit.canMoveTo(it) && it.improvement == "Ancient ruins"}
        if(tileWithRuin==null) return false
        unit.moveToTile(tileWithRuin)
        return true
    }

    internal fun explore(unit: MapUnit, unitDistanceToTiles: HashMap<TileInfo, Float>) {
        val distanceToTiles:HashMap<TileInfo, Float>
        if(tryGoToRuin(unit,unitDistanceToTiles))
        {
            if(unit.currentMovement==0f) return
            distanceToTiles = unit.getDistanceToTiles()
        }
        else distanceToTiles = unitDistanceToTiles

        for(tile in unit.currentTile.getTilesInDistance(5))
            if(unit.canMoveTo(tile) && tile.position !in unit.civInfo.exploredTiles
                    &&  unit.movementAlgs().canReach(tile)){
                unit.movementAlgs().headTowards(tile)
                return
            }


        val reachableTiles= distanceToTiles
                .filter { unit.canMoveTo(it.key) && unit.movementAlgs().canReach(it.key) }

        val reachableTilesMaxWalkingDistance = reachableTiles.filter { it.value == unit.currentMovement }
        if (reachableTilesMaxWalkingDistance.any()) unit.moveToTile(reachableTilesMaxWalkingDistance.toList().getRandom().first)
        else if (reachableTiles.any()) unit.moveToTile(reachableTiles.toList().getRandom().first)
    }

    fun automatedExplore(unit:MapUnit){
        if(tryGoToRuin(unit,unit.getDistanceToTiles()) && unit.currentMovement==0f) return

        for(i in 1..10){
            val unexploredTilesAtDistance = unit.getTile().getTilesAtDistance(i)
                    .filter { unit.canMoveTo(it)  && it.position !in unit.civInfo.exploredTiles
                            && unit.movementAlgs().canReach(it) }
            if(unexploredTilesAtDistance.isNotEmpty()){
                unit.movementAlgs().headTowards(unexploredTilesAtDistance.getRandom())
                return
            }
        }
    }

}

class SpecificUnitAutomation{

    private fun hasWorkableSeaResource(tileInfo: TileInfo, civInfo: CivilizationInfo): Boolean {
        return tileInfo.hasViewableResource(civInfo) && tileInfo.isWater() && tileInfo.improvement==null
    }

    fun automateWorkBoats(unit: MapUnit) {
        val seaResourcesInCities = unit.civInfo.cities.flatMap { it.getTilesInRange() }.asSequence()
                .filter { hasWorkableSeaResource(it, unit.civInfo) && (unit.canMoveTo(it) || unit.currentTile == it) }
        val closestReachableResource = seaResourcesInCities.sortedBy { it.arialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movementAlgs().canReach(it) }

        if (closestReachableResource != null) {
            unit.movementAlgs().headTowards(closestReachableResource)
            if (unit.currentMovement > 0 && unit.currentTile == closestReachableResource) {
                val createImprovementAction = UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen)
                        .firstOrNull { it.name.startsWith("Create") } // could be either fishing boats or oil well
                if (createImprovementAction != null)
                    return createImprovementAction.action() // unit is already gone, can't "explore"
            }
        }
        else UnitAutomation().explore(unit, unit.getDistanceToTiles())
    }


    fun rankTileAsCityCenter(tileInfo: TileInfo, nearbyTileRankings: Map<TileInfo, Float>): Float {
        val bestTilesFromOuterLayer = tileInfo.getTilesAtDistance(2)
                .asSequence()
                .sortedByDescending { nearbyTileRankings[it] }.take(2)
                .toList()
        val top5Tiles = tileInfo.neighbors.union(bestTilesFromOuterLayer)
                .asSequence()
                .sortedByDescending { nearbyTileRankings[it] }
                .take(5)
                .toList()
        var rank =  top5Tiles.asSequence().map { nearbyTileRankings[it]!! }.sum()
        if(tileInfo.neighbors.any{it.baseTerrain == "Coast"}) rank += 5
        return rank
    }


    fun automateSettlerActions(unit: MapUnit) {
        if(unit.getTile().militaryUnit==null) return // Don't move until you're accompanied by a military unit

        val tilesNearCities = unit.civInfo.gameInfo.civilizations.flatMap { it.cities }
                .flatMap { it.getCenterTile().getTilesInDistance(3) }.toHashSet()

        // This is to improve performance - instead of ranking each tile in the area up to 19 times, do it once.
        val nearbyTileRankings = unit.getTile().getTilesInDistance(7)
                .associateBy ( {it},{ Automation().rankTile(it,unit.civInfo) })

        val possibleCityLocations = unit.getTile().getTilesInDistance(5)
                .filter { (unit.canMoveTo(it) || unit.currentTile==it) && it !in tilesNearCities && it.isLand() }

        val bestCityLocation: TileInfo? = possibleCityLocations
                .asSequence()
                .sortedByDescending { rankTileAsCityCenter(it, nearbyTileRankings) }
                .firstOrNull { unit.movementAlgs().canReach(it) }

        if(bestCityLocation==null) // We got a badass over here, all tiles within 5 are taken? Screw it, random walk.
            return UnitAutomation().explore(unit, unit.getDistanceToTiles())

        if(bestCityLocation.getTilesInDistance(3).any { it.isCityCenter() })
            throw Exception("City within distance")

        if (unit.getTile() == bestCityLocation)
            UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen).first { it.name == "Found city" }.action()
        else {
            unit.movementAlgs().headTowards(bestCityLocation)
            if (unit.currentMovement > 0 && unit.getTile() == bestCityLocation)
                UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen).first { it.name == "Found city" }.action()
        }
    }

    fun automateGreatPerson(unit: MapUnit) {
        val relatedStat = GreatPersonManager().statToGreatPersonMapping.entries.first { it.value==unit.name }.key

        val citiesByStatBoost = unit.civInfo.cities.sortedByDescending{
            val stats = Stats()
            for (bonus in it.cityStats.statPercentBonusList.values) stats.add(bonus)
            stats.toHashMap()[relatedStat]!!
        }
        for(city in citiesByStatBoost){
            val pathToCity =unit.movementAlgs().getShortestPath(city.getCenterTile())
            if(pathToCity.isEmpty()) continue
            if(pathToCity.size>2){
                unit.movementAlgs().headTowards(city.getCenterTile())
                return
            }

            // if we got here, we're pretty close, start looking!
            val tiles = city.getTiles().asSequence()
                    .filter { (unit.canMoveTo(it) || unit.currentTile==it)
                            && it.isLand()
                            && !it.isCityCenter()
                            && it.resource==null }
                    .sortedByDescending { Automation().rankTile(it,unit.civInfo) }.toList()
            val chosenTile = tiles.firstOrNull { unit.movementAlgs().canReach(it) }
            if(chosenTile==null) continue // to another city

            unit.movementAlgs().headTowards(chosenTile)
            if(unit.currentTile==chosenTile && unit.currentMovement>0)
                UnitActions().getUnitActions(unit,UnCivGame.Current.worldScreen)
                        .first { it.name.startsWith("Create") }.action()
            return
        }

    }

}