package com.unciv.view

import com.unciv.logic.battle.AirInterception
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.Nuke
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.PossibleUpgrade
import com.unciv.logic.map.mapunit.actions.UnitActions
import com.unciv.models.Counter
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.logic.map.mapunit.AvailablePromotion
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import yairm210.purity.annotations.Readonly

/** View of a [MapUnit] from the perspective of [viewer] via [gameView]. */
class MapUnitView internal constructor(
    unit: MapUnit,
    viewer: Civilization,
    spectatorMode: Boolean,
    gameView: GameView,
) : ForeignMapUnitView(unit, viewer, spectatorMode, gameView) {
    val due: Boolean get() = unit.due

    val xp: Int get() = unit.promotions.XP
    @Readonly fun xpForNextPromotion(): Int = unit.promotions.xpForNextPromotion()
    @Readonly fun canBePromoted(): Boolean = unit.promotions.canBePromoted()
    @Readonly fun getPromotionNames(): Set<String> = unit.promotions.promotions
    /** The unit's promotions as objects, in json order. */
    @Readonly fun getPromotions(): Sequence<Promotion> = unit.promotions.getPromotions(sorted = true)
    /** Upgrades of this unit worth showing (normal ones, not ruins-only), see [PossibleUpgrade.isShownToPlayer] */
    @Readonly fun getPossibleUpgrades(): List<PossibleUpgrade> = unit.upgrade.getUpgradesOfKind(isSpecial = false)
    /** `true` if a regular upgrade to [baseUnit] could be performed right now */
    @Readonly fun canUpgradeTo(baseUnit: BaseUnit): Boolean = getPossibleUpgrades()
        .any { it.upgradedUnit == baseUnit && unit.upgrade.canPerformUpgrade(it, isFree = false) }
    /** Performs a regular upgrade to [baseUnit] if [canUpgradeTo] allows it
     *  @return whether an upgrade was performed */
    fun tryUpgradeTo(baseUnit: BaseUnit): Boolean {
        val upgrade = getPossibleUpgrades()
            .firstOrNull { it.upgradedUnit == baseUnit && unit.upgrade.canPerformUpgrade(it, isFree = false) }
            ?: return false
        unit.upgrade.performUpgrade(upgrade, isFree = false)
        return true
    }
    @Readonly fun isInOwnTerritory(): Boolean = unit.currentTile.getOwner() == unit.civ
    @Readonly fun hasUniqueToBuildImprovements(): Boolean = unit.cache.hasUniqueToBuildImprovements
    @Readonly fun hasUniqueToCreateWaterImprovements(): Boolean = unit.cache.hasUniqueToCreateWaterImprovements
    @Readonly fun hasStrengthBonusInRadiusUnique(): Boolean = unit.cache.hasStrengthBonusInRadiusUnique
    @Readonly fun hasCitadelPlacementUnique(): Boolean = unit.cache.hasCitadelPlacementUnique
    @Readonly fun getAvailablePromotions(): List<AvailablePromotion> = unit.promotions.getPromotionTreeCandidates()
    @Readonly fun canAffordPromotions(count: Int): Boolean = unit.promotions.canAffordPromotions(count)
    /** `true` if this unit stands in a non-puppet city of its own civ, so its promotions can be saved as the city's default. */
    @Readonly fun canSaveDefaultPromotions(): Boolean {
        val city = unit.currentTile.getCity() ?: return false
        return city.civ == unit.civ && !city.isPuppet
    }

    @Readonly override fun civ(): CivView = gameView.getCivView(unit.civ)

    @Readonly fun getOtherEscortUnit(): MapUnitView? = unit.getOtherEscortUnit()?.let { gameView.getMapUnitView(it) }
    // All "prepare and then choose tile" logic is actually UI stuff, and should be migrated out of logic layer
    @Readonly fun isPreparingParadrop(): Boolean = unit.isPreparingParadrop()
    // This is pure UI and should be migrated somewhere where it can be shared by both its usages
    @Readonly fun getMovementString(): String = unit.getMovementString()
    @Readonly fun getMovementDestination(): TileView = gameView.tileMapView.getTile(unit.getMovementDestination())
    val action: String? get() = unit.action
    @Readonly fun isMoving(): Boolean = unit.isMoving()
    @Readonly fun isExploring(): Boolean = unit.isExploring()
    @Readonly fun isEscorting(): Boolean = unit.isEscorting()
    @Readonly fun isSleeping(): Boolean = unit.isSleeping()
    @Readonly fun isAutomated(): Boolean = unit.isAutomated()
    @Readonly fun isSetUpForSiege(): Boolean = unit.isSetUpForSiege()
    @Readonly fun canBuildImprovement(improvement: TileImprovement): Boolean = unit.canBuildImprovement(improvement)
    @Readonly fun getTurnsToBuild(improvement: TileImprovement): Int = improvement.getTurnsToBuild(unit.civ, unit)
    @Readonly fun getStockpiledResourceRequirements(improvement: TileImprovement): Counter<String> =
        improvement.getStockpiledResourceRequirements(unit.cache.state)
    @Readonly fun getImprovementInProgress(): String? = unit.getTile().improvementInProgress
    @Readonly fun canBuildCurrentImprovement(): Boolean {
        val tile = unit.getTile()
        return tile.improvementInProgress != null && unit.canBuildImprovement(tile.getTileImprovementInProgress()!!)
    }
    /** `true` if [unit] was removed from its tile (captured, killed) since being selected. */
    @Readonly fun hasDisappeared(): Boolean = unit !in unit.getTile().getUnits()

    /** @throws com.unciv.logic.map.mapunit.movement.UnitMovement.UnreachableDestinationException if no tile can be reached this turn towards [targetTileView] */
    @Readonly fun getTileToMoveToThisTurn(targetTileView: TileView): TileView =
        gameView.tileMapView.getTile(unit.movement.getTileToMoveToThisTurn(targetTileView.unwrap()))
    @Readonly fun getPathToTile(tileView: TileView): List<TileView> =
        unit.movement.getDistanceToTiles().getPathToTile(tileView.unwrap()).map { gameView.tileMapView.getTile(it) }
    @Readonly fun getRoadPath(tileView: TileView): List<TileView>? =
        unit.movement.getRoadPath(tileView.unwrap())?.map { gameView.tileMapView.getTile(it) }
    @Readonly fun canReach(tileView: TileView): Boolean = unit.movement.canReach(tileView.unwrap())
    @Readonly fun getShortestPath(tileView: TileView): List<TileView> =
        unit.movement.getShortestPath(tileView.unwrap()).map { gameView.tileMapView.getTile(it) }
    @Readonly fun canSwapTo(tileView: TileView): Boolean = unit.movement.canUnitSwapTo(tileView.unwrap())
    // All "prepare and then choose tile" logic is actually UI stuff, and should be migrated out of logic layer
    @Readonly fun isPreparingAirSweep(): Boolean = unit.isPreparingAirSweep()
    @Readonly fun canMoveTo(tileView: TileView): Boolean = unit.movement.canMoveTo(tileView.unwrap())
    // This reads as "logic leaking through to UI"
    @Readonly fun isUnknownTileWeShouldAssumeToBePassable(tileView: TileView): Boolean =
        unit.movement.isUnknownTileWeShouldAssumeToBePassable(tileView.unwrap())
    @Readonly fun isNuclearWeapon(): Boolean = unit.isNuclearWeapon()
    @Readonly fun getNukeBlastRadius(): Int = unit.getNukeBlastRadius()
    @Readonly fun cannotMove(): Boolean = unit.cache.cannotMove
    @Readonly fun isAutomatingRoadConnection(): Boolean = unit.isAutomatingRoadConnection()
    @Readonly fun rulesetHasRoadImprovement(): Boolean = unit.currentTile.ruleset.roadImprovement != null
    @Readonly fun getReachableTilesInCurrentTurn(): List<TileView> =
        unit.movement.getReachableTilesInCurrentTurn().map { gameView.tileMapView.getTile(it) }.toList()
    @Readonly fun getUnitSwappableTiles(): List<TileView> =
        unit.movement.getUnitSwappableTiles().map { gameView.tileMapView.getTile(it) }.toList()
    @Readonly fun getValidRoadConnectionTiles(): List<TileView> =
        unit.civ.gameInfo.tileMap.tileList.filter { MapPathing.isValidRoadPathTile(unit.civ, it) }
            .map { gameView.tileMapView.getTile(it) }
    @Readonly fun getTilesInAttackRange(): List<TileView> =
        unit.getTile().getTilesInDistanceRange(IntRange(1, unit.getRange())).map { gameView.tileMapView.getTile(it) }.toList()
    @Readonly fun getAttackableEnemies(): List<AttackableTileView> =
        TargetHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
            .map { AttackableTileView(it, viewer, spectatorMode, gameView) }
    /** `null` if the unit isn't currently pathing a road; otherwise the tiles still to come on that path. */
    @Readonly fun getFutureAutomatedRoadConnectionTiles(): List<TileView>? {
        val path = unit.automatedRoadConnectionPath ?: return null
        val currTileIndex = path.indexOf(unit.currentTile.position)
        if (currTileIndex == -1) return emptyList()
        return path.filterIndexed { index, _ -> index > currTileIndex }
            .mapNotNull { gameView.tileMapView.getTile(it) }
    }

    // Actions
    fun trySwapMoveToTile(tileView: TileView, keepEscorting: Boolean = false): Boolean {
        unit.movement.swapMoveToTile(tileView.unwrap(), keepEscorting)
        return true
    }
    /** Clears the improvement queue of the tile this unit stands on. */
    fun tryStopWorkingOnImprovement(): Boolean {
        unit.getTile().stopWorkingOnImprovement()
        return true
    }
    /** Starts working on [improvement] on the tile this unit stands on, and queues [queuedImprovement] after it if given. */
    fun tryStartWorkingOnImprovement(improvement: TileImprovement, queuedImprovement: TileImprovement? = null): Boolean {
        val tile = unit.getTile()
        tile.startWorkingOnImprovement(improvement, unit.civ, unit)
        if (queuedImprovement != null)
            tile.queueImprovement(queuedImprovement, unit.civ, unit)
        return true
    }
    fun trySetInstanceName(name: String?): Boolean {
        unit.instanceName = name
        return true
    }
    fun tryAddPromotion(promotionName: String): Boolean {
        unit.promotions.addPromotion(promotionName)
        return true
    }
    /** Remembers this unit's promotions as the default for new units of its type built in the city it stands in. */
    fun trySaveDefaultPromotions(): Boolean {
        val city = unit.currentTile.getCity() ?: return false
        city.unitShouldUseSavedPromotion[unit.baseUnit.name] = true
        city.unitToPromotions[unit.baseUnit.name] = unit.promotions
        return true
    }
    fun tryDisband(): Boolean {
        unit.disband()
        return true
    }
    fun tryResetAction(): Boolean {
        unit.action = null
        return true
    }
    fun tryHeadTowards(tileView: TileView): Boolean {
        unit.movement.headTowards(tileView.unwrap())
        return true
    }
    fun trySetMoveToAction(tileView: TileView): Boolean {
        val position = tileView.position()
        unit.action = "moveTo ${position.x},${position.y}"
        return true
    }
    /** Moves [unit] to [attackableTileView], handles siege setup, and returns `true` if an attack is still possible. */
    fun tryMovePreparingAttack(attackableTileView: AttackableTileView, tryHealPillage: Boolean = false): Boolean =
        Battle.movePreparingAttack(MapUnitCombatant(unit), attackableTileView.unwrap(), tryHealPillage)
    /** Meant to be called only after all prerequisite checks (e.g. [tryMovePreparingAttack]) have been done. */
    fun attackOrNuke(attackableTileView: AttackableTileView): Battle.DamageDealt =
        Battle.attackOrNuke(MapUnitCombatant(unit), attackableTileView.unwrap())
    @Readonly fun mayUseNuke(targetTileView: TileView): Boolean = Nuke.mayUseNuke(MapUnitCombatant(unit), targetTileView.unwrap())
    fun tryNuke(targetTileView: TileView): Boolean {
        Nuke.NUKE(MapUnitCombatant(unit), targetTileView.unwrap())
        return true
    }
    fun tryAirSweep(targetTileView: TileView): Boolean {
        AirInterception.airSweep(MapUnitCombatant(unit), targetTileView.unwrap())
        return true
    }
    fun tryInvokeUnitAction(unitActionType: UnitActionType): Boolean = UnitActions.invokeUnitAction(unit, unitActionType)
    fun tryMoveToTile(tileView: TileView): Boolean {
        unit.movement.moveToTile(tileView.unwrap())
        return true
    }

    @Readonly fun getAirSweepAttackModifiers(): Map<String, Int> = BattleDamage.getAirSweepAttackModifiers(MapUnitCombatant(unit))
}
