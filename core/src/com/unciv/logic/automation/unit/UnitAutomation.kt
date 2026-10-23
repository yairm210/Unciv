package com.unciv.logic.automation.unit

import com.unciv.view.hasMovement
import com.unciv.view.isAirUnit
import com.unciv.view.isCivilian
import com.unciv.view.isNuclearWeapon
import com.unciv.view.TileView
import com.unciv.logic.automation.civilization.ThreatManager
import com.unciv.logic.automation.civilization.AutomationCache
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.mapunit.UpgradeAvailability
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.logic.map.mapunit.actions.UnitActionsPillage
import com.unciv.logic.map.mapunit.actions.UnitActionsUpgrade
import kotlin.math.ceil
import com.unciv.view.GameView
import com.unciv.view.ForeignCityView
import com.unciv.view.isAtWar
import com.unciv.view.stateBasedRandom
import com.unciv.view.MapUnitView
import yairm210.purity.annotations.Readonly
import com.unciv.logic.automation.Timers.Companion.timeThis
import com.unciv.logic.automation.civilization.NextTurnAutomation

object UnitAutomation {

    private const val CLOSE_ENEMY_TILES_AWAY_LIMIT = 5
    private const val CLOSE_ENEMY_TURNS_AWAY_LIMIT = 3f


