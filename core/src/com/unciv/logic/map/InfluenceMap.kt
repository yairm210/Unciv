package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo

object InfluenceMap {

    fun setInfluenceScore(viewingCiv: CivilizationInfo, tilesToCheck: List<TileInfo>? = null) {
        val viewable = viewingCiv.gameInfo.tileMap.values
        
        //Such a dirty hack, need to actually create an object that holds these for each Civ
        for (tile in viewable) {
            tile.influenceScore = 0.0F
        }
        val tilesWithEnemies = (tilesToCheck ?: viewable)
                .filter { containsEnemyUnit(it, viewingCiv) }
        val tilesWithNeutralCombatant = (tilesToCheck ?: viewable)
                .filter { containsNeutralUnit(it, viewingCiv) }
        val tilesWithFriendlyCombatant = (tilesToCheck ?: viewable)
                .filter { containsFriendlyUnit(it, viewingCiv) }
        setTiles(tilesWithEnemies)
        setTiles(tilesWithNeutralCombatant, 0.5F)
        setTiles(tilesWithFriendlyCombatant, -1.0F)

    }
    
    private fun getModifiedUnitStrength(tile: TileInfo, unit: MapUnit): Float {
        return (unit.baseUnit.strength * (1 + tile.getDefensiveBonus())) * (unit.health/100.0F)
    }
    
    private fun getInfluenceOfUnit(tile: TileInfo, unit: MapUnit, influenceModifier: Float = 1.0F) {
        if (unit.type.isMilitary()) {
            tile.influenceScore += getModifiedUnitStrength(tile, unit) * influenceModifier
            if (unit.type.isMelee()) {
                val tilesInMoveRange = unit.movement.getDistanceToTilesWithinTurn(
                        unit.getTile().position,
                        unit.getMaxMovement().toFloat()
                ).keys.asSequence().filter { it.position != tile.position }
                for (tile in tilesInMoveRange) {
                    tile.influenceScore += getModifiedUnitStrength(tile, unit) * influenceModifier
                }
            }
            if (unit.type.isRanged()) {
                val rangeOfAttack = unit.getRange()
                val tilesInTargetRange =
                        if (unit.hasUnique("Ranged attacks may be performed over obstacles") || unit.type.isAirUnit())
                            tile.getTilesInDistance(rangeOfAttack)
                        else tile.getViewableTilesList(rangeOfAttack)
                                .asSequence()
                for (tile in tilesInTargetRange) {
                    tile.influenceScore += getModifiedUnitStrength(tile, unit) * influenceModifier
                }
            }
        } else if (unit.name == Constants.greatGeneral || unit.baseUnit.replaces == Constants.greatGeneral) {
            val tilesInAffectedRange = tile.getTilesInDistance(2)
            for (tile in tilesInAffectedRange) {
                tile.influenceScore *= 1.15F * influenceModifier
            }
        } else if (unit.type.isCivilian()) {
            tile.influenceScore += 4.0F * influenceModifier
        }
    }
    
    private fun getInfluenceOfCity(city: CityInfo, influenceModifier: Float = 1.0F) {
        val tilesInTargetRange = city?.getCenterTile()?.getTilesInDistance(city.range)?.asSequence()?.filter { it.position != city.location }
        val cityStrength = CityCombatant(city!!).getCityStrength()
        city.getCenterTile().influenceScore += (cityStrength?.toFloat()!! / 2.0F) * influenceModifier
        if (tilesInTargetRange != null) {
            for (tile in tilesInTargetRange) {
                tile.influenceScore += (cityStrength.toFloat() / 2.0F) * influenceModifier
            }
        }
    }
    
    private fun setTiles(tiles: List<TileInfo>, influenceModifier: Float = 1.0F) {
        for (tile in tiles) {
            for (unit in tile.getUnits()) {
                getInfluenceOfUnit(tile, unit, influenceModifier)
            }
            if (tile.isCityCenter()) getInfluenceOfCity(tile.getCity()!!, influenceModifier)
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