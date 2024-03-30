package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsPillage
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade

object UnitAutomation {

    private const val CLOSE_ENEMY_TILES_AWAY_LIMIT = 5
    private const val CLOSE_ENEMY_TURNS_AWAY_LIMIT = 3f

    private fun isGoodTileToExplore(unit: MapUnit, tile: Tile): Boolean {
        return (tile.getOwner() == null || !tile.getOwner()!!.isCityState())
                && tile.neighbors.any { !unit.civ.hasExplored(it) }
                && (!unit.civ.isCityState() || tile.neighbors.any { it.getOwner() == unit.civ }) // Don't want city-states exploring far outside their borders
                && unit.getDamageFromTerrain(tile) <= 0    // Don't take unnecessary damage
                && unit.civ.threatManager.getDistanceToClosestEnemyUnit(tile, 3) > 3 // don't walk in range of enemy units
                && unit.movement.canMoveTo(tile) // expensive, evaluate last
                && unit.movement.canReach(tile) // expensive, evaluate last
    }

    internal fun tryExplore(unit: MapUnit): Boolean {
        if (tryGoToRuinAndEncampment(unit) && (unit.currentMovement == 0f || unit.isDestroyed)) return true

        val explorableTilesThisTurn =
                unit.movement.getDistanceToTiles().keys.filter { isGoodTileToExplore(unit, it) }
        if (explorableTilesThisTurn.any()) {
            val bestTile = explorableTilesThisTurn
                .sortedByDescending { it.tileHeight }  // secondary sort is by 'how far can you see'
                .maxByOrNull { it.aerialDistanceTo(unit.currentTile) }!! // primary sort is by 'how far can you go'
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
        if (!unit.civ.isMajorCiv()) return false // barbs don't have anything to do in ruins

        val tileWithRuinOrEncampment = unit.viewableTiles
            .firstOrNull {
                (
                        (it.improvement != null && it.getTileImprovement()!!.isAncientRuinsEquivalent())
                                || it.improvement == Constants.barbarianEncampment
                        )
                        && unit.movement.canMoveTo(it) && unit.movement.canReach(it)
            } ?: return false
        unit.movement.headTowards(tileWithRuinOrEncampment)
        return true
    }

    // "Fog busting" is a strategy where you put your units slightly outside your borders to discourage barbarians from spawning
    private fun tryFogBust(unit: MapUnit): Boolean {
        if (!Automation.afraidOfBarbarians(unit.civ)) return false // Not if we're not afraid

        // If everything around this unit is visible, we can stop.
        // Calculations below are quite expensive especially in the late game.
        if (unit.currentTile.getTilesInDistance(5).all { it.isVisible(unit.civ) }) {
            return false
        }

        val reachableTilesThisTurn =
                unit.movement.getDistanceToTiles().keys.filter { isGoodTileForFogBusting(unit, it) }
        if (reachableTilesThisTurn.any()) {
            unit.movement.headTowards(reachableTilesThisTurn.random()) // Just pick one
            return true
        }

        // Nothing immediate, lets look further. Number increases exponentially with distance - at 10 this took a looong time
        for (tile in unit.currentTile.getTilesInDistance(5))
            if (isGoodTileForFogBusting(unit, tile)) {
                unit.movement.headTowards(tile)
                return true
            }
        return false
    }

    private fun isGoodTileForFogBusting(unit: MapUnit, tile: Tile): Boolean {
        return unit.movement.canMoveTo(tile)
                && tile.getOwner() == null
                && tile.neighbors.all { it.getOwner() == null }
                && unit.civ.hasExplored(tile)
                && tile.getTilesInDistance(2).any { it.getOwner() == unit.civ }
                && unit.getDamageFromTerrain(tile) <= 0
                && unit.movement.canReach(tile) // expensive, evaluate last
    }

    fun wander(unit: MapUnit, stayInTerritory: Boolean = false, tilesToAvoid: Set<Tile> = setOf()) {
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val reachableTiles = unitDistanceToTiles
                .filter {
                    it.key !in tilesToAvoid
                    && unit.movement.canMoveTo(it.key)
                    && unit.movement.canReach(it.key)
                }

        val reachableTilesMaxWalkingDistance = reachableTiles
                .filter { it.value.totalDistance == unit.currentMovement
                        && unit.getDamageFromTerrain(it.key) <= 0 // Don't end turn on damaging terrain for no good reason
                        && (!stayInTerritory || it.key.getOwner() == unit.civ) }
        if (reachableTilesMaxWalkingDistance.any()) unit.movement.moveToTile(reachableTilesMaxWalkingDistance.toList().random().first)
        else if (reachableTiles.any()) unit.movement.moveToTile(reachableTiles.keys.random())
    }

    internal fun tryUpgradeUnit(unit: MapUnit): Boolean {
        if (unit.civ.isHuman() && (!UncivGame.Current.settings.automatedUnitsCanUpgrade
                || UncivGame.Current.settings.autoPlay.fullAutoPlayAI)) return false

        val upgradeUnits = getUnitsToUpgradeTo(unit)
        if (upgradeUnits.none()) return false // for resource reasons, usually
        val upgradedUnit = upgradeUnits.minBy { it.cost }

        if (upgradedUnit.getResourceRequirementsPerTurn(StateForConditionals(unit.civ, unit = unit)).keys.any { !unit.requiresResource(it) }) {
            // The upgrade requires new resource types, so check if we are willing to invest them
            if (!Automation.allowSpendingResource(unit.civ, upgradedUnit)) return false
        }

        val upgradeActions = UnitActionsUpgrade.getUpgradeActions(unit)

        upgradeActions.firstOrNull{ (it as UpgradeUnitAction).unitToUpgradeTo == upgradedUnit }?.action?.invoke() ?: return false
        //todo Incorrect - an _unsuccessful_ upgrade might have _resurrected_ the original in which case it's a new clone, and unit.isDestroyed is still true
        return unit.isDestroyed // a successful upgrade action will destroy this unit
    }

    /** Get the base unit this map unit could upgrade to, respecting researched tech and nation uniques only.
     *  Note that if the unit can't upgrade, the current BaseUnit is returned.
     */
    private fun getUnitsToUpgradeTo(unit: MapUnit): Sequence<BaseUnit> {

        fun isInvalidUpgradeDestination(baseUnit: BaseUnit): Boolean {
            if (!unit.civ.tech.isResearched(baseUnit))
                return true
            return baseUnit.getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)
                .any { !it.conditionalsApply(StateForConditionals(unit.civ, unit = unit)) }
        }

        return unit.baseUnit.getRulesetUpgradeUnits(StateForConditionals(unit.civ, unit = unit))
            .map { unit.civ.getEquivalentUnit(it) }
            .filter { !isInvalidUpgradeDestination(it) && unit.upgrade.canUpgrade(it) }
    }

