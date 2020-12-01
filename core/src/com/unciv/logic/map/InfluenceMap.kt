package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.civilization.CivilizationInfo

object InfluenceMap {

    fun setInfluenceScore(viewingCiv: CivilizationInfo, tilesToCheck: List<TileInfo>? = null) {
        val viewable = viewingCiv.gameInfo.tileMap.values
        
        //Such a dirty hack, need to actually create an object that holds these for each Civ
        for (tile in viewable) {
            tile.enemyInfluenceScore = 0.0F
            tile.friendlyInfluenceScore = 0.0F
        }
        val tilesWithEnemies = (tilesToCheck ?: viewable)
                .filter { containsEnemyUnit(it, viewingCiv) }
        val tilesWithNeutralCombatant = (tilesToCheck ?: viewable)
                .filter { containsNeutralUnit(it, viewingCiv) }
        val tilesWithFriendlyCombatant = (tilesToCheck ?: viewable)
                .filter { containsFriendlyUnit(it, viewingCiv) }
        setEnemyTiles(tilesWithEnemies)
        setNeutralTiles(tilesWithNeutralCombatant)
        setFriendlyTiles(tilesWithFriendlyCombatant)

    }
    
    private fun setEnemyTiles(enemyTiles: List<TileInfo>) {
        for (enemyTile in enemyTiles) {
            for (unit in enemyTile.getUnits()) {
                if (unit.type.isMilitary()) {
                    enemyTile.enemyInfluenceScore += (unit.baseUnit.strength * (1 + enemyTile.getDefensiveBonus())) * (unit.health/100.0F)
                    if (unit.type.isMelee()) {
                        val tilesInMoveRange = unit.movement.getDistanceToTilesWithinTurn(
                            unit.getTile().position,
                            unit.getMaxMovement().toFloat()
                        ).keys.asSequence().filter { it.position != enemyTile.position }
                        for (tile in tilesInMoveRange) {
                            tile.enemyInfluenceScore += (unit.baseUnit.strength * (1 + tile.getDefensiveBonus())) * (unit.health / 100.0F)
                        }
                    }
                    if (unit.type.isRanged()) {
                        val rangeOfAttack = unit.getRange()
                        val tilesInTargetRange =
                                if (unit.hasUnique("Ranged attacks may be performed over obstacles") || unit.type.isAirUnit())
                                    enemyTile.getTilesInDistance(rangeOfAttack)
                                else enemyTile.getViewableTilesList(rangeOfAttack)
                                        .asSequence()
                        for (tile in tilesInTargetRange) {
                            tile.enemyInfluenceScore += (unit.baseUnit.rangedStrength * (1 + tile.getDefensiveBonus())) * (unit.health / 100.0F)
                        }
                    }
                } else if (unit.name == Constants.greatGeneral || unit.baseUnit.replaces == Constants.greatGeneral) {
                    val tilesInAffectedRange = enemyTile.getTilesInDistance(2)
                    for (tile in tilesInAffectedRange) {
                        tile.enemyInfluenceScore *= 1.15F
                    }
                } else if (unit.type.isCivilian()) {
                    enemyTile.enemyInfluenceScore = -2.0F
                }
            }
            if (enemyTile.isCityCenter()) {
                val enemyCity = enemyTile.getCity()
                val tilesInTargetRange = enemyCity?.getCenterTile()?.getTilesInDistance(enemyCity.range)?.asSequence()?.filter { it.position != enemyTile.position }
                    val enemyCityStrength = enemyCity?.let { CityCombatant(it).getCityStrength() }
                enemyTile.enemyInfluenceScore += (enemyCityStrength?.toFloat()!! / 2.0F)
                if (tilesInTargetRange != null) {
                    for (tile in tilesInTargetRange) {
                        tile.enemyInfluenceScore += (enemyCityStrength.toFloat() / 2.0F)
                    }
                }
            }
        }
    }

    private fun setFriendlyTiles(friendlyTiles: List<TileInfo>) {
        for (friendlyTile in friendlyTiles) {
            for (unit in friendlyTile.getUnits()) {
                if (unit.type.isMilitary()) {
                    friendlyTile.friendlyInfluenceScore += (-unit.baseUnit.strength * (1 + friendlyTile.getDefensiveBonus())) * (unit.health/100.0F)
                    if (unit.type.isMelee()) {
                        val tilesInMoveRange = unit.movement.getDistanceToTilesWithinTurn(
                                unit.getTile().position,
                                unit.getMaxMovement().toFloat()
                        ).keys.asSequence().filter { it.position != friendlyTile.position }
                        for (tile in tilesInMoveRange) {
                            tile.friendlyInfluenceScore += (-unit.baseUnit.strength * (1 + tile.getDefensiveBonus())) * (unit.health / 100.0F)
                        }
                    }
                    if (unit.type.isRanged()) {
                        val rangeOfAttack = unit.getRange()
                        val tilesInTargetRange =
                                if (unit.hasUnique("Ranged attacks may be performed over obstacles") || unit.type.isAirUnit())
                                    friendlyTile.getTilesInDistance(rangeOfAttack)
                                else friendlyTile.getViewableTilesList(rangeOfAttack)
                                        .asSequence()
                        for (tile in tilesInTargetRange) {
                            tile.friendlyInfluenceScore += (-unit.baseUnit.rangedStrength * (1 + tile.getDefensiveBonus())) * (unit.health / 100.0F)
                        }
                    }
                } else if (unit.name == Constants.greatGeneral || unit.baseUnit.replaces == Constants.greatGeneral) {
                    val tilesInAffectedRange = friendlyTile.getTilesInDistance(2)
                    for (tile in tilesInAffectedRange) {
                        tile.friendlyInfluenceScore *= 1.15F
                    }
                } else if (unit.type.isCivilian()) {
                    friendlyTile.friendlyInfluenceScore += 2.0F
                }
            }
            if (friendlyTile.isCityCenter()) {
                val friendlyCity = friendlyTile.getCity()
                val tilesInTargetRange = friendlyCity?.getCenterTile()?.getTilesInDistance(friendlyCity.range)?.asSequence()?.filter { it.position != friendlyTile.position }
                val friendlyCityStrength = friendlyCity?.let { CityCombatant(it).getCityStrength() }
                friendlyTile.friendlyInfluenceScore += -friendlyCityStrength?.toFloat()!!
                if (tilesInTargetRange != null) {
                    for (tile in tilesInTargetRange) {
                        tile.friendlyInfluenceScore += -friendlyCityStrength.toFloat()
                    }
                }
            }
        }
    }

