package com.unciv.logic.automation

import com.unciv.Constants
import com.unciv.logic.battle.*
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.worldscreen.unit.UnitActions

object UnitAutomation {

    const val CLOSE_ENEMY_TILES_AWAY_LIMIT = 5
    const val CLOSE_ENEMY_TURNS_AWAY_LIMIT = 3f

    private fun isGoodTileToExplore(unit: MapUnit, tile: TileInfo): Boolean {
        return unit.movement.canMoveTo(tile)
                && (tile.getOwner() == null || !tile.getOwner()!!.isCityState())
                && tile.neighbors.any { it.position !in unit.civInfo.exploredTiles }
                && unit.movement.canReach(tile)
                && (!unit.civInfo.isCityState() || tile.neighbors.any { it.getOwner() == unit.civInfo }) // Don't want city-states exploring far outside their borders
    }

    internal fun tryExplore(unit: MapUnit): Boolean {
        if (tryGoToRuinAndEncampment(unit) && unit.currentMovement == 0f) return true

        val explorableTilesThisTurn =
                unit.movement.getDistanceToTiles().keys.filter { isGoodTileToExplore(unit, it) }
        if (explorableTilesThisTurn.any()) {
            val bestTile = explorableTilesThisTurn
                .sortedByDescending { it.getHeight() }  // secondary sort is by 'how far can you see'
                .maxByOrNull { it: TileInfo -> it.aerialDistanceTo(unit.currentTile) }!! // primary sort is by 'how far can you go'
            unit.movement.headTowards(bestTile)
            return true
        }

        // Nothing immediate, lets look further. Number increases exponentially with distance - at 10 this took a looong time
        for (tile in unit.currentTile.getTilesInDistance(5))
            if (isGoodTileToExplore(unit, tile)) {
                unit.movement.headTowards(tile)
                return true
            }
        return false
    }

    private fun tryGoToRuinAndEncampment(unit: MapUnit): Boolean {
        if (!unit.civInfo.isMajorCiv()) return false // barbs don't have anything to do in ruins
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val tileWithRuinOrEncampment = unitDistanceToTiles.keys
                .firstOrNull {
                    (it.improvement == Constants.ancientRuins || it.improvement == Constants.barbarianEncampment)
                            && unit.movement.canMoveTo(it)
                }
        if (tileWithRuinOrEncampment == null)
            return false
        unit.movement.moveToTile(tileWithRuinOrEncampment)
        return true
    }

    @JvmStatic
    fun wander(unit: MapUnit) {
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val reachableTiles = unitDistanceToTiles
                .filter { unit.movement.canMoveTo(it.key) && unit.movement.canReach(it.key) }

        val reachableTilesMaxWalkingDistance = reachableTiles
                .filter { it.value.totalDistance == unit.currentMovement }
        if (reachableTilesMaxWalkingDistance.any()) unit.movement.moveToTile(reachableTilesMaxWalkingDistance.toList().random().first)
        else if (reachableTiles.any()) unit.movement.moveToTile(reachableTiles.keys.random())
    }

    internal fun tryUpgradeUnit(unit: MapUnit): Boolean {
        if (unit.baseUnit.upgradesTo == null) return false
        val upgradedUnit = unit.getUnitToUpgradeTo()
        if (!upgradedUnit.isBuildable(unit.civInfo)) return false // for resource reasons, usually

        val upgradeAction = UnitActions.getUpgradeAction(unit)
        if (upgradeAction == null) return false

        upgradeAction.action?.invoke()
        return true
    }