    fun automateUnitMoves(unitView: MapUnitView, cache: AutomationCache = AutomationCache()):Unit = timeThis("automateUnitMoves") {
        val unit = unitView.getUnit()
        check(!unit.civ.isBarbarian) { "Barbarians is not allowed here." }

        // Might die next turn - move!
        if (unit.getDamageFromTerrain() > 0 && tryHealUnit(unitView, cache)) return


        if (unitView.isCivilian()) {
            CivilianUnitAutomation.automateCivilianUnit(unitView, getDangerousTiles(unitView), cache)
            return
        }

        val isHumanAndNotAutoplaying =
            unit.civ.isHuman() && GUI.getWorldScreenIfActive()?.autoPlay?.isAutoPlaying() != true
        // AI promotes units via NextTurnAutomation.automateUnits
        if (isHumanAndNotAutoplaying) NextTurnAutomation.applyPromotions(unitView)
        // AI upgrades units via UseGoldAutomation in NextTurnAutomation
        if (isHumanAndNotAutoplaying && tryUpgradeUnit(unitView)) return

        //This allows for military units with certain civilian abilities to behave as civilians in peace and soldiers in war
        if ((unitView.hasUnique(UniqueType.BuildImprovements) || unitView.hasUnique(UniqueType.FoundCity) || 
                    unitView.hasUnique(UniqueType.FoundPuppetCity) ||
                    unitView.hasUnique(UniqueType.ReligiousUnit) || unitView.hasUnique(UniqueType.CreateWaterImprovements))
            && !unit.civ.isAtWar()){
            CivilianUnitAutomation.automateCivilianUnit(unitView, getDangerousTiles(unitView), cache)
            return
        }

        // Note that not all nukes have to be air units
        if (unitView.isNuclearWeapon()) {
            return AirUnitAutomation.automateNukes(unitView)
        }

        if (unitView.isAirUnit()) {
            if (unit.canIntercept())
                return AirUnitAutomation.automateFighter(unitView, cache)

            if (unitView.hasUnique(UniqueType.SelfDestructs))
                return AirUnitAutomation.automateMissile(unitView)

            return AirUnitAutomation.automateBomber(unitView)
        }

        // Accompany settlers
        if (tryAccompanySettlerOrGreatPerson(unitView)) return

        if (tryGoToRuin(unitView) && !unitView.hasMovement()) return

        if (unitView.unitHealth < 50 && (tryRetreat(unitView, cache) || tryHealUnit(unitView, cache))) return // do nothing but heal

        // If there are no enemies nearby and we can heal here, wait until we are at full health
        if (unitView.unitHealth < 100 && canUnitHealInTurnsOnCurrentTile(unitView, cache, 2, 3)) return

        if (tryHeadTowardsOurSiegedCity(unitView)) return

        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit)) return

        // if there is an attackable unit in the vicinity, attack!
        if (tryAttacking(unitView, cache)) return

        if (tryTakeBackCapturedCity(unitView)) return

        // Focus all units without a specific target on the enemy city closest to one of our cities
        if (HeadTowardsEnemyCityAutomation.tryHeadTowardsEnemyCity(unitView)) return

        if (tryHeadTowardsEncampment(unitView)) return

        if (tryGarrisoningLandUnit(unitView)) return

        if (unitView.unitHealth < 80 && tryHealUnit(unitView, cache)) return

        // move towards the closest reasonably attackable enemy unit within 3 turns of movement (and 5 tiles range)
        if (tryAdvanceTowardsCloseEnemy(unitView)) return

        if (unitView.unitHealth < 100 && tryHealUnit(unitView, cache)) return

        if (tryPrepare(unitView)) return

        // else, try to go to unreached tiles
        if (tryExplore(unitView, cache)) return

        if (tryFogBust(unitView)) return

        // Idle CS units should wander so they don't obstruct players so much
        if (unitView.civ().isCityState)
            wander(unitView, stayInTerritory = true)
    }

    /** Number of tiles within [radius] of a tile on an unbounded hex map, including the tile itself */
    @Readonly private fun tilesInHexRadius(radius: Int) = 1 + 3 * radius * (radius + 1)

    @Readonly
    private fun isGoodTileToExplore(unitView: MapUnitView, cache: AutomationCache, tile: TileView, unitVisibilityRange: Int): Boolean {
        val civ = unitView.civ()
        // These should be ordered by increasing computational cost
        return tile.getOwner()?.isCityState != true
                && (!civ.isCityState || tile.getExploredTilesAtDistance(1).any { it.getOwner() == civ }) // Don't want city-states exploring far outside their borders
                && unitView.getDamageFromTerrain(tile) <= 0    // Don't take unnecessary damage
                // Some tile in range is unexplored. Since we can't know what tiles exist that we don't know about, this is a guess.
                //   On map edges this will be wrong... Units may "hug edges". In practice this is not a big deal. 
                && tile.getExploredTilesInDistance(unitVisibilityRange).count() < tilesInHexRadius(unitVisibilityRange)
                && ThreatManager.getDistanceToClosestEnemyUnit(civ, cache, tile, 3) > 3 // don't walk in range of enemy units
                && unitView.canMoveTo(tile) // expensive, evaluate last
                && unitView.canReach(tile) // expensive, evaluate last
    }

    internal fun tryExplore(unitView: MapUnitView, cache: AutomationCache): Boolean = timeThis("tryExplore") {
        if (tryGoToRuin(unitView) && (!unitView.hasMovement() || unitView.isDestroyed())) return true

        val unitVisibilityRange = unitView.getVisibilityRange()
        val explorableTilesThisTurn =
                unitView.getDistanceToTiles().keys.filter { isGoodTileToExplore(unitView, cache, it, unitVisibilityRange) }
        if (explorableTilesThisTurn.any()) {
            // Assign each tile a score for "explore value"
            // This could be more elaborate: for example add a malus for distant tiles such as to move not too far away from capital (barb control)
            // or bonus according to tile yields (likely candidates for city locations), but this comes at a cost of performance
            val bestTile = explorableTilesThisTurn
                .maxBy { it.tileHeight + it.estimateUnexploredTilesAtDistance(unitVisibilityRange) }
            return unitView.tryHeadTowards(bestTile)
        }

        // Nothing immediate, lets look further. Number increases exponentially with distance - at 10 this took a looong time
        for (tile in unitView.getTile().getExploredTilesInDistance(5))
            if (isGoodTileToExplore(unitView, cache, tile, unitVisibilityRange))
                return unitView.tryHeadTowards(tile)
        return false
    }

    private fun tryGoToRuin(unitView: MapUnitView): Boolean {
        if (!unitView.civ().isMajorCiv()) return false // barbs don't have anything to do in ruins

        val tileWithRuin = unitView.getViewableTiles()
            .firstOrNull {
                it.getRuleset().tileImprovements[it.improvement]?.isAncientRuinsEquivalent() == true
                    && unitView.canMoveTo(it) && unitView.canReach(it)
            }
            ?: return false
        unitView.tryHeadTowards(tileWithRuin)
        return true
    }

    // "Fog busting" is a strategy where you put your units slightly outside your borders to discourage barbarians from spawning
    private fun tryFogBust(unitView: MapUnitView): Boolean {
        if (!Automation.afraidOfBarbarians(unitView.civ())) return false // Not if we're not afraid

        // If everything around this unit is visible, we can stop.
        // Calculations below are quite expensive especially in the late game.
        val unitTile = unitView.getTile()
        val tilesInRange = unitTile.getExploredTilesInDistance(5).toList()
        if (tilesInRange.size == tilesInHexRadius(5) && tilesInRange.all { it.isVisible() }) return false

        val rng = unitView.stateBasedRandom("UnitAutomation.tryFogBust")
        val reachableTilesThisTurn =
                unitView.getDistanceToTiles().keys.filter { isGoodTileForFogBusting(unitView, it) }
        if (reachableTilesThisTurn.any()) {
            unitView.tryHeadTowards(reachableTilesThisTurn.random(rng)) // Just pick one
            return true
        }

        // Nothing immediate, lets look further. Number increases exponentially with distance - at 10 this took a looong time
        for (tile in unitTile.getExploredTilesInDistance(5))
            if (isGoodTileForFogBusting(unitView, tile)) {
                unitView.tryHeadTowards(tile)
                return true
            }
        return false
    }

    @Readonly
    private fun isGoodTileForFogBusting(unitView: MapUnitView, tile: TileView): Boolean {
        val civ = unitView.civ()
        return unitView.canMoveTo(tile)
                && tile.getOwner() == null
                && tile.getVisibleNeighbors().all { it.getOwner() == null }
                && civ.hasExplored(tile)
                && tile.getExploredTilesInDistance(2).any { it.getOwner() == civ }
                && unitView.getDamageFromTerrain(tile) <= 0
                && unitView.canReach(tile) // expensive, evaluate last
    }

    fun wander(unitView: MapUnitView, stayInTerritory: Boolean = false, tilesToAvoid: Set<TileView> = setOf()) {
        if (!unitView.hasMovement()) return // return in case we can't move anyways
        val unitTile = unitView.getTile()
        val unitDistanceToTiles = unitTile.getExploredTilesAtDistance(1)
        // We could walk further, but wander() is meant to let units not stay on the same tile permanently,
        // to avoid obstructing human scouts and workers, moving just one tile should be enough
        val reachableTiles = unitDistanceToTiles
                .filter {
                    it !in tilesToAvoid
                        && unitView.canMoveTo(it)
                        && unitView.getDistanceToTiles().contains(it) // Yes this is required despite the above line - see #14461
                        && unitView.getDamageFromTerrain(it) <= 0 // Don't end turn on damaging terrain for no good reason
                        && (!stayInTerritory || it.getOwner() == unitView.civ() || unitTile.getOwner() != unitView.civ())
                }
        val rng = unitView.stateBasedRandom("UnitAutomation.wander")
        if (reachableTiles.any()) unitView.tryMoveToTile(reachableTiles.toList().random(rng))
    }

    internal fun tryUpgradeUnit(unitView: MapUnitView): Boolean {
        val civ = unitView.civ()
        if (civ.isHuman() && !UncivGame.Current.settings.automatedUnitsCanUpgrade
            && UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == false) return false

        val upgrade = unitView.getPossibleUpgrades()
            .filter { it.availability == UpgradeAvailability.Available // for resource reasons, usually
                && !(civ.isBarbarian() && it.upgradedUnit.hasUnique(UniqueType.CannotBeBarbarian)) }
            .minByOrNull { it.upgradedUnit.cost } ?: return false

        // The upgrade requires new resource types, so check if we are willing to invest them
        if (upgrade.newResourceRequirements.any { it.value > 0 }
            && !Automation.allowSpendingResource(civ, upgrade.upgradedUnit)) return false

        return unitView.tryUpgradeTo(upgrade.upgradedUnit)
    }


    /** @return true only if the unit has 0 movement left */
    private fun tryAttacking(unitView: MapUnitView, cache: AutomationCache): Boolean {
        repeat(unitView.maxAttacksPerTurn() - unitView.attacksThisTurn) {
            if (BattleHelper.tryAttackNearbyEnemy(unitView)) return true
            // Calvary style tctic, attack and then retreat
            if (unitView.unitHealth < 50 && tryRetreat(unitView, cache)) return true
        }
        return false
    }

    private fun tryHeadTowardsEncampment(unitView: MapUnitView): Boolean {
        if (unitView.hasUnique(UniqueType.SelfDestructs)) return false // don't use single-use units against barbarians...
        val knownEncampments = unitView.civ().cities().asSequence()
            .flatMap { it.getCenterTile().getExploredTilesInDistance(6) }
                .filter { it.isBarbarianEncampment() }
            .distinct()
        val encampmentsCloseToCities = knownEncampments.asSequence()
            .sortedBy { it.aerialDistanceTo(unitView.getTile()) }
        val encampmentToHeadTowards = encampmentsCloseToCities.firstOrNull { unitView.canReach(it) }
            ?: return false
        unitView.tryHeadTowards(encampmentToHeadTowards)
        return true
    }

    private fun tryRetreat(unitView: MapUnitView, cache: AutomationCache): Boolean {
        // Precondition: This must be a military unit
        if (unitView.isCivilian()) return false
        if (unitView.isAirUnit()) return false
        val civ = unitView.civ()
        val unitTile = unitView.getTile()
        // Better to do a more healing oriented move then
        if (ThreatManager.getDistanceToClosestEnemyUnit(civ, cache, unitTile, 4, true) > 3) return false


        val unitDistanceToTiles = unitView.getDistanceToTiles()
        val closestCity = civ.cities().minByOrNull { it.getCenterTile().aerialDistanceTo(unitTile) }
            ?.takeIf { it.getCenterTile().aerialDistanceTo(unitTile) < 20 }

        // Finding the distance to the closest enemy is expensive, so lets sort the tiles using a cheaper function
        val sortedTilesToRetreatTo: Sequence<TileView> = if (closestCity != null) {
            // If we have a city, lets favor the tiles closer to that city
            unitDistanceToTiles.asSequence().map { it.key }.sortedBy { it.aerialDistanceTo(closestCity.getCenterTile()) }
        } else {
            // Rare case, what if we don't have a city nearby?
            // Lets favor the tiles that don't have enemies close by
            // Ideally we should check in a greater radius but might get way too expensive
            unitDistanceToTiles.asSequence().map { it.key }.sortedByDescending { ThreatManager.getDistanceToClosestEnemyUnit(civ, cache, it, 3, false) }
        }

        val ourDistanceToClosestEnemy = ThreatManager.getDistanceToClosestEnemyUnit(civ, cache, unitTile, 4, false)
        // Lets check all tiles and swap with the first one
        for (retreatTile in sortedTilesToRetreatTo) {
            val tileDistanceToClosestEnemy = ThreatManager.getDistanceToClosestEnemyUnit(civ, cache, retreatTile, 4, false)
            if (ourDistanceToClosestEnemy >= tileDistanceToClosestEnemy) continue

            val otherUnit = retreatTile.militaryUnit
            if (otherUnit == null) {
                // See if we can retreat to the tile
                if (!unitView.canMoveTo(retreatTile)) continue
                unitView.tryMoveToTile(retreatTile)
                return true
            } else if (otherUnit.civ() == civ) {
                // The tile is taken, lets see if we want to swap retreat to it
                if (otherUnit.unitHealth <= 80) continue
                if (otherUnit.getBaseUnit().isRanged()) {
                    // Don't swap ranged units closer than they have to be
                    val range = otherUnit.getBaseUnit().range
                    if (ourDistanceToClosestEnemy < range)
                        continue
                }
                if (unitView.canSwapTo(retreatTile)) {
                    unitView.tryHeadTowards(retreatTile) // we need to move through the intermediate tiles
                    // if nothing changed
                    if (unitView.getTile().getVisibleNeighbors().contains(otherUnit.getTile()) && unitView.canSwapTo(retreatTile)) {
                        unitView.trySwapMoveToTile(retreatTile)
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun tryHealUnit(unitView: MapUnitView, cache: AutomationCache): Boolean {
        if (unitView.getBaseUnit().isRanged() && unitView.hasUnique(UniqueType.HealsEvenAfterAction))
            return false // will heal anyway, and attacks don't hurt

        // Try pillage improvements until healed
        while (tryPillageImprovement(unitView, true)) {
            // If we are fully healed and can still do things, lets keep on going by returning false
            if (!unitView.hasMovement() || unitView.unitHealth == 100) return !unitView.hasMovement()
        }

        val unitDistanceToTiles = unitView.getDistanceToTiles()
        if (unitDistanceToTiles.isEmpty()) return true // can't move, so...

        val dangerousTiles = ThreatManager.getDangerousTiles(cache, unitView, 3)
        
        // If the unit can heal on this tile in two turns, just heal here
        if (unitView.getTile() !in dangerousTiles 
            && canUnitHealInTurnsOnCurrentTile(unitView, cache, 3)) return true

        val currentUnitTile = unitView.getTile()

        val viableTilesForHealing = unitDistanceToTiles.keys
                .filter { it !in dangerousTiles && unitView.canMoveTo(it) }
        val tilesByHealingRate = viableTilesForHealing.groupBy { unitView.healingAmountForTile(it) }

        if (tilesByHealingRate.keys.all { it == 0 }) { // We can't heal here at all! We're probably embarked
            if (!unitView.isAirUnit()) {
                val reachableCityTile = unitView.civ().cities().asSequence()
                    .map { it.getCenterTile() }
                    .sortedBy { it.aerialDistanceTo(currentUnitTile) }
                    .firstOrNull { unitView.canReach(it) }
                if (reachableCityTile != null) unitView.tryHeadTowards(reachableCityTile)
                else wander(unitView)
                return true
            }
            // Try to get closer to an empty city
            val emptyCities = unitView.civ().cities().asSequence()
                .map { it.getCenterTile() }
                .filter { unitView.canMoveTo(it) }
            if (emptyCities.none()) return false // Nowhere to move to heal

            val nextTileToMove = unitDistanceToTiles.keys
                .filter { unitView.canMoveTo(it) }
                .minByOrNull { tile ->
                    emptyCities.minOf { city ->
                        city.aerialDistanceTo(tile)
                    }
                } ?: return false

            return unitView.tryMoveToTile(nextTileToMove)
        }

        val bestTilesForHealing = tilesByHealingRate.maxByOrNull { it.key }!!.value
        val bestTileForHealing = bestTilesForHealing.maxByOrNull { unitView.getDefensiveBonusOn(it) }!!
        val bestTileForHealingRank = unitView.healingAmountForTile(bestTileForHealing)

        if (currentUnitTile != bestTileForHealing
                && bestTileForHealingRank > unitView.healingAmountForTile(currentUnitTile) - unitView.getDamageFromTerrain(currentUnitTile))
            unitView.tryMoveToTile(bestTileForHealing)

        unitView.tryFortifyIfCan()
        return true
    }

    /**
     * @return true if the tile is safe and the unit can heal to full within [turns]
     */
    @Readonly
    private fun canUnitHealInTurnsOnCurrentTile(unitView: MapUnitView, cache: AutomationCache, turns: Int, noEnemyDistance: Int = 3): Boolean {
        if (unitView.hasUnique(UniqueType.HealsEvenAfterAction)) return false // We can keep on moving
        val unitTile = unitView.getTile()
        // Check if we are not in a safe city and there is an enemy nearby this isn't a good tile to heal on
        if (!(unitTile.isCityCenter() && unitTile.getCity()!!.getHealth() > 50)
            && ThreatManager.getDistanceToClosestEnemyUnit(unitView.civ(), cache, unitView.getTile(), noEnemyDistance) <= noEnemyDistance) return false

        // Round up, otherwise e.g. 99 health over 2 turns would give 0 required healing per turn
        val healthRequiredPerTurn = ceil((100 - unitView.unitHealth).toFloat() / turns).toInt()
        return healthRequiredPerTurn <= unitView.healingAmountForTile(unitTile)
    }

    @Readonly
    private fun getDangerousTiles(unitView: MapUnitView): HashSet<TileView> {
        val civ = unitView.civ()
        val nearbyEnemyUnits = unitView.getTile().getExploredTilesInDistance(3)
            .flatMap { tile -> tile.getVisibleUnits().filter { civ.isAtWarWith(it.civ()) } }

        val tilesInRangeOfAttack = nearbyEnemyUnits
            .flatMap { it.getTile().getExploredTilesInDistance((it.getMaxMovement() - 1) + it.getRange()) }

        val tilesWithinBombardmentRange = unitView.getTile().getExploredTilesInDistance(3)
            .filter { it.isCityCenter() && it.getCity()!!.owningCiv().isAtWarWith(civ) }
            .flatMap { it.getExploredTilesInDistance(it.getCity()!!.getBombardRange()) }

        val tilesWithTerrainDamage = unitView.getTile().getExploredTilesInDistance(3)
            .filter { unitView.getDamageFromTerrain(it) > 0 }

        return (tilesInRangeOfAttack + tilesWithinBombardmentRange + tilesWithTerrainDamage).toHashSet()
    }

    /**
     * @return true if the unit was able to pillage a tile, false otherwise
     */
    fun tryPillageImprovement(unitView: MapUnitView, onlyPillageToHeal: Boolean = false): Boolean {
        if (unitView.isCivilian()) return false
        val civ = unitView.civ()
        val tilesThatCanWalkToAndThenPillage = unitView.getDistanceToTiles()
            .filter { it.value < unitView.currentMovement }.keys
            .filter { unitView.canMoveTo(it) && unitView.canPillage(it)
                    && (it.canPillageTileImprovement()
                    || (!onlyPillageToHeal && it.canPillageRoad() && it.getRoadOwner() != null && civ.isAtWarWith(it.getRoadOwner()!!))) }

        if (tilesThatCanWalkToAndThenPillage.isEmpty()) return false
        val tileToPillage = tilesThatCanWalkToAndThenPillage.maxBy { unitView.getDefensiveBonusOn(it, includeImprovementBonus = false) }
        if (unitView.getTile() != tileToPillage)
            unitView.tryMoveToTile(tileToPillage)

        if (unitView.getTile() != tileToPillage) return false

        return unitView.tryInvokeUnitAction(UnitActionType.Pillage)
    }

    /** Move towards the closest attackable enemy of the [unit].
     *
     *  Limited by [CLOSE_ENEMY_TURNS_AWAY_LIMIT] and [CLOSE_ENEMY_TILES_AWAY_LIMIT].
     *  Tiles attack from which would result in instant death of the [unit] are ignored. */
    private fun tryAdvanceTowardsCloseEnemy(unitView: MapUnitView): Boolean {
        val unit = unitView.getUnit()
        // this can be sped up if we check each layer separately
        val unitDistanceToTiles = unit.movement.getMovementToTilesAtPosition(
                unit.getTile().position,
                unit.getMaxMovement() * CLOSE_ENEMY_TURNS_AWAY_LIMIT
        )
        val closeEnemy = TargetHelper.getAttackableEnemies(unit, unitDistanceToTiles, tilesToCheck = unit.getTile().getTilesInDistance(CLOSE_ENEMY_TILES_AWAY_LIMIT).toList())
            .filterNot { unitView.getBaseUnit().isRanged() && it.tileToAttack.isCityCenter() && it.tileToAttack.getCity()!!.health == 1 } // occurs fairly often probably because AI dumb
            .filter { unit.getDamageFromTerrain(it.tileToAttackFrom) <= 0 }  // Don't attack from a mountain or near enemy citadels
            .filter {
            // Ignore units that would 1-shot you if you attacked
            BattleDamage.calculateDamageToAttacker(MapUnitCombatant(unit), Battle.getMapCombatantOfTile(it.tileToAttack)!!) < unitView.unitHealth }
            .minByOrNull { it.tileToAttack.aerialDistanceTo(unit.getTile()) }

        if (closeEnemy != null) {
            unit.movement.headTowards(closeEnemy.tileToAttackFrom)
            return true
        }
        return false
    }

    /**
     * Tries to move the unit to the closest city that is close to a target civilization
     */
    private fun tryPrepare(unitView: MapUnitView): Boolean {
        val unit = unitView.getUnit()
        val civInfo = unit.civ

        @Readonly
        fun hasPreparationFlag(targetCiv: Civilization): Boolean {
            val diploManager = civInfo.getDiplomacyManager(targetCiv)!!
            if (diploManager.hasFlag(DiplomacyFlags.Denunciation)
                    || diploManager.otherCivDiplomacy().hasFlag(DiplomacyFlags.Denunciation)) return true
            if (diploManager.hasFlag(DiplomacyFlags.WaryOf) && diploManager.getFlag(DiplomacyFlags.WaryOf) < 0) return true
            return false
        }

        val hostileCivs = civInfo.getKnownCivs().filter { it.isAtWarWith(civInfo) || hasPreparationFlag(it) }.toSet()
        val closeCities = ThreatManager.getNeighboringCitiesOfOtherCivs(unitView.civ()).filter { it.second.owningCiv().getCiv() in hostileCivs }
        val closestDistance = closeCities.minOfOrNull { it.first.getCenterTile().aerialDistanceTo(it.second.getCenterTile()) }
            ?: return false
        val citiesToDefend = closeCities
            .filter { it.first.getCenterTile().aerialDistanceTo(it.second.getCenterTile()) <= closestDistance + 2 }
            .map { it.first }
            .distinct() // Remove duplicate cities
            .sortedBy { unitView.getTile().aerialDistanceTo(it.getCenterTile()) }

        // Move to the closest city with a tile we can enter nearby
        for (city in citiesToDefend) {
            if (unitView.getTile().aerialDistanceTo(city.getCenterTile()) <= 2) return true
            val tileToMoveTo = city.getCenterTile().getExploredTilesInDistance(2)
                .firstOrNull { unitView.canMoveTo(it) && unitView.canReach(it) } ?: continue
            unitView.tryHeadTowards(tileToMoveTo)
            return true
        }
        return false
    }

    private fun tryAccompanySettlerOrGreatPerson(unitView: MapUnitView): Boolean {
        val distanceToTiles = unitView.getDistanceToTiles()
        val settlerOrGreatPersonToAccompany = unitView.civ().getUnits()
            .firstOrNull {
                val tile = it.getTile()
                it.isCivilian() &&
                        (it.hasUnique(UniqueType.FoundCity) || it.getMatchingUniques(UniqueType.GreatPerson).any())
                        && !it.hasUnique(UniqueType.StrengthBonusInRadius) // Exlude great generals, as they move independently after all military units
                        && (tile == unitView.getTile() || tile.militaryUnit == null && unitView.canMoveTo(tile))
                        && distanceToTiles.containsKey(tile)
            } ?: return false
        unitView.tryHeadTowards(settlerOrGreatPersonToAccompany.getTile())
        return true
    }

    private fun tryHeadTowardsOurSiegedCity(unitView: MapUnitView): Boolean {
        val siegedCities = unitView.civ().cities()
                .asSequence()
                .filter { it.getHealth() < it.getMaxHealth() }

        if (siegedCities.any { it.getCenterTile().aerialDistanceTo(unitView.getTile()) <= 2 })
            return false

        val reachableTileNearSiegedCity = siegedCities
                .flatMap { it.getCenterTile().getExploredTilesAtDistance(2) }
                .sortedBy { it.aerialDistanceTo(unitView.getTile()) }
                .firstOrNull { unitView.canMoveTo(it) && unitView.canReach(it)
                        && unitView.getDamageFromTerrain(it) <= 0 } // Avoid ending up on damaging terrain

        if (reachableTileNearSiegedCity != null) {
            unitView.tryHeadTowards(reachableTileNearSiegedCity)
        }
        return !unitView.hasMovement()
    }


    fun tryEnterOwnClosestCity(unitView: MapUnitView): Boolean {
        val closestCity = unitView.civ().cities()
            .asSequence()
            .sortedBy { it.getCenterTile().aerialDistanceTo(unitView.getTile()) }
            .firstOrNull { unitView.canReach(it.getCenterTile()) }
          ?: return false // Panic!

        unitView.tryHeadTowards(closestCity.getCenterTile())
        return true
    }

    fun tryBombardEnemy(city: City): Boolean {
        if (!city.canBombard()) return false
        val enemy = chooseBombardTarget(city)
            ?: return false
        Battle.attack(CityCombatant(city), enemy)
        return true
    }

    @Readonly
    private fun chooseBombardTarget(city: City): ICombatant? {
        val targets = TargetHelper.getBombardableTiles(city).map { Battle.getMapCombatantOfTile(it)!! }
            .filterNot { it is MapUnitCombatant && it.isCivilian() }
            // Don't bombard civilians (they're more efficiently captured/killed by military units if they're indeed this close)
        if (targets.none()) return null
        return targets.maxByOrNull { BattleDamage.calculateDamageToDefender(CityCombatant(city), it) }
    }

    private fun tryTakeBackCapturedCity(unitView: MapUnitView): Boolean {
        val unit = unitView.getUnit()
        var capturedCities = unit.civ.getKnownCivs() // This is a Sequence
                .flatMap { it.cities.asSequence() }
                .filter {
                    unit.civ.isAtWarWith(it.civ) &&
                            unit.civ == it.foundingCivObject &&
                            it.isInResistance() &&
                            it.health < it.getMaxHealth()
                } //Most likely just been captured


        if (unitView.getBaseUnit().isRanged()) // ranged units don't harm capturable cities, waste of a turn
            capturedCities = capturedCities.filterNot { it.health == 1 }

        val closestReachableCapturedCity = capturedCities
                .map { it.getCenterTile() }
                .sortedBy { it.aerialDistanceTo(unit.getTile()) }
                .firstOrNull { unit.movement.canReach(it) }

        if (closestReachableCapturedCity != null) {
            return HeadTowardsEnemyCityAutomation.headTowardsEnemyCity(
                unitView,
                closestReachableCapturedCity,
                // This should be cached after the `canReach` call above.
                unit.movement.getShortestPath(closestReachableCapturedCity)
            )
        }
        return false
    }

    private fun tryGarrisoningLandUnit(unitView: MapUnitView): Boolean {
        if (unitView.getBaseUnit().isWaterUnit) return false // Water units don't count for Garrison bonus
        val citiesWithoutGarrison = unitView.civ().cities().filter {
            val centerTile = it.getCenterTile()
            centerTile.militaryUnit == null
                    && unitView.canMoveTo(centerTile)
        }

        val unitTile = unitView.getTile()
        val citiesToTry = if (!unitView.civ().isAtWar()) {
            if (unitTile.isCityCenter()) return true // It's always good to have a unit in the city center, so if you haven't found anyone around to attack, forget it.
            citiesWithoutGarrison.asSequence()
        } else {
            if (unitTile.isCityCenter() &&
                    isCityThatNeedsDefendingInWartime(unitTile.getCity()!!)) return true
            val citiesWithoutGarrisonThatNeedDefending = citiesWithoutGarrison.asSequence()
                    .filter { isCityThatNeedsDefendingInWartime(it) }
            if (citiesWithoutGarrisonThatNeedDefending.any()) citiesWithoutGarrisonThatNeedDefending
            else citiesWithoutGarrison.asSequence()
        }

        val closestReachableCityNeedsDefending = citiesToTry
            .sortedBy { it.getCenterTile().aerialDistanceTo(unitTile) }
            .firstOrNull { unitView.canReach(it.getCenterTile()) }
            ?: return false
        unitView.tryHeadTowards(closestReachableCityNeedsDefending.getCenterTile())
        return true
    }
    @Readonly
    private fun isCityThatNeedsDefendingInWartime(city: ForeignCityView): Boolean {
        if (city.getHealth() < city.getMaxHealth()) return true // this city is under attack!
        for (enemyCivCity in city.owningCiv().getCivsAtWarWith().flatMap { it.cities() })
            if (city.getCenterTile().aerialDistanceTo(enemyCivCity.getCenterTile()) <= 5) return true // this is an edge city that needs defending
        return false
    }

    /** This is what a unit with the 'explore' action does.
    It also explores, but also has other functions, like healing if necessary. */
    fun automatedExplore(unitView: MapUnitView, cache: AutomationCache = AutomationCache()) {
        val unit = unitView.getUnit()
        if (tryGoToRuin(unitView) && (!unitView.hasMovement() || unit.isDestroyed)) return
        if (unitView.unitHealth < 80 && tryHealUnit(unitView, cache)) return
        if (tryExplore(unitView, cache)) return
        unit.civ.addNotification("${unit.shortDisplayName()} finished exploring.", MapUnitAction(unit), NotificationCategory.Units, unit.name, "OtherIcons/Sleep")
        unit.action = null
    }


}