    fun automateUnitMoves(unit: MapUnit) {
        check(!unit.civ.isBarbarian()) { "Barbarians is not allowed here." }

        // Might die next turn - move!
        if (unit.health <= unit.getDamageFromTerrain() && tryHealUnit(unit)) return


        if (unit.isCivilian()) {
            CivilianUnitAutomation.automateCivilianUnit(unit, getDangerousTiles(unit))
            return
        }

        while (unit.promotions.canBePromoted() &&
            // Restrict Human automated units from promotions via setting
                (UncivGame.Current.settings.automatedUnitsChoosePromotions || unit.civ.isAI())) {
            val availablePromotions = unit.promotions.getAvailablePromotions()
                .filterNot { it.hasUnique(UniqueType.SkipPromotion) }
            if (availablePromotions.none()) break
            unit.promotions.addPromotion(
                availablePromotions.filter { it.hasUnique(UniqueType.FreePromotion) }.toList().randomOrNull()?.name
                    ?: availablePromotions.toList().random().name)
        }

        //This allows for military units with certain civilian abilities to behave as civilians in peace and soldiers in war
        if ((unit.hasUnique(UniqueType.BuildImprovements) || unit.hasUnique(UniqueType.FoundCity) ||
                unit.hasUnique(UniqueType.ReligiousUnit) || unit.hasUnique(UniqueType.CreateWaterImprovements))
                && !unit.civ.isAtWar()){
            CivilianUnitAutomation.automateCivilianUnit(unit, getDangerousTiles(unit))
            return
        }

        // Note that not all nukes have to be air units
        if (unit.baseUnit.isNuclearWeapon()) {
            return AirUnitAutomation.automateNukes(unit)
        }

        if (unit.baseUnit.isAirUnit()) {
            if (unit.canIntercept())
                return AirUnitAutomation.automateFighter(unit)

            if (unit.hasUnique(UniqueType.SelfDestructs))
                return AirUnitAutomation.automateMissile(unit)

            return AirUnitAutomation.automateBomber(unit)
        }

        if (tryGoToRuinAndEncampment(unit) && unit.currentMovement == 0f) return

        if (tryUpgradeUnit(unit)) return

        if (unit.health < 50 && (tryRetreat(unit) || tryHealUnit(unit))) return // do nothing but heal

        // If there are no enemies nearby and we can heal here, wait until we are at full health
        if (unit.health < 100 && canUnitHealInTurnsOnCurrentTile(unit,2, 4)) return

        // Accompany settlers
        if (tryAccompanySettlerOrGreatPerson(unit)) return

        if (tryHeadTowardsOurSiegedCity(unit)) return

        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit)) return

        // if there is an attackable unit in the vicinity, attack!
        if (tryAttacking(unit)) return

        if (tryTakeBackCapturedCity(unit)) return

        // Focus all units without a specific target on the enemy city closest to one of our cities
        if (HeadTowardsEnemyCityAutomation.tryHeadTowardsEnemyCity(unit)) return

        if (tryGarrisoningRangedLandUnit(unit)) return

        if (tryStationingMeleeNavalUnit(unit)) return

        if (unit.health < 80 && tryHealUnit(unit)) return

        // move towards the closest reasonably attackable enemy unit within 3 turns of movement (and 5 tiles range)
        if (tryAdvanceTowardsCloseEnemy(unit)) return

        if (tryHeadTowardsEncampment(unit)) return

        if (unit.health < 100 && tryHealUnit(unit)) return

        // else, try to go to unreached tiles
        if (tryExplore(unit)) return

        if (tryFogBust(unit)) return

        // Idle CS units should wander so they don't obstruct players so much
        if (unit.civ.isCityState())
            wander(unit, stayInTerritory = true)
    }


    /** @return true only if the unit has 0 movement left */
    private fun tryAttacking(unit: MapUnit): Boolean {
        repeat(unit.maxAttacksPerTurn() - unit.attacksThisTurn) {
            if (BattleHelper.tryAttackNearbyEnemy(unit)) return true
            // Calvary style tctic, attack and then retreat
            if (unit.health < 50 && tryRetreat(unit)) return true
        }
        return false
    }

    private fun tryHeadTowardsEncampment(unit: MapUnit): Boolean {
        if (unit.hasUnique(UniqueType.SelfDestructs)) return false // don't use single-use units against barbarians...
        val knownEncampments = unit.civ.gameInfo.tileMap.values.asSequence()
                .filter { it.improvement == Constants.barbarianEncampment && unit.civ.hasExplored(it) }
        val cities = unit.civ.cities
        val encampmentsCloseToCities = knownEncampments
            .filter { cities.any { city -> city.getCenterTile().aerialDistanceTo(it) < 6 } }
            .sortedBy { it.aerialDistanceTo(unit.currentTile) }
        val encampmentToHeadTowards = encampmentsCloseToCities.firstOrNull { unit.movement.canReach(it) }
            ?: return false
        unit.movement.headTowards(encampmentToHeadTowards)
        return true
    }

    private fun tryRetreat(unit: MapUnit): Boolean {
        if (!unit.civ.isAtWar()) return false
        // Precondition: This must be a military unit
        if (unit.isCivilian()) return false
        if (unit.baseUnit.isAirUnit()) return false
        // Better to do a more healing oriented move then
        if (unit.civ.threatManager.getDistanceToClosestEnemyUnit(unit.getTile(),6, true) > 4) return false


        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val closestCity = unit.civ.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(unit.getTile()) }?.takeIf { it.getCenterTile().aerialDistanceTo(unit.getTile()) < 20 }

        // Finding the distance to the closest enemy is expensive, so lets sort the tiles using a cheaper function
        val sortedTilesToRetreatTo: Sequence<Tile> = if (closestCity != null) {
            // If we have a city, lets favor the tiles closer to that city
            unitDistanceToTiles.asSequence().map { it.key }.sortedBy { it.aerialDistanceTo(closestCity.getCenterTile()) }
        } else {
            // Rare case, what if we don't have a city nearby?
            // Lets favor the tiles that don't have enemies close by
            // Ideally we should check in a greater radius but might get way too expensive
            unitDistanceToTiles.asSequence().map { it.key }.sortedByDescending { unit.civ.threatManager.getDistanceToClosestEnemyUnit(it, 3, false) }
        }

        val ourDistanceToClosestEnemy = unit.civ.threatManager.getDistanceToClosestEnemyUnit(unit.getTile(),6, false)
        // Lets check all tiles and swap with the first one
        for (retreatTile in sortedTilesToRetreatTo) {
            val tileDistanceToClosestEnemy = unit.civ.threatManager.getDistanceToClosestEnemyUnit(retreatTile,6,false)
            if (ourDistanceToClosestEnemy >= tileDistanceToClosestEnemy) continue

            val otherUnit = retreatTile.militaryUnit
            if (otherUnit == null) {
                // See if we can retreat to the tile
                if (unit.movement.canMoveTo(retreatTile)) {
                    unit.movement.moveToTile(retreatTile)
                    return true
                }
            } else if (otherUnit.civ == unit.civ) {
                // The tile is taken, lets see if we want to swap retreat to it
                if (otherUnit.health > 80) {
                    if (otherUnit.baseUnit.isRanged()) {
                        // Don't swap ranged units closer than they have to be
                        val range = otherUnit.baseUnit.range
                        if (ourDistanceToClosestEnemy < range)
                            continue
                    }
                    if (unit.movement.canUnitSwapTo(retreatTile)) {
                        unit.movement.swapMoveToTile(retreatTile)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun tryHealUnit(unit: MapUnit): Boolean {
        if (unit.baseUnit.isRanged() && unit.hasUnique(UniqueType.HealsEvenAfterAction))
            return false // will heal anyway, and attacks don't hurt

        // Try pillage improvements until healed
        while(tryPillageImprovement(unit, false)) {
            // If we are fully healed and can still do things, lets keep on going by returning false
            if (unit.currentMovement == 0f || unit.health == 100) return unit.currentMovement == 0f
        }

        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        if (unitDistanceToTiles.isEmpty()) return true // can't move, so...

        // If the unit can heal on this tile in two turns, just heal here
        if (canUnitHealInTurnsOnCurrentTile(unit,3,)) return true

        val currentUnitTile = unit.getTile()

        val dangerousTiles = unit.civ.threatManager.getDangerousTiles(unit, 4)

        val viableTilesForHealing = unitDistanceToTiles.keys
                .filter { it !in dangerousTiles && unit.movement.canMoveTo(it) }
        val tilesByHealingRate = viableTilesForHealing.groupBy { unit.rankTileForHealing(it) }

        if (tilesByHealingRate.keys.all { it == 0 }) { // We can't heal here at all! We're probably embarked
            if (!unit.baseUnit.movesLikeAirUnits()) {
                val reachableCityTile = unit.civ.cities.asSequence()
                    .map { it.getCenterTile() }
                    .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                    .firstOrNull { unit.movement.canReach(it) }
                if (reachableCityTile != null) unit.movement.headTowards(reachableCityTile)
                else wander(unit)
                return true
            }
            // Try to get closer to an empty city
            val emptyCities = unit.civ.cities.asSequence()
                .map { it.getCenterTile() }
                .filter { unit.movement.canMoveTo(it) }
            if (emptyCities.none()) return false // Nowhere to move to heal

            val nextTileToMove = unitDistanceToTiles.keys
                .filter { unit.movement.canMoveTo(it) }
                .minByOrNull { tile ->
                    emptyCities.minOf { city ->
                        city.aerialDistanceTo(tile)
                    }
                } ?: return false

            unit.movement.moveToTile(nextTileToMove)
            return true
        }

        val bestTilesForHealing = tilesByHealingRate.maxByOrNull { it.key }!!.value
        val bestTileForHealing = bestTilesForHealing.maxByOrNull { it.getDefensiveBonus() }!!
        val bestTileForHealingRank = unit.rankTileForHealing(bestTileForHealing)

        if (currentUnitTile != bestTileForHealing
                && bestTileForHealingRank > unit.rankTileForHealing(currentUnitTile) - unit.getDamageFromTerrain())
            unit.movement.moveToTile(bestTileForHealing)

        unit.fortifyIfCan()
        return true
    }

    /**
     * @return true if the tile is safe and the unit can heal to full within [turns]
     */
    private fun canUnitHealInTurnsOnCurrentTile(unit: MapUnit, turns: Int, noEnemyDistance: Int = 3): Boolean {
        if (unit.hasUnique(UniqueType.HealsEvenAfterAction)) return false // We can keep on moving
        // Check if we are not in a safe city and there is an enemy nearby this isn't a good tile to heal on
        if (!(unit.getTile().isCityCenter() && unit.getTile().getCity()!!.health > 50)
            && unit.civ.threatManager.getDistanceToClosestEnemyUnit(unit.getTile(), noEnemyDistance) <= noEnemyDistance) return false

        val healthRequiredPerTurn =  (100 - unit.health) / turns
        return healthRequiredPerTurn <= unit.rankTileForHealing(unit.getTile())
    }

    private fun getDangerousTiles(unit: MapUnit): HashSet<Tile> {
        val nearbyRangedEnemyUnits = unit.currentTile.getTilesInDistance(3)
            .flatMap { tile -> tile.getUnits().filter { unit.civ.isAtWarWith(it.civ) } }

        val tilesInRangeOfAttack = nearbyRangedEnemyUnits
            .flatMap { it.getTile().getTilesInDistance(it.getRange()) }

        val tilesWithinBombardmentRange = unit.currentTile.getTilesInDistance(3)
            .filter { it.isCityCenter() && it.getCity()!!.civ.isAtWarWith(unit.civ) }
            .flatMap { it.getTilesInDistance(it.getCity()!!.range) }

        val tilesWithTerrainDamage = unit.currentTile.getTilesInDistance(3)
            .filter { unit.getDamageFromTerrain(it) > 0 }

        return (tilesInRangeOfAttack + tilesWithinBombardmentRange + tilesWithTerrainDamage).toHashSet()
    }

    /**
     * @return true if the unit was able to pillage a tile, false otherwise
     */
    fun tryPillageImprovement(unit: MapUnit, onlyPillageToHeal: Boolean = false): Boolean {
        if (unit.isCivilian()) return false
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val tilesThatCanWalkToAndThenPillage = unitDistanceToTiles
            .filter { it.value.totalDistance < unit.currentMovement }.keys
            .filter { unit.movement.canMoveTo(it) && UnitActionsPillage.canPillage(unit, it)
                    && (it.canPillageTileImprovement()
                    || (!onlyPillageToHeal && it.canPillageRoad() && it.getRoadOwner() != null && unit.civ.isAtWarWith(it.getRoadOwner()!!))) }

        if (tilesThatCanWalkToAndThenPillage.isEmpty()) return false
        val tileToPillage = tilesThatCanWalkToAndThenPillage.maxByOrNull { it.getDefensiveBonus(false) }!!
        if (unit.getTile() != tileToPillage)
            unit.movement.moveToTile(tileToPillage)

        if (unit.currentTile != tileToPillage) return false

        // We CANNOT use invokeUnitAction, since the default unit action contains a popup, which - when automated -
        //  runs a UI action on a side thread leading to crash!
        UnitActionsPillage.getPillageAction(unit, unit.currentTile)?.action?.invoke()
        return true
    }

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
        var closeEnemies = TargetHelper.getAttackableEnemies(
            unit,
            unitDistanceToTiles,
            tilesToCheck = unit.getTile().getTilesInDistance(CLOSE_ENEMY_TILES_AWAY_LIMIT).toList()
        ).filter {
            // Ignore units that would 1-shot you if you attacked. Account for taking terrain damage after the fact.
            BattleDamage.calculateDamageToAttacker(
                MapUnitCombatant(unit),
                Battle.getMapCombatantOfTile(it.tileToAttack)!!
            )
                    + unit.getDamageFromTerrain(it.tileToAttackFrom) < unit.health
        }

        if (unit.baseUnit.isRanged())
            closeEnemies = closeEnemies.filterNot { it.tileToAttack.isCityCenter() && it.tileToAttack.getCity()!!.health == 1 }

        val closestEnemy = closeEnemies
            .filter { unit.getDamageFromTerrain(it.tileToAttackFrom) <= 0 }  // Don't attack from a mountain
            .minByOrNull { it.tileToAttack.aerialDistanceTo(unit.getTile()) }

        if (closestEnemy != null) {
            unit.movement.headTowards(closestEnemy.tileToAttackFrom)
            return true
        }
        return false
    }

    private fun tryAccompanySettlerOrGreatPerson(unit: MapUnit): Boolean {
        val settlerOrGreatPersonToAccompany = unit.civ.units.getCivUnits()
            .firstOrNull {
                val tile = it.currentTile
                it.isCivilian() &&
                        (it.hasUnique(UniqueType.FoundCity) || unit.isGreatPerson())
                        && tile.militaryUnit == null && unit.movement.canMoveTo(tile)
                        && unit.movement.getDistanceToTiles().containsKey(tile)
            } ?: return false
        unit.movement.headTowards(settlerOrGreatPersonToAccompany.currentTile)
        return true
    }

    private fun tryHeadTowardsOurSiegedCity(unit: MapUnit): Boolean {
        val siegedCities = unit.civ.cities
                .asSequence()
                .filter {
                    unit.civ == it.civ &&
                            it.health < it.getMaxHealth() * 0.75
                } //Weird health issues and making sure that not all forces move to good defenses

        if (siegedCities.any { it.getCenterTile().aerialDistanceTo(unit.getTile()) <= 2 })
            return false

        val reachableTileNearSiegedCity = siegedCities
                .flatMap { it.getCenterTile().getTilesAtDistance(2) }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movement.canMoveTo(it) && unit.movement.canReach(it)
                        && unit.getDamageFromTerrain(it) <= 0 } // Avoid ending up on damaging terrain

        if (reachableTileNearSiegedCity != null) {
            unit.movement.headTowards(reachableTileNearSiegedCity)
        }
        return unit.currentMovement == 0f
    }


    fun tryEnterOwnClosestCity(unit: MapUnit): Boolean {
        val closestCity = unit.civ.cities
            .asSequence()
            .sortedBy { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            .firstOrNull { unit.movement.canReach(it.getCenterTile()) }
          ?: return false // Panic!

        unit.movement.headTowards(closestCity.getCenterTile())
        return true
    }

    fun tryBombardEnemy(city: City): Boolean {
        if (!city.canBombard()) return false
        val enemy = chooseBombardTarget(city)
            ?: return false
        Battle.attack(CityCombatant(city), enemy)
        return true
    }

    private fun chooseBombardTarget(city: City): ICombatant? {
        var targets = TargetHelper.getBombardableTiles(city).map { Battle.getMapCombatantOfTile(it)!! }
            .filterNot { it.isCivilian() && !it.getUnitType().hasUnique(UniqueType.Uncapturable) } // Don't bombard capturable civilians
        if (targets.none()) return null

        val siegeUnits = targets
                .filter { it is MapUnitCombatant && it.unit.baseUnit.isProbablySiegeUnit() }
        val nonEmbarkedSiege = siegeUnits.filter { it is MapUnitCombatant && !it.unit.isEmbarked() }
        if (nonEmbarkedSiege.any()) targets = nonEmbarkedSiege
        else if (siegeUnits.any()) targets = siegeUnits
        else {
            val rangedUnits = targets
                    .filter { it.isRanged() }
            if (rangedUnits.any()) targets = rangedUnits
        }

        val hitsToKill = targets.associateWith { it.getHealth().toFloat() / BattleDamage.calculateDamageToDefender(
            CityCombatant(city),
            it
        ).toFloat().coerceAtLeast(1f) }
        val target = hitsToKill.filter { it.value <= 1 }.maxByOrNull { it.key.getAttackingStrength() }?.key
        if (target != null) return target
        return hitsToKill.minByOrNull { it.value }?.key
    }

    private fun tryTakeBackCapturedCity(unit: MapUnit): Boolean {
        var capturedCities = unit.civ.getKnownCivs() // This is a Sequence
                .flatMap { it.cities.asSequence() }
                .filter {
                    unit.civ.isAtWarWith(it.civ) &&
                            unit.civ.civName == it.foundingCiv &&
                            it.isInResistance() &&
                            it.health < it.getMaxHealth()
                } //Most likely just been captured


        if (unit.baseUnit.isRanged()) // ranged units don't harm capturable cities, waste of a turn
            capturedCities = capturedCities.filterNot { it.health == 1 }

        val closestReachableCapturedCity = capturedCities
                .map { it.getCenterTile() }
                .sortedBy { it.aerialDistanceTo(unit.getTile()) }
                .firstOrNull { unit.movement.canReach(it) }

        if (closestReachableCapturedCity != null) {
            return HeadTowardsEnemyCityAutomation.headTowardsEnemyCity(
                unit,
                closestReachableCapturedCity,
                // This should be cached after the `canReach` call above.
                unit.movement.getShortestPath(closestReachableCapturedCity)
            )
        }
        return false
    }

    private fun tryGarrisoningRangedLandUnit(unit: MapUnit): Boolean {
        if (unit.baseUnit.isMelee() || unit.baseUnit.isWaterUnit()) return false // don't garrison melee units, they're not that good at it
        val citiesWithoutGarrison = unit.civ.cities.filter {
            val centerTile = it.getCenterTile()
            centerTile.militaryUnit == null
                    && unit.movement.canMoveTo(centerTile)
        }


        val citiesToTry = if (!unit.civ.isAtWar()) {
            if (unit.getTile().isCityCenter()) return true // It's always good to have a unit in the city center, so if you haven't found anyone around to attack, forget it.
            citiesWithoutGarrison.asSequence()
        } else {
            if (unit.getTile().isCityCenter() &&
                    isCityThatNeedsDefendingInWartime(unit.getTile().getCity()!!)) return true
            val citiesWithoutGarrisonThatNeedDefending = citiesWithoutGarrison.asSequence()
                    .filter { isCityThatNeedsDefendingInWartime(it) }
            if (citiesWithoutGarrisonThatNeedDefending.any()) citiesWithoutGarrisonThatNeedDefending
            else citiesWithoutGarrison.asSequence()
        }

        val closestReachableCityNeedsDefending = citiesToTry
            .sortedBy { it.getCenterTile().aerialDistanceTo(unit.currentTile) }
            .firstOrNull { unit.movement.canReach(it.getCenterTile()) }
            ?: return false
        unit.movement.headTowards(closestReachableCityNeedsDefending.getCenterTile())
        return true
    }

    private fun isCityThatNeedsDefendingInWartime(city: City): Boolean {
        if (city.health < city.getMaxHealth()) return true // this city is under attack!
        for (enemyCivCity in city.civ.diplomacy.values
            .filter { it.diplomaticStatus == DiplomaticStatus.War }
            .map { it.otherCiv() }.flatMap { it.cities })
            if (city.getCenterTile().aerialDistanceTo(enemyCivCity.getCenterTile()) <= 5) return true // this is an edge city that needs defending
        return false
    }

    private fun tryStationingMeleeNavalUnit(unit: MapUnit): Boolean {
        fun isMeleeNaval(mapUnit: MapUnit) = mapUnit.baseUnit.isMelee() && mapUnit.type.isWaterUnit()

        if (!isMeleeNaval(unit)) return false
        val closeCity = unit.getTile().getTilesInDistance(3)
            .firstOrNull { it.isCityCenter() }

        // We're the closest unit to this city, we should stay here :)
        if (closeCity != null && closeCity.getTilesInDistance(3)
                .flatMap { it.getUnits() }
                .firstOrNull {isMeleeNaval(it)} == unit
            && unit.movement.canReach(closeCity)) {
            unit.movement.headTowards(closeCity)
            return true
        }

        val citiesWithoutNavalDefence = unit.civ.cities.filter { it.isCoastal() }
            .filter { it.getCenterTile().aerialDistanceTo(unit.getTile()) < 20 } // Not too far away
            .filter { it.getCenterTile().getTilesInDistance(3)
                .flatMap { it.getUnits() }
                .none { isMeleeNaval(it) }}

        val reachableCity = citiesWithoutNavalDefence.firstOrNull {
            unit.movement.canReach(it.getCenterTile())
        } ?: return false
        unit.movement.headTowards(reachableCity.getCenterTile())
        return true
    }

    /** This is what a unit with the 'explore' action does.
    It also explores, but also has other functions, like healing if necessary. */
    fun automatedExplore(unit: MapUnit) {
        if (tryGoToRuinAndEncampment(unit) && (unit.currentMovement == 0f || unit.isDestroyed)) return
        if (unit.health < 80 && tryHealUnit(unit)) return
        if (tryExplore(unit)) return
        unit.civ.addNotification("${unit.shortDisplayName()} finished exploring.", unit.currentTile.position, NotificationCategory.Units, unit.name, "OtherIcons/Sleep")
        unit.action = null
    }


}