    fun automateUnitMoves(unit: MapUnit) {
        if (unit.civInfo.isBarbarian())
            throw IllegalStateException("Barbarians is not allowed here.")

        if (unit.type.isCivilian()) {
            if (unit.hasUnique(Constants.settlerUnique))
                return SpecificUnitAutomation.automateSettlerActions(unit)

            // Constants.workerUnique deprecated since 3.15.5
            if (unit.hasUnique(Constants.canBuildImprovements) || unit.hasUnique(Constants.workerUnique))
                return WorkerAutomation(unit).automateWorkerAction()

            if (unit.name == "Work Boats") // This is really not modular
                return SpecificUnitAutomation.automateWorkBoats(unit)

            if (unit.hasUnique("Bonus for units in 2 tile radius 15%"))
                return SpecificUnitAutomation.automateGreatGeneral(unit)

            if (unit.hasUnique("Can construct []"))
                return SpecificUnitAutomation.automateImprovementPlacer(unit) // includes great people plus moddable units

            return // The AI doesn't know how to handle unknown civilian units
        }

        if (unit.type == UnitType.Fighter)
            return SpecificUnitAutomation.automateFighter(unit)

        if (unit.type == UnitType.Bomber)
            return SpecificUnitAutomation.automateBomber(unit)

        if (unit.type == UnitType.Missile || unit.type == UnitType.AtomicBomber)
            return SpecificUnitAutomation.automateMissile(unit)


        if (tryGoToRuinAndEncampment(unit)) {
            if (unit.currentMovement == 0f) return
        }

        if (tryUpgradeUnit(unit)) return

        // Accompany settlers
        if (tryAccompanySettlerOrGreatPerson(unit)) return

        if (tryHeadTowardsSiegedCity(unit)) return

        if (unit.health < 50 && tryHealUnit(unit)) return // do nothing but heal

        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit)) return

        // if there is an attackable unit in the vicinity, attack!
        if (BattleHelper.tryAttackNearbyEnemy(unit)) return

        if (tryTakeBackCapturedCity(unit)) return

        if (tryGarrisoningUnit(unit)) return

        if (unit.health < 80 && tryHealUnit(unit)) return

        // move towards the closest reasonably attackable enemy unit within 3 turns of movement (and 5 tiles range)
        if (tryAdvanceTowardsCloseEnemy(unit)) return

        if (unit.health < 100 && tryHealUnit(unit)) return

        // Focus all units without a specific target on the enemy city closest to one of our cities
        if (tryHeadTowardsEnemyCity(unit)) return

        if (tryHeadTowardsEncampment(unit)) return

        // else, try to go o unreached tiles
        if (tryExplore(unit)) return
    }

    private fun tryHeadTowardsEncampment(unit: MapUnit): Boolean {
        if (unit.type == UnitType.Missile) return false // don't use missiles against barbarians...
        val knownEncampments = unit.civInfo.gameInfo.tileMap.values.asSequence()
                .filter { it.improvement == Constants.barbarianEncampment && unit.civInfo.exploredTiles.contains(it.position) }
        val cities = unit.civInfo.cities
        val encampmentsCloseToCities = knownEncampments.filter { cities.any { city -> city.getCenterTile().aerialDistanceTo(it) < 6 } }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
        val encampmentToHeadTowards = encampmentsCloseToCities.firstOrNull { unit.movement.canReach(it) }
        if (encampmentToHeadTowards == null) {
            return false
        }
        unit.movement.headTowards(encampmentToHeadTowards)
        return true
    }

    fun tryHealUnit(unit: MapUnit): Boolean {
        if (unit.type.isRanged() && unit.hasUnique("Unit will heal every turn, even if it performs an action"))
            return false // will heal anyway, and attacks don't hurt

        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        if (unitDistanceToTiles.isEmpty()) return true // can't move, so...


        val currentUnitTile = unit.getTile()

        if (tryPillageImprovement(unit)) return true


        val tilesWithRangedEnemyUnits = unit.currentTile.getTilesInDistance(3)
                .flatMap { it.getUnits().filter { unit.civInfo.isAtWarWith(it.civInfo) } }

        val tilesInRangeOfAttack = tilesWithRangedEnemyUnits
                .flatMap { it.getTile().getTilesInDistance(it.getRange()) }

        val tilesWithinBomardmentRange = unit.currentTile.getTilesInDistance(3)
                .filter { it.isCityCenter() && it.getCity()!!.civInfo.isAtWarWith(unit.civInfo) }
                .flatMap { it.getTilesInDistance(it.getCity()!!.range) }

        val dangerousTiles = (tilesInRangeOfAttack + tilesWithinBomardmentRange).toHashSet()


        val viableTilesForHealing = unitDistanceToTiles.keys
                .filter { it !in dangerousTiles && unit.movement.canMoveTo(it) }
        val tilesByHealingRate = viableTilesForHealing.groupBy { unit.rankTileForHealing(it) }

        if (tilesByHealingRate.keys.none { it != 0 }) { // We can't heal here at all! We're probably embarked
            val reachableCityTile = unit.civInfo.cities.asSequence().map { it.getCenterTile() }
                    .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                    .firstOrNull { unit.movement.canReach(it) }
            if (reachableCityTile != null) unit.movement.headTowards(reachableCityTile)
            else wander(unit)
            return true
        }

        val bestTilesForHealing = tilesByHealingRate.maxByOrNull {  it.key }!!.value
        val bestTileForHealing = bestTilesForHealing.maxByOrNull {  it.getDefensiveBonus() }!!
        val bestTileForHealingRank = unit.rankTileForHealing(bestTileForHealing)

        if (currentUnitTile != bestTileForHealing
                && bestTileForHealingRank > unit.rankTileForHealing(currentUnitTile))
            unit.movement.moveToTile(bestTileForHealing)

        unit.fortifyIfCan()
        return true
    }

    fun tryPillageImprovement(unit: MapUnit): Boolean {
        if (unit.type.isCivilian()) return false
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val tilesThatCanWalkToAndThenPillage = unitDistanceToTiles
            .filter { it.value.totalDistance < unit.currentMovement }.keys
            .filter { unit.movement.canMoveTo(it) && UnitActions.canPillage(unit, it) }
            .filter { it.getOwner() != unit.civInfo }

        if (tilesThatCanWalkToAndThenPillage.isEmpty()) return false
        val tileToPillage = tilesThatCanWalkToAndThenPillage.maxByOrNull { it: TileInfo -> it.getDefensiveBonus() }!!
        if (unit.getTile() != tileToPillage)
            unit.movement.moveToTile(tileToPillage)

        UnitActions.getPillageAction(unit)?.action?.invoke()
        return true
    }

    fun getBombardTargets(city: CityInfo): Sequence<TileInfo> =
            city.getCenterTile().getTilesInDistance(city.range)
                    .filter { BattleHelper.containsAttackableEnemy(it, CityCombatant(city)) }

    /** Move towards the closest attackable enemy of the [unit].
     *
     *  Limited by [CLOSE_ENEMY_TURNS_AWAY_LIMIT] and [CLOSE_ENEMY_TILES_AWAY_LIMIT].
     *  Tiles attack from which would result in instant death of the [unit] are ignored. */
    private fun tryAdvanceTowardsCloseEnemy(unit: MapUnit): Boolean {
        // this can be sped up if we check each layer separately
        val unitDistanceToTiles = unit.movement.getDistanceToTilesWithinTurn(
                unit.getTile().position,
                unit.getMaxMovement() * CLOSE_ENEMY_TURNS_AWAY_LIMIT
        )
        var closeEnemies = BattleHelper.getAttackableEnemies(
                unit,
                unitDistanceToTiles,
                tilesToCheck = unit.getTile().getTilesInDistance(CLOSE_ENEMY_TILES_AWAY_LIMIT).toList()
        ).filter {
            // Ignore units that would 1-shot you if you attacked
            BattleDamage.calculateDamageToAttacker(MapUnitCombatant(unit),
                    it.tileToAttackFrom,
                    Battle.getMapCombatantOfTile(it.tileToAttack)!!) < unit.health
        }

        if (unit.type.isRanged())
            closeEnemies = closeEnemies.filterNot { it.tileToAttack.isCityCenter() && it.tileToAttack.getCity()!!.health == 1 }

        val closestEnemy = closeEnemies.minByOrNull { it.tileToAttack.aerialDistanceTo(unit.getTile()) }

        if (closestEnemy != null) {
            unit.movement.headTowards(closestEnemy.tileToAttackFrom)
            return true
        }
        return false
    }

    private fun tryAccompanySettlerOrGreatPerson(unit: MapUnit): Boolean {
        val settlerOrGreatPersonToAccompany = unit.civInfo.getCivUnits()
                .firstOrNull {
                    val tile = it.currentTile
                    it.type == UnitType.Civilian &&
                            (it.hasUnique(Constants.settlerUnique) || unit.isGreatPerson())
                            && tile.militaryUnit == null && unit.movement.canMoveTo(tile) && unit.movement.canReach(tile)
                }
        if (settlerOrGreatPersonToAccompany == null) return false
        unit.movement.headTowards(settlerOrGreatPersonToAccompany.currentTile)
        return true
    }

    private fun tryHeadTowardsSiegedCity(unit: MapUnit): Boolean {
        val siegedCities = unit.civInfo.cities
                .asSequence()
                .filter {
                    unit.civInfo == it.civInfo &&
                            it.health < it.getMaxHealth() * 0.75
                } //Weird health issues and making sure that not all forces move to good defenses

        val reachableTileNearSiegedCity = siegedCities
                .flatMap { it.getCenterTile().getTilesAtDistance(2) }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movement.canMoveTo(it) && unit.movement.canReach(it) }

        if (reachableTileNearSiegedCity != null) {
            unit.movement.headTowards(reachableTileNearSiegedCity)
            return true
        }
        return false
    }


    private fun tryHeadTowardsEnemyCity(unit: MapUnit): Boolean {
        if (unit.civInfo.cities.isEmpty()) return false

        var enemyCities = unit.civInfo.gameInfo.civilizations
                .filter { unit.civInfo.isAtWarWith(it) }
                .flatMap { it.cities }.asSequence()
                .filter { it.location in unit.civInfo.exploredTiles }

        if (unit.type.isRanged()) // ranged units don't harm capturable cities, waste of a turn
            enemyCities = enemyCities.filterNot { it.health == 1 }

        val closestReachableEnemyCity = enemyCities
                .asSequence().map { it.getCenterTile() }
                .sortedBy { cityCenterTile ->
                    // sort enemy cities by closeness to our cities, and only then choose the first reachable - checking canReach is comparatively very time-intensive!
                    unit.civInfo.cities.asSequence()
                        .map { cityCenterTile.aerialDistanceTo(it.getCenterTile()) }.minOrNull()!!
                }
                .firstOrNull { unit.movement.canReach(it) }

        if (closestReachableEnemyCity != null) {
            return headTowardsEnemyCity(unit, closestReachableEnemyCity)
        }
        return false
    }

    private fun headTowardsEnemyCity(unit: MapUnit, closestReachableEnemyCity: TileInfo): Boolean {
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val unitRange = unit.getRange()

        if (unitRange > 2) { // long-ranged unit, should never be in a bombardable position
            val tilesInBombardRange = closestReachableEnemyCity.getTilesInDistance(2).toSet()
            val tileToMoveTo =
                    unitDistanceToTiles.asSequence()
                            .filter {
                                it.key.aerialDistanceTo(closestReachableEnemyCity) <=
                                        unitRange && it.key !in tilesInBombardRange
                            }
                        .minByOrNull { it.value.totalDistance }?.key

            // move into position far away enough that the bombard doesn't hurt
            if (tileToMoveTo != null) {
                unit.movement.headTowards(tileToMoveTo)
                return true
            }
            return false // didn't move
        }

        val numberOfUnitsAroundCity = closestReachableEnemyCity.getTilesInDistance(4)
                .count { it.militaryUnit != null && it.militaryUnit!!.civInfo == unit.civInfo }

        if (numberOfUnitsAroundCity < 3) {
            // don't head straight to the city, try to head to landing grounds -
            // this is against tha AI's brilliant plan of having everyone embarked and attacking via sea when unnecessary.
            val tileToHeadTo = closestReachableEnemyCity.getTilesInDistanceRange(3..4)
                    .filter { it.isLand }
                    .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                    .firstOrNull { unit.movement.canReach(it) }

            if (tileToHeadTo != null) { // no need to worry, keep going as the movement alg. says
                unit.movement.headTowards(tileToHeadTo)
                return true
            }
        }

        unit.movement.headTowards(closestReachableEnemyCity) // go for it!

        return true
    }

    fun tryBombardEnemy(city: CityInfo): Boolean {
        if (!city.canBombard()) return false
        val enemy = chooseBombardTarget(city)
        if (enemy == null) return false
        Battle.attack(CityCombatant(city), enemy)
        return true
    }

    private fun chooseBombardTarget(city: CityInfo): ICombatant? {
        var targets = getBombardTargets(city).map { Battle.getMapCombatantOfTile(it)!! }
        if (targets.none()) return null

        val siegeUnits = targets
                .filter { it.getUnitType() == UnitType.Siege }
        if (siegeUnits.any()) targets = siegeUnits
        else {
            val rangedUnits = targets
                    .filter { it.getUnitType().isRanged() }
            if (rangedUnits.any()) targets = rangedUnits
        }
        return targets.minByOrNull { it: ICombatant -> it.getHealth() }
    }

    private fun tryTakeBackCapturedCity(unit: MapUnit): Boolean {
        var capturedCities = unit.civInfo.getKnownCivs().asSequence()
                .flatMap { it.cities.asSequence() }
                .filter {
                    unit.civInfo.isAtWarWith(it.civInfo) &&
                            unit.civInfo.civName == it.foundingCiv &&
                            it.isInResistance() &&
                            it.health < it.getMaxHealth()
                } //Most likely just been captured


        if (unit.type.isRanged()) // ranged units don't harm capturable cities, waste of a turn
            capturedCities = capturedCities.filterNot { it.health == 1 }

        val closestReachableCapturedCity = capturedCities
                .map { it.getCenterTile() }
                .sortedBy { it.aerialDistanceTo(unit.getTile()) }
                .firstOrNull { unit.movement.canReach(it) }

        if (closestReachableCapturedCity != null) {
            return headTowardsEnemyCity(unit, closestReachableCapturedCity)
        }
        return false

    }

    private fun tryGarrisoningUnit(unit: MapUnit): Boolean {
        if (unit.type.isMelee() || unit.type.isWaterUnit()) return false // don't garrison melee units, they're not that good at it
        val citiesWithoutGarrison = unit.civInfo.cities.filter {
            val centerTile = it.getCenterTile()
            centerTile.militaryUnit == null
                    && unit.movement.canMoveTo(centerTile)
        }

        fun isCityThatNeedsDefendingInWartime(city: CityInfo): Boolean {
            if (city.health < city.getMaxHealth()) return true // this city is under attack!
            for (enemyCivCity in unit.civInfo.diplomacy.values
                    .filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() }.flatMap { it.cities })
                if (city.getCenterTile().aerialDistanceTo(enemyCivCity.getCenterTile()) <= 5) return true // this is an edge city that needs defending
            return false
        }

        val citiesToTry: Sequence<CityInfo>

        if (!unit.civInfo.isAtWar()) {
            if (unit.getTile().isCityCenter()) return true // It's always good to have a unit in the city center, so if you haven't found anyone around to attack, forget it.
            citiesToTry = citiesWithoutGarrison.asSequence()
        } else {
            if (unit.getTile().isCityCenter() &&
                    isCityThatNeedsDefendingInWartime(unit.getTile().getCity()!!)) return true

            citiesToTry = citiesWithoutGarrison.asSequence()
                    .filter { isCityThatNeedsDefendingInWartime(it) }
        }

        val closestReachableCityNeedsDefending = citiesToTry
                .sortedBy { it.getCenterTile().aerialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movement.canReach(it.getCenterTile()) }
        if (closestReachableCityNeedsDefending == null) return false
        unit.movement.headTowards(closestReachableCityNeedsDefending.getCenterTile())
        return true
    }

    /** This is what a unit with the 'explore' action does.
    It also explores, but also has other functions, like healing if necessary. */
    fun automatedExplore(unit: MapUnit) {
        if (tryGoToRuinAndEncampment(unit) && unit.currentMovement == 0f) return
        if (unit.health < 80 && tryHealUnit(unit)) return
        if (tryExplore(unit)) return
        unit.civInfo.addNotification("[${unit.displayName()}] finished exploring.", unit.currentTile.position, unit.name, "OtherIcons/Sleep")
        unit.action = null
    }


    fun runAway(unit: MapUnit) {
        val reachableTiles = unit.movement.getDistanceToTiles()
        val enterableCity = reachableTiles.keys.firstOrNull { it.isCityCenter() && unit.movement.canMoveTo(it) }
        if (enterableCity != null) {
            unit.movement.moveToTile(enterableCity)
            return
        }
        val tileFurthestFromEnemy = reachableTiles.keys.filter { unit.movement.canMoveTo(it) }
            .maxByOrNull { countDistanceToClosestEnemy(unit, it) }
        if (tileFurthestFromEnemy == null) return // can't move anywhere!
        unit.movement.moveToTile(tileFurthestFromEnemy)
    }


    fun countDistanceToClosestEnemy(unit: MapUnit, tile: TileInfo): Int {
        for (i in 1..3)
            if (tile.getTilesAtDistance(i).any { containsEnemyMilitaryUnit(unit, it) })
                return i
        return 4
    }

    fun containsEnemyMilitaryUnit(unit: MapUnit, tileInfo: TileInfo) = tileInfo.militaryUnit != null
            && tileInfo.militaryUnit!!.civInfo.isAtWarWith(unit.civInfo)

}