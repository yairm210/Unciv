package com.unciv.logic

import com.unciv.UnCivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.TileImprovement
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.unit.UnitActions

class Automation {

    private fun findTileToWork(currentTile: TileInfo, civInfo: CivilizationInfo): TileInfo {
        val selectedTile = currentTile.tileMap.getTilesInDistance(currentTile.position, 4)
                .filter {
                    (it.unit == null || it == currentTile)
                            && it.improvement == null
                            && it.canBuildImprovement(chooseImprovement(it), civInfo)
                }
                .maxBy { getPriority(it, civInfo) }
        if (selectedTile != null && getPriority(selectedTile, civInfo) > 1) return selectedTile
        else return currentTile
    }

    private fun getPriority(tileInfo: TileInfo, civInfo: CivilizationInfo): Int {
        var priority = 0
        if (tileInfo.isWorked()) priority += 3
        if (tileInfo.getOwner() == civInfo) priority += 2
        if (tileInfo.hasViewableResource(civInfo)) priority += 1
        else if (tileInfo.neighbors.any { it.getOwner() != null }) priority += 1
        return priority
    }

    private fun chooseImprovement(tile: TileInfo): TileImprovement {
        val improvementString = when {
            tile.improvementInProgress != null -> tile.improvementInProgress
            tile.terrainFeature == "Forest" -> "Lumber mill"
            tile.terrainFeature == "Jungle" -> "Trading post"
            tile.terrainFeature == "Marsh" -> "Remove Marsh"
            tile.resource != null -> tile.tileResource.improvement
            tile.baseTerrain == "Hill" -> "Mine"
            tile.baseTerrain == "Grassland" || tile.baseTerrain == "Desert" || tile.baseTerrain == "Plains" -> "Farm"
            tile.baseTerrain == "Tundra" -> "Trading post"
            else -> null
        }
        return GameBasics.TileImprovements[improvementString]!!
    }

    fun automateWorkerAction(unit: MapUnit) {
        var tile = unit.getTile()
        val tileToWork = findTileToWork(tile, unit.civInfo)
        if (tileToWork != tile) {
            tile = unit.headTowards(tileToWork.position)
            unit.doPreTurnAction(tile)
            return
        }
        if (tile.improvementInProgress == null) {
            val improvement = chooseImprovement(tile)
            if (tile.canBuildImprovement(improvement, unit.civInfo))
            // What if we're stuck on this tile but can't build there?
                tile.startWorkingOnImprovement(improvement, unit.civInfo)
        }
    }

    fun automateCivMoves(civInfo: CivilizationInfo) {
        if (civInfo.tech.techsToResearch.isEmpty()) {
            val researchableTechs = GameBasics.Technologies.values.filter { civInfo.tech.canBeResearched(it.name) }
            val techToResearch = researchableTechs.minBy { it.cost }
            civInfo.tech.techsResearched.add(techToResearch!!.name)
        }

        for (city in civInfo.cities) {
            if (city.health < city.getMaxHealth()) trainCombatUnit(city)
        }

        for (unit in civInfo.getCivUnits()) {
            automateUnitMoves(unit)
        }
    }

    private fun trainCombatUnit(city: CityInfo) {
        city.cityConstructions.currentConstruction = "Archer" // when we have more units then we'll see.
    }

    fun automateUnitMoves(unit: MapUnit) {

        if (unit.name == "Settler") {
            UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen!!).first { it.name == "Found city" }.action()
            return
        }

        if (unit.name == "Worker") {
            Automation().automateWorkerAction(unit)
            return
        }


        fun healUnit() {
            // If we're low on health then heal
            // todo: go to a more defensible place if there is one
            val tilesInDistance = unit.getDistanceToTiles().keys
            val unitTile = unit.getTile()

            // Go to friendly tile if within distance - better healing!
            val friendlyTile = tilesInDistance.firstOrNull { it.getOwner()?.civName == unit.owner && it.unit == null }
            if (unitTile.getOwner()?.civName != unit.owner && friendlyTile != null) {
                unit.moveToTile(friendlyTile)
                return
            }

            // Or at least get out of enemy territory yaknow
            val neutralTile = tilesInDistance.firstOrNull { it.getOwner() == null && it.unit == null }
            if (unitTile.getOwner()?.civName != unit.owner && unitTile.getOwner() != null && neutralTile != null) {
                unit.moveToTile(neutralTile)
                return
            }
        }

        if (unit.health < 50) {
            healUnit()
            return
        } // do nothing but heal

        // if there is an attackable unit in the vicinity, attack!
        val attackableTiles = unit.civInfo.getViewableTiles()
                .filter { it.unit != null && it.unit!!.owner != unit.civInfo.civName && !it.isCityCenter }.toHashSet()
        val distanceToTiles = unit.getDistanceToTiles()
        val unitTileToAttack = distanceToTiles.keys.firstOrNull { attackableTiles.contains(it) }

        if (unitTileToAttack != null) {
            val unitToAttack = unitTileToAttack.unit!!
            if (unitToAttack.getBaseUnit().unitType == UnitType.Civilian) { // kill
                unitToAttack.civInfo.addNotification("Our " + unitToAttack.name + " was destroyed by an enemy " + unit.name + "!", unitTileToAttack.position)
                unitTileToAttack.unit = null
                unit.headTowards(unitTileToAttack.position)
                return
            }

            val damageToAttacker = Battle(unit.civInfo.gameInfo).calculateDamageToAttacker(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))

            if (damageToAttacker < unit.health) { // don't attack if we'll die from the attack
                if(unit.getBaseUnit().unitType == UnitType.Melee)
                    unit.headTowards(unitTileToAttack.position)
                Battle(unit.civInfo.gameInfo).attack(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))
                return
            }
        }

        if (unit.health < 80) {
            healUnit()
            return
        } // do nothing but heal until 80 health


        // else, if there is a reachable spot from which we can attack this turn
        // (say we're an archer and there's a unit 3 tiles away), go there and attack
        // todo

        // else, find the closest enemy unit that we know of within 5 spaces and advance towards it
        val closestUnit = unit.civInfo.gameInfo.tileMap.getTilesInDistance(unit.getTile().position, 5)
                .firstOrNull { attackableTiles.contains(it) }

        if (closestUnit != null) {
            unit.headTowards(closestUnit.position)
            return
        }

        if (unit.health < 100) {
            healUnit()
            return
        }

        // else, go to a random space
        unit.moveToTile(distanceToTiles.keys.filter { it.unit == null }.toList().getRandom())
    }
}