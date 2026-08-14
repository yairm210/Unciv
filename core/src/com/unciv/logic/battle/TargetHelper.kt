package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.automation.Timers.Companion.timeThis
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.PathsToTilesWithinTurn
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

object TargetHelper {
    @Readonly
    fun getAttackableEnemies(
        unit: MapUnit,
        unitDistanceToTiles: PathsToTilesWithinTurn,
        tilesToCheck: List<Tile>? = null,
        stayOnTile: Boolean = false
    ): ArrayList<AttackableTile> = timeThis("getAttackableEnemies") {
        val attackableTiles = ArrayList<AttackableTile>()
        if (unit.canMeleeAttack())
            for (tile in collectAttackableEnemies(unit, unitDistanceToTiles, tilesToCheck, stayOnTile, isRangedAttack = false))
                attackableTiles += tile
        if (unit.baseUnit.isRanged())
            for (tile in collectAttackableEnemies(unit, unitDistanceToTiles, tilesToCheck, stayOnTile, isRangedAttack = true))
                attackableTiles += tile
        return attackableTiles
    }

    /**
     * Picks one [AttackableTile] when several exist for the same target
     * (hybrid melee+ranged units). Prefers capturing a 1-HP city, then attacking
     * from the current tile, then ranged (no return damage).
     */
    @Readonly
    fun chooseAttackableTileAgainst(
        unit: MapUnit,
        tile: Tile,
        attackableEnemies: List<AttackableTile>
    ): AttackableTile? {
        val candidates = attackableEnemies.filter { it.tileToAttack == tile }
        if (candidates.size <= 1) return candidates.firstOrNull()
        return candidates.minWithOrNull(preferredAttackComparator(unit))
    }

    @Readonly
    fun preferredAttackableEnemies(unit: MapUnit, attackableEnemies: List<AttackableTile>): List<AttackableTile> =
        attackableEnemies.groupBy { it.tileToAttack }.mapNotNull { (tile, tiles) ->
            chooseAttackableTileAgainst(unit, tile, tiles)
        }

    @Readonly
    private fun preferredAttackComparator(unit: MapUnit) = compareBy<AttackableTile>(
        { attack ->
            val city = attack.tileToAttack.getCity()
            if (city != null && city.health == 1 && !attack.isRangedAttack) 0 else 1
        },
        { if (it.tileToAttackFrom == unit.currentTile) 0 else 1 },
        { if (it.isRangedAttack) 0 else 1 },
        { -it.movementLeftAfterMovingToAttackTile }
    )

    @Readonly
    private fun collectAttackableEnemies(
        unit: MapUnit,
        unitDistanceToTiles: PathsToTilesWithinTurn,
        tilesToCheck: List<Tile>?,
        stayOnTile: Boolean,
        isRangedAttack: Boolean
    ): ArrayList<AttackableTile> {
        val attackableTiles = ArrayList<AttackableTile>()
        val rangeOfAttack = unit.getRange()
        val unitMustBeSetUp = isRangedAttack && unit.hasUnique(UniqueType.MustSetUp)
        val tilesToAttackFrom = if (stayOnTile || unit.baseUnit.movesLikeAirUnits)
            sequenceOf(Pair(unit.currentTile, unit.currentMovement))
        else getTilesToAttackFromWhenUnitMoves(unitDistanceToTiles, unitMustBeSetUp, unit)

        val skipAdjacentRanged = isRangedAttack && unit.canMeleeAttack()
        val tilesWithEnemies: HashSet<Tile> = HashSet()
        val tilesWithoutEnemies: HashSet<Tile> = HashSet()
        for ((reachableTile, movementLeft) in tilesToAttackFrom) {  // tiles we'll still have energy after we reach there
            // If we are a melee unit that is escorting, we only want to be able to attack from this
            // tile if the escorted unit can also move into the tile we are attacking if we kill the enemy unit.
            if (!isRangedAttack && unit.isEscorting()) {
                val escortingUnit = unit.getOtherEscortUnit()!!
                if (!escortingUnit.movement.canReachInCurrentTurn(reachableTile)
                    || escortingUnit.currentMovement - escortingUnit.movement.getDistanceToTiles()[reachableTile]!!.totalMovement <= 0f)
                    continue
            }

            val tilesInAttackRange =
                if (!isRangedAttack) reachableTile.neighbors
                else if (unit.baseUnit.movesLikeAirUnits || unit.hasUnique(UniqueType.IndirectFire, checkCivInfoUniques = true))
                    reachableTile.getTilesInDistance(rangeOfAttack)
                else reachableTile.tileMap.getViewableTiles(reachableTile.position, rangeOfAttack, true).asSequence()

            for (tile in tilesInAttackRange) {
                when {
                    // Since military units can technically enter tiles with enemy civilians,
                    // some try to move to to the tile and then attack the unit it contains, which is silly
                    tile == reachableTile -> continue
                    skipAdjacentRanged && (tile.aerialDistanceTo(reachableTile) <= 1
                        || tile.aerialDistanceTo(unit.currentTile) <= 1) -> continue

                    tile in tilesWithEnemies -> attackableTiles += AttackableTile(
                        reachableTile,
                        tile,
                        movementLeft,
                        Battle.getMapCombatantOfTile(tile),
                        isRangedAttack
                    )
                    tile in tilesWithoutEnemies -> continue // avoid checking the same empty tile multiple times
                    tileContainsAttackableEnemy(unit, tile, tilesToCheck, isRangedAttack) || unit.isPreparingAirSweep() -> {
                        tilesWithEnemies += tile
                        attackableTiles += AttackableTile(
                            reachableTile, tile, movementLeft,
                            Battle.getMapCombatantOfTile(tile),
                            isRangedAttack
                        )
                    }
                    else -> tilesWithoutEnemies += tile
                }
            }
        }
        return attackableTiles
    }

    @Readonly
    private fun getTilesToAttackFromWhenUnitMoves(unitDistanceToTiles: PathsToTilesWithinTurn, unitMustBeSetUp: Boolean, unit: MapUnit) =
        unitDistanceToTiles.asSequence()
            .sortedWith {a,b -> a.value.totalMovement.compareTo(b.value.totalMovement) }
            .map { (tile, distance) ->
                val movementPointsToExpendAfterMovement = if (unitMustBeSetUp) 1 else 0
                val movementPointsToExpendHere =
                    if (unitMustBeSetUp && !unit.isSetUpForSiege()) 1 else 0
                val movementPointsToExpendBeforeAttack =
                    if (tile == unit.currentTile) movementPointsToExpendHere else movementPointsToExpendAfterMovement
                val movementLeft =
                    unit.currentMovement - distance.totalMovement - movementPointsToExpendBeforeAttack
                Pair(tile, movementLeft)
            }
            // still got leftover movement points after all that, to attack
            .filter { it.second > Constants.minimumMovementEpsilon }
            .filter {
                it.first == unit.getTile() || unit.movement.canMoveTo(it.first)
            }

    @Readonly
    private fun tileContainsAttackableEnemy(
        unit: MapUnit,
        tile: Tile,
        tilesToCheck: List<Tile>?,
        isRangedAttack: Boolean
    ): Boolean {
        if (tile !in (tilesToCheck ?: unit.civ.viewableTiles) ||
            !containsAttackableEnemy(tile, MapUnitCombatant(unit, isRangedAttack)))
            return false
        val mapCombatant = Battle.getMapCombatantOfTile(tile)

        return (isRangedAttack || mapCombatant !is MapUnitCombatant || !mapCombatant.unit.isCivilian() || unit.movement.canPassThrough(tile))
    }

    @Readonly
    fun containsAttackableEnemy(tile: Tile, combatant: ICombatant): Boolean {
        if (combatant is MapUnitCombatant && combatant.unit.isEmbarked() && !combatant.hasUnique(UniqueType.AttackOnSea)) {
            // Can't attack water units while embarked, only land
            if (tile.isWater || combatant.isRanged())
                return false
        }

        val tileCombatant = Battle.getMapCombatantOfTile(tile) ?: return false
        if (tileCombatant.getCivInfo() == combatant.getCivInfo()) return false
        // If the user automates units, one may capture the city before the user had a chance to decide what to do with it,
        //  and then the next unit should not attack that city
        if (tileCombatant is CityCombatant && tileCombatant.city.hasJustBeenConquered) return false
        if (!combatant.getCivInfo().isAtWarWith(tileCombatant.getCivInfo())) return false

        if (combatant is MapUnitCombatant && combatant.isLandUnit() && combatant.isMelee() && tile.isWater &&
            !combatant.getCivInfo().tech.unitsCanEmbark && !combatant.unit.cache.canMoveOnWater
        )
            return false

        
        if (combatant is MapUnitCombatant) {
            val gameContext = GameContext(
                unit = combatant.unit, tile = tile, 
                ourCombatant = combatant, theirCombatant = tileCombatant, combatAction = CombatAction.Attack)

            if (combatant.hasUnique(UniqueType.CannotAttack, gameContext))
                return false

            if (combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackUnits, gameContext).run {
                    any() && none { tileCombatant.matchesFilter(it.params[0]) }
                }
            )
                return false

            if (combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackTiles, gameContext).run {
                    any() && none { tile.matchesFilter(it.params[0]) }
                }
            )
                return false
        }

        // Only units with the right unique can view submarines (or other invisible units) from more then one tile away.
        // Garrisoned invisible units can be attacked by anyone, as else the city will be in invincible.
        if (tileCombatant.isInvisible(combatant.getCivInfo()) && !tile.isCityCenter()) {
            return combatant.getCivInfo().viewableInvisibleUnitsTiles.map { it.position }.contains(tile.position)
        }
        
        return true
    }

    /** Get a list of visible tiles which have something attackable */
    @Readonly
    fun getBombardableTiles(city: City): Sequence<Tile> =
            city.getCenterTile().getTilesInDistance(city.getBombardRange())
                    .filter { it.isVisible(city.civ) && containsAttackableEnemy(it, CityCombatant(city)) }

}
