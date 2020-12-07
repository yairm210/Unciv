package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo

object InfluenceMap {

    var tilesInRangeGeneral = listOf<TileInfo>()
    var tilesInAttackRange = listOf<TileInfo>()


    fun setInfluenceScore(viewingCiv: CivilizationInfo, tilesToCheck: List<TileInfo>? = null) {
        val viewable = viewingCiv.gameInfo.tileMap.values
        
        //Such a dirty hack, need to actually create an object that holds these for each Civ
        for (tile in viewable) {
            tile.friendlyInfluenceScore = 0.0F
            tile.enemyInfluenceScore = 0.0F
        }
        val tilesWithEnemies = (tilesToCheck ?: viewable)
                .filter { containsEnemyUnit(it, viewingCiv) }
        val tilesWithNeutralCombatant = (tilesToCheck ?: viewable)
                .filter { containsNeutralUnit(it, viewingCiv) }
        val tilesWithFriendlyCombatant = (tilesToCheck ?: viewable)
                .filter { containsFriendlyUnit(it, viewingCiv) }
        setTiles(tilesWithEnemies, 1.0F, true)
        setTiles(tilesWithNeutralCombatant, 0.5F, true)
        setTiles(tilesWithFriendlyCombatant, 1.0F, false)

    }
    
    private fun getModifiedUnitStrength(tile: TileInfo, unit: MapUnit): Float {
        return (unit.baseUnit.strength * (1 + tile.getDefensiveBonus())) * (unit.health/100.0F)
    }
    
    private fun getModifiedUnitRangedStrength(tile: TileInfo, unit: MapUnit): Float {
        return  (unit.baseUnit.rangedStrength * (1 + tile.getDefensiveBonus())) * (unit.health/100.0F)
    }
    
    private fun getInfluenceOfUnit(tile: TileInfo, unit: MapUnit, influenceModifier: Float = 1.0F): Float {
        var score = 0.0F
        if (unit.type.isMilitary()) {
            score += getModifiedUnitStrength(tile, unit) * influenceModifier
            if (unit.type.isMelee()) {
                tilesInAttackRange = unit.movement.getDistanceToTilesWithinTurn(
                        unit.getTile().position,
                        unit.getMaxMovement().toFloat()
                ).keys.toList().filter { it.position != tile.position }
            }
            if (unit.type.isRanged()) {
                val rangeOfAttack = unit.getRange()
                tilesInAttackRange =
                        if (unit.hasUnique("Ranged attacks may be performed over obstacles") || unit.type.isAirUnit())
                            tile.getTilesInDistance(rangeOfAttack).toList()
                        else tile.getViewableTilesList(rangeOfAttack)
                                .toList()
            }
        } else if (unit.name == Constants.greatGeneral || unit.baseUnit.replaces == Constants.greatGeneral) {
            tilesInRangeGeneral += tile.getTilesAtDistance(2)
        } else if (unit.type.isCivilian()) {
            score += 4.0F * influenceModifier
        }
        return score
    }
    
    private fun getInfluenceOfCity(city: CityInfo, influenceModifier: Float = 1.0F): Float {
        val cityStrength = CityCombatant(city).getCityStrength()
        return (cityStrength.toFloat() / 2.0F) * influenceModifier
    }
    
    private fun setTiles(tiles: List<TileInfo>, influenceModifier: Float = 1.0F, isEnemy: Boolean) {
        tilesInRangeGeneral = listOf<TileInfo>()

        for (tile in tiles) {
            var tilesInRangeCities = listOf<TileInfo>()
            var score = 0.0F
            var cityScore = 0.0F

            for (unit in tile.getUnits()) {
                tilesInAttackRange = listOf<TileInfo>()
                score += getInfluenceOfUnit(tile, unit, influenceModifier)
                for (attackableTiles in tilesInAttackRange) {
                    if (isEnemy) {
                        if (unit.type.isRanged()){
                            attackableTiles.enemyInfluenceScore += getModifiedUnitRangedStrength(attackableTiles, unit)
                        } else {
                            attackableTiles.enemyInfluenceScore += getModifiedUnitStrength(attackableTiles, unit)
                        }
                    } else {
                        if (unit.type.isRanged()) {
                            attackableTiles.friendlyInfluenceScore += getModifiedUnitRangedStrength(attackableTiles, unit)
                        } else {
                            attackableTiles.friendlyInfluenceScore += getModifiedUnitStrength(attackableTiles, unit)
                        }
                    }
                }
            }

            if (tile.isCityCenter()) {
                var city = tile.getCity()!!
                cityScore = getInfluenceOfCity(city, influenceModifier)
                tilesInRangeCities = city.getCenterTile().getTilesInDistance(city.range).toList().filter { it.position != city.location }
                score += cityScore
                }

            if (isEnemy) {
                tile.enemyInfluenceScore += score
                for (bombardmentTile in tilesInRangeCities) {
                    bombardmentTile.enemyInfluenceScore += cityScore
                }
            } else {
                tile.friendlyInfluenceScore += score
                for (bombardmentTile in tilesInRangeCities) {
                    bombardmentTile.friendlyInfluenceScore += cityScore
                }
            }
        }
        // Generals need to affect units, this has to go last. Generals also need to affect the enemy and friendly units. Ideally they should only buff their civs units however until we can map per civ this will have to do
        if (isEnemy) {
            for (affectedTile in tilesInRangeGeneral) {
                affectedTile.enemyInfluenceScore *= 1.15F
            }
        } else {
            for (affectedTile in tilesInRangeGeneral) {
                affectedTile.friendlyInfluenceScore *= 1.15F
            }
        }
    }

    private fun containsEnemyUnit(tile: TileInfo, viewingCiv: CivilizationInfo): Boolean {
        for (unit in tile.getUnits()) {
            if (unit.civInfo != viewingCiv && unit.civInfo.isAtWarWith(viewingCiv)) return true
        }
        if (tile.isCityCenter()) {
            if (tile.getCity()!!.civInfo != viewingCiv && tile.getCity()!!.civInfo.isAtWarWith(viewingCiv)) return true
        }
        return false
    }

    private fun containsNeutralUnit(tile: TileInfo, viewingCiv: CivilizationInfo): Boolean {
        for (unit in tile.getUnits()) {
            if (unit.civInfo != viewingCiv && !unit.civInfo.isAtWarWith(viewingCiv)) return true
        }
        if (tile.isCityCenter()) {
            if (tile.getCity()!!.civInfo != viewingCiv && !(tile.getCity()!!.civInfo.isAtWarWith(viewingCiv))) return true
        }
        return false
    }

    private fun containsFriendlyUnit(tile: TileInfo, viewingCiv: CivilizationInfo): Boolean {
        for (unit in tile.getUnits()) {
            if (unit.civInfo == viewingCiv) return true
        }
        if (tile.isCityCenter()) {
            if (tile.getCity()!!.civInfo == viewingCiv) return true
        }
        return false
    }
}