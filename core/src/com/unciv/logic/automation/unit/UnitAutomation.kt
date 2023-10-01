package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
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
                && tile.getTilesInDistance(3) .none { containsEnemyMilitaryUnit(unit, it) } // don't walk in range of enemy units
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

    fun wander(unit: MapUnit, stayInTerritory: Boolean = false, tilesToAvoid:Set<Tile> = setOf()) {
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
        val isHuman = unit.civ.isHuman()
        if (!UncivGame.Current.settings.automatedUnitsCanUpgrade && isHuman) return false
        if (unit.baseUnit.upgradesTo == null) return false
        val upgradedUnit = unit.upgrade.getUnitToUpgradeTo()
        if (!upgradedUnit.isBuildable(unit.civ)) return false // for resource reasons, usually

        if (upgradedUnit.getResourceRequirementsPerTurn().keys.any { !unit.baseUnit.requiresResource(it) }) {
            // The upgrade requires new resource types, so check if we are willing to invest them
            if (!Automation.allowSpendingResource(unit.civ, upgradedUnit)) return false
        }

        val upgradeAction = UnitActionsUpgrade.getUpgradeAction(unit)
            ?: return false

        upgradeAction.action?.invoke()
        return unit.isDestroyed // a successful upgrade action will destroy this unit
    }

    fun automateUnitMoves(unit: MapUnit) {
        check(!unit.civ.isBarbarian()) { "Barbarians is not allowed here." }

        // Might die next turn - move!
        if (unit.health <= unit.getDamageFromTerrain() && tryHealUnit(unit)) return


        if (unit.isCivilian()) {
            automateCivilianUnit(unit)
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
            automateCivilianUnit(unit)
            return
        }

        if (unit.baseUnit.isAirUnit() && unit.canIntercept())
            return SpecificUnitAutomation.automateFighter(unit)

        if (unit.baseUnit.isAirUnit() && !unit.baseUnit.isNuclearWeapon())
            return SpecificUnitAutomation.automateBomber(unit)

        if (unit.baseUnit.isNuclearWeapon())
            return SpecificUnitAutomation.automateNukes(unit)

        if (unit.hasUnique(UniqueType.SelfDestructs))
            return SpecificUnitAutomation.automateMissile(unit)

        if (tryGoToRuinAndEncampment(unit) && unit.currentMovement == 0f) return

        if (tryUpgradeUnit(unit)) return

        // Accompany settlers
        if (tryAccompanySettlerOrGreatPerson(unit)) return

        if (tryHeadTowardsOurSiegedCity(unit)) return

        if (unit.health < 50 && tryHealUnit(unit)) return // do nothing but heal

        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit)) return

        // if there is an attackable unit in the vicinity, attack!
        if (tryAttacking(unit)) return

        if (tryTakeBackCapturedCity(unit)) return

        // Focus all units without a specific target on the enemy city closest to one of our cities
        if (tryHeadTowardsEnemyCity(unit)) return

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

    private fun automateCivilianUnit(unit: MapUnit) {
        if (tryRunAwayIfNeccessary(unit)) return

        if (unit.currentTile.isCityCenter() && unit.currentTile.getCity()!!.isCapital()
                && !unit.hasUnique(UniqueType.AddInCapital)
                && unit.civ.units.getCivUnits().any { unit.hasUnique(UniqueType.AddInCapital) }){
            // First off get out of the way, then decide if you actually want to do something else
            val tilesCanMoveTo = unit.movement.getDistanceToTiles()
                .filter { unit.movement.canMoveTo(it.key) }
            if (tilesCanMoveTo.isNotEmpty())
                unit.movement.moveToTile(tilesCanMoveTo.minByOrNull { it.value.totalDistance }!!.key)
        }

        val tilesWhereWeWillBeCaptured = unit.currentTile.getTilesInDistance(5)
            .mapNotNull { it.militaryUnit }
            .filter { it.civ.isAtWarWith(unit.civ) }
            .flatMap { it.movement.getReachableTilesInCurrentTurn() }
            .filter { it.militaryUnit?.civ != unit.civ }
            .toSet()

        if (unit.hasUnique(UniqueType.FoundCity))
            return SpecificUnitAutomation.automateSettlerActions(unit, tilesWhereWeWillBeCaptured)

        if (unit.cache.hasUniqueToBuildImprovements)
            return unit.civ.getWorkerAutomation().automateWorkerAction(unit, tilesWhereWeWillBeCaptured)

        if (unit.cache.hasUniqueToCreateWaterImprovements){
            if (!unit.civ.getWorkerAutomation().automateWorkBoats(unit))
                tryExplore(unit)
            return
        }

        if (unit.hasUnique(UniqueType.MayFoundReligion)
                && unit.civ.religionManager.religionState < ReligionState.Religion
                && unit.civ.religionManager.mayFoundReligionAtAll()
        )
            return SpecificUnitAutomation.foundReligion(unit)

        if (unit.hasUnique(UniqueType.MayEnhanceReligion)
                && unit.civ.religionManager.religionState < ReligionState.EnhancedReligion
                && unit.civ.religionManager.mayEnhanceReligionAtAll(unit)
        )
            return SpecificUnitAutomation.enhanceReligion(unit)

        // We try to add any unit in the capital we can, though that might not always be desirable
        // For now its a simple option to allow AI to win a science victory again
        if (unit.hasUnique(UniqueType.AddInCapital))
            return SpecificUnitAutomation.automateAddInCapital(unit)

        //todo this now supports "Great General"-like mod units not combining 'aura' and citadel
        // abilities, but not additional capabilities if automation finds no use for those two
        if (unit.cache.hasStrengthBonusInRadiusUnique
                && SpecificUnitAutomation.automateGreatGeneral(unit))
            return
        if (unit.cache.hasCitadelPlacementUnique && SpecificUnitAutomation.automateCitadelPlacer(unit))
            return
        if (unit.cache.hasCitadelPlacementUnique || unit.cache.hasStrengthBonusInRadiusUnique)
            return SpecificUnitAutomation.automateGreatGeneralFallback(unit)

        if (unit.civ.religionManager.maySpreadReligionAtAll(unit))
            return SpecificUnitAutomation.automateMissionary(unit)

        if (unit.hasUnique(UniqueType.PreventSpreadingReligion) || unit.canDoLimitedAction(Constants.removeHeresy))
            return SpecificUnitAutomation.automateInquisitor(unit)

        val isLateGame = isLateGame(unit.civ)
        // Great scientist -> Hurry research if late game
        if (isLateGame) {
            val hurriedResearch = UnitActions.invokeUnitAction(unit, UnitActionType.HurryResearch)
            if (hurriedResearch) return
        }

        // Great merchant -> Conduct trade mission if late game and if not at war.
        // TODO: This could be more complex to walk to the city state that is most beneficial to
        //  also have more influence.
        if (unit.hasUnique(UniqueType.CanTradeWithCityStateForGoldAndInfluence)
                // Don't wander around with the great merchant when at war. Barbs might also be a
                // problem, but hopefully by the time we have a great merchant, they're under control.
                && !unit.civ.isAtWar()
                && isLateGame
        ) {
            val tradeMissionCanBeConductedEventually =
                    SpecificUnitAutomation.conductTradeMission(unit)
            if (tradeMissionCanBeConductedEventually)
                return
        }

        // Great engineer -> Try to speed up wonder construction if late game
        if (isLateGame &&
                (unit.hasUnique(UniqueType.CanSpeedupConstruction)
                        || unit.hasUnique(UniqueType.CanSpeedupWonderConstruction))) {
            val wonderCanBeSpedUpEventually = SpecificUnitAutomation.speedupWonderConstruction(unit)
            if (wonderCanBeSpedUpEventually)
                return
        }


        // This has to come after the individual abilities for the great people that can also place
        // instant improvements (e.g. great scientist).
        if (unit.hasUnique(UniqueType.ConstructImprovementInstantly)
        ) {
            // catch great prophet for civs who can't found/enhance/spread religion
            // includes great people plus moddable units
            val improvementCanBePlacedEventually =
                    SpecificUnitAutomation.automateImprovementPlacer(unit)
            if (!improvementCanBePlacedEventually)
                UnitActions.invokeUnitAction(unit, UnitActionType.StartGoldenAge)
        }

        // TODO: The AI tends to have a lot of great generals. Maybe there should be a cutoff
        //  (depending on number of cities) and after that they should just be used to start golden
        //  ages?

        return // The AI doesn't know how to handle unknown civilian units
    }

    private fun isLateGame(civ: Civilization): Boolean {
        val researchCompletePercent =
                (civ.tech.researchedTechnologies.size * 1.0f) / civ.gameInfo.ruleset.technologies.size
        return researchCompletePercent >= 0.8f
    }

    /** @return true only if the unit has 0 movement left */
    private fun tryAttacking(unit: MapUnit): Boolean {
        repeat(unit.maxAttacksPerTurn() - unit.attacksThisTurn) {
            if (BattleHelper.tryAttackNearbyEnemy(unit)) return true
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

    private fun tryHealUnit(unit: MapUnit): Boolean {
        if (unit.baseUnit.isRanged() && unit.hasUnique(UniqueType.HealsEvenAfterAction))
            return false // will heal anyway, and attacks don't hurt

        if (tryPillageImprovement(unit)) return true
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        if (unitDistanceToTiles.isEmpty()) return true // can't move, so...

        val currentUnitTile = unit.getTile()

        val nearbyRangedEnemyUnits = unit.currentTile.getTilesInDistance(3)
                .flatMap { tile -> tile.getUnits().filter { unit.civ.isAtWarWith(it.civ) } }

        val tilesInRangeOfAttack = nearbyRangedEnemyUnits
                .flatMap { it.getTile().getTilesInDistance(it.getRange()) }

        val tilesWithinBombardmentRange = unit.currentTile.getTilesInDistance(3)
                .filter { it.isCityCenter() && it.getCity()!!.civ.isAtWarWith(unit.civ) }
                .flatMap { it.getTilesInDistance(it.getCity()!!.range) }

        val tilesWithTerrainDamage = unit.currentTile.getTilesInDistance(3)
                .filter { unit.getDamageFromTerrain(it) > 0 }

        val dangerousTiles = (tilesInRangeOfAttack + tilesWithinBombardmentRange + tilesWithTerrainDamage).toHashSet()


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

    fun tryPillageImprovement(unit: MapUnit): Boolean {
        if (unit.isCivilian()) return false
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val tilesThatCanWalkToAndThenPillage = unitDistanceToTiles
            .filter { it.value.totalDistance < unit.currentMovement }.keys
            .filter { unit.movement.canMoveTo(it) && UnitActionsPillage.canPillage(unit, it)
                    && (it.canPillageTileImprovement()
                    || (it.canPillageRoad() && it.getRoadOwner() != null && unit.civ.isAtWarWith(it.getRoadOwner()!!)))}

        if (tilesThatCanWalkToAndThenPillage.isEmpty()) return false
        val tileToPillage = tilesThatCanWalkToAndThenPillage.maxByOrNull { it.getDefensiveBonus() }!!
        if (unit.getTile() != tileToPillage)
            unit.movement.moveToTile(tileToPillage)

        UnitActionsPillage.getPillageAction(unit)?.action?.invoke()
        return unit.currentMovement == 0f
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

    fun tryHeadTowardsEnemyCity(unit: MapUnit): Boolean {
        if (unit.civ.cities.isEmpty()) return false

        // only focus on *attacking* 1 enemy at a time otherwise you'll lose on both fronts

        val enemies = unit.civ.getKnownCivs()
            .filter { unit.civ.isAtWarWith(it) && it.cities.isNotEmpty() }

        val closestEnemyCity = enemies
            .mapNotNull { NextTurnAutomation.getClosestCities(unit.civ, it) }
            .minByOrNull { it.aerialDistance }?.city2
          ?: return false // no attackable cities found

        // Our main attack target is the closest city, but we're fine with deviating from that a bit
        var enemyCitiesByPriority = closestEnemyCity.civ.cities
            .associateWith { it.getCenterTile().aerialDistanceTo(closestEnemyCity.getCenterTile()) }
            .asSequence().filterNot { it.value > 10 } // anything 10 tiles away from the target is irrelevant
            .sortedBy { it.value }.map { it.key } // sort the list by closeness to target - least is best!

        if (unit.baseUnit.isRanged()) // ranged units don't harm capturable cities, waste of a turn
            enemyCitiesByPriority = enemyCitiesByPriority.filterNot { it.health == 1 }

        val closestReachableEnemyCity = enemyCitiesByPriority
                .firstOrNull { unit.movement.canReach(it.getCenterTile()) }

        if (closestReachableEnemyCity != null) {
            return headTowardsEnemyCity(
                unit,
                closestReachableEnemyCity.getCenterTile(),
                // This should be cached after the `canReach` call above.
                unit.movement.getShortestPath(closestReachableEnemyCity.getCenterTile())
            )
        }
        return false
    }


    private fun headTowardsEnemyCity(
        unit: MapUnit,
        closestReachableEnemyCity: Tile,
        shortestPath: List<Tile>
    ): Boolean {
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val unitRange = unit.getRange()

        if (unitRange > 2) { // long-ranged unit, should never be in a bombardable position
            val tilesInBombardRange = closestReachableEnemyCity.getTilesInDistance(2).toSet()
            val tileToMoveTo =
                    unitDistanceToTiles.asSequence()
                            .filter {
                                it.key.aerialDistanceTo(closestReachableEnemyCity) <=
                                        unitRange && it.key !in tilesInBombardRange
                                        && unit.getDamageFromTerrain(it.key) <= 0 // Don't set up on a mountain
                            }
                        .minByOrNull { it.value.totalDistance }?.key

            // move into position far away enough that the bombard doesn't hurt
            if (tileToMoveTo != null) {
                unit.movement.headTowards(tileToMoveTo)
                return true
            }
            return false
        }

        // None of the stuff below is relevant if we're still quite far away from the city, so we
        // short-circuit here for performance reasons.
        val minDistanceFromCityToConsiderForLandingArea = 3
        val maxDistanceFromCityToConsiderForLandingArea = 5
        if (unit.currentTile.aerialDistanceTo(closestReachableEnemyCity) > maxDistanceFromCityToConsiderForLandingArea
                // Even in the worst case of only being able to move 1 tile per turn, we would still
                // not overshoot.
                && shortestPath.size > minDistanceFromCityToConsiderForLandingArea ) {
            unit.movement.moveToTile(shortestPath[0])
            return true
        }

        val ourUnitsAroundEnemyCity = closestReachableEnemyCity.getTilesInDistance(6)
            .flatMap { it.getUnits() }
            .filter { it.isMilitary() && it.civ == unit.civ }

        val city = closestReachableEnemyCity.getCity()!!
        val cityCombatant = CityCombatant(city)

        val expectedDamagePerTurn = ourUnitsAroundEnemyCity
            .map { BattleDamage.calculateDamageToDefender(MapUnitCombatant(it), cityCombatant) }
            .sum() // City heals 20 per turn

        if (expectedDamagePerTurn < city.health && // If we can take immediately, go for it
            (expectedDamagePerTurn <= 20 || city.health / (expectedDamagePerTurn-20) > 5)){ // otherwise check if we can take within a couple of turns

            // We won't be able to take this even with 5 turns of continuous damage!
            // don't head straight to the city, try to head to landing grounds -
            // this is against tha AI's brilliant plan of having everyone embarked and attacking via sea when unnecessary.
            val tileToHeadTo = closestReachableEnemyCity.getTilesInDistanceRange(3..5)
                    .filter { it.isLand && unit.getDamageFromTerrain(it) <= 0 } // Don't head for hurty terrain
                    .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                    .firstOrNull { (unit.movement.canMoveTo(it) || it == unit.currentTile) && unit.movement.canReach(it) }

            if (tileToHeadTo != null) { // no need to worry, keep going as the movement alg. says
                unit.movement.headTowards(tileToHeadTo)
            }
            return true
        }

        unit.movement.moveToTile(shortestPath[0]) // go for it!

        return true
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
            return headTowardsEnemyCity(
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

        fun isCityThatNeedsDefendingInWartime(city: City): Boolean {
            if (city.health < city.getMaxHealth()) return true // this city is under attack!
            for (enemyCivCity in unit.civ.diplomacy.values
                    .filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() }.flatMap { it.cities })
                if (city.getCenterTile().aerialDistanceTo(enemyCivCity.getCenterTile()) <= 5) return true // this is an edge city that needs defending
            return false
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

    /** Returns whether the civilian spends its turn hiding and not moving */
    private fun tryRunAwayIfNeccessary(unit: MapUnit): Boolean {
        // This is a little 'Bugblatter Beast of Traal': Run if we can attack an enemy
        // Cheaper than determining which enemies could attack us next turn
        val enemyUnitsInWalkingDistance = unit.movement.getDistanceToTiles().keys
            .filter { containsEnemyMilitaryUnit(unit, it) }

        if (enemyUnitsInWalkingDistance.isNotEmpty() && !unit.baseUnit.isMilitary()
            && unit.getTile().militaryUnit == null && !unit.getTile().isCityCenter()) {
            runAway(unit)
            return true
        }

        return false
    }

    private fun runAway(unit: MapUnit) {
        val reachableTiles = unit.movement.getDistanceToTiles()
        val enterableCity = reachableTiles.keys
            .firstOrNull { it.isCityCenter() && unit.movement.canMoveTo(it) }
        if (enterableCity != null) {
            unit.movement.moveToTile(enterableCity)
            return
        }
        val defensiveUnit = reachableTiles.keys
            .firstOrNull {
                it.militaryUnit != null && it.militaryUnit!!.civ == unit.civ && it.civilianUnit == null
            }
        if (defensiveUnit != null) {
            unit.movement.moveToTile(defensiveUnit)
            return
        }
        val tileFurthestFromEnemy = reachableTiles.keys
            .filter { unit.movement.canMoveTo(it) && unit.getDamageFromTerrain(it) < unit.health }
            .maxByOrNull { countDistanceToClosestEnemy(unit, it) }
            ?: return // can't move anywhere!
        unit.movement.moveToTile(tileFurthestFromEnemy)
    }


    private fun countDistanceToClosestEnemy(unit: MapUnit, tile: Tile): Int {
        for (i in 1..3)
            if (tile.getTilesAtDistance(i).any { containsEnemyMilitaryUnit(unit, it) })
                return i
        return 4
    }

    private fun containsEnemyMilitaryUnit(unit: MapUnit, tile: Tile) =
        tile.militaryUnit != null
        && tile.militaryUnit!!.civ.isAtWarWith(unit.civ)

}