    private fun setNeutralTiles(neutralTiles: List<TileInfo>) {
        for (neutralTile in neutralTiles) {
            for (unit in neutralTile.getUnits()) {
                if (unit.type.isMilitary()) {
                    neutralTile.enemyInfluenceScore += (unit.baseUnit.strength * (1 + neutralTile.getDefensiveBonus())) * (unit.health / 100.0F) * 0.5F
                    if (unit.type.isMelee()) {
                        val tilesInMoveRange = unit.movement.getDistanceToTilesWithinTurn(
                                unit.getTile().position,
                                unit.getMaxMovement().toFloat()
                        ).keys.asSequence().filter { it.position != neutralTile.position }
                        for (tile in tilesInMoveRange) {
                            tile.enemyInfluenceScore += (unit.baseUnit.strength * (1 + tile.getDefensiveBonus())) * (unit.health / 100.0F)
                        }
                    }
                    if (unit.type.isRanged()) {
                        val rangeOfAttack = unit.getRange()
                        val tilesInTargetRange =
                                if (unit.hasUnique("Ranged attacks may be performed over obstacles") || unit.type.isAirUnit())
                                    neutralTile.getTilesInDistance(rangeOfAttack)
                                else neutralTile.getViewableTilesList(rangeOfAttack)
                                        .asSequence()
                        for (tile in tilesInTargetRange) {
                            tile.enemyInfluenceScore += (unit.baseUnit.rangedStrength * (1 + tile.getDefensiveBonus())) * (unit.health / 100.0F)
                        }
                    }
                } else if (unit.name == Constants.greatGeneral || unit.baseUnit.replaces == Constants.greatGeneral) {
                    val tilesInAffectedRange = neutralTile.getTilesInDistance(2)
                    for (tile in tilesInAffectedRange) {
                        tile.enemyInfluenceScore *= 1.15F
                    }
                } else if (unit.type.isCivilian()) {
                    neutralTile.enemyInfluenceScore += -2.0F
                }
            }
            if (neutralTile.isCityCenter()) {
                val neutralCity = neutralTile.getCity()
                val tilesInTargetRange = neutralCity?.getCenterTile()?.getTilesInDistance(neutralCity.range)?.asSequence()?.filter { it.position != neutralTile.position }
                    val neutralCityStrength = neutralCity?.let { CityCombatant(it).getCityStrength() }
                neutralTile.enemyInfluenceScore += ((neutralCityStrength?.toFloat()!! / 2.0F) * 0.5F)
                if (tilesInTargetRange != null) {
                    for (tile in tilesInTargetRange) {
                        tile.enemyInfluenceScore += ((neutralCityStrength?.toFloat()!! / 2.0F) * 0.5F)
                    }
                }
            }
        }
    }

    private fun containsEnemyUnit(tile: TileInfo, viewingCiv: CivilizationInfo): Boolean {
        for (unit in tile.getUnits()) {
            if (unit.civInfo == viewingCiv) return false
            if (unit.civInfo.isAtWarWith(viewingCiv)) return true
        }
        if (tile.isCityCenter()) {
            if (tile.getCity()?.civInfo == viewingCiv) return false
            if (tile.getCity()?.civInfo?.isAtWarWith(viewingCiv)!!) return true
        }
        return false
    }

    private fun containsNeutralUnit(tile: TileInfo, viewingCiv: CivilizationInfo): Boolean {
        for (unit in tile.getUnits()) {
            if (unit.civInfo == viewingCiv) return false
            if (!unit.civInfo.isAtWarWith(viewingCiv)) return true
        }
        if (tile.isCityCenter()) {
            if (tile.getCity()?.civInfo == viewingCiv) return false
            if (!tile.getCity()?.civInfo?.isAtWarWith(viewingCiv)!!) return true
        }
        return false
    }

    private fun containsFriendlyUnit(tile: TileInfo, viewingCiv: CivilizationInfo): Boolean {
        for (unit in tile.getUnits()) {
            if (unit.civInfo == viewingCiv) return true
        }
        if (tile.isCityCenter()) {
            if (tile.getCity()?.civInfo == viewingCiv) return true
        }
        return false
    }
}