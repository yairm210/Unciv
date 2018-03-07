package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.TileImprovement

import java.text.DecimalFormat

class MapUnit {
    @Transient
    lateinit var civInfo: CivilizationInfo

    @JvmField var owner: String? = null
    @JvmField var name: String? = null
    @JvmField var maxMovement: Int = 0
    @JvmField var currentMovement: Float = 0f
    @JvmField var action: String? = null // work, automation, fortifying, I dunno what.

    val movementString: String
        get() = DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + maxMovement

    fun doPreTurnAction(tile: TileInfo) {
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff
        if (action != null && action!!.startsWith("moveTo")) {
            val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val destinationVector = Vector2(Integer.parseInt(destination[0]).toFloat(), Integer.parseInt(destination[1]).toFloat())
            val gotTo = headTowards(tile.position, destinationVector)
            if(gotTo==tile) // We didn't move at all
                return
            if (gotTo.position == destinationVector) action = null
            if (currentMovement != 0f) doPreTurnAction(gotTo)
            return
        }

        if ("automation" == action) doAutomatedAction(tile)
    }

    private fun doPostTurnAction(tile: TileInfo) {
        if (name == "Worker" && tile.improvementInProgress != null) workOnImprovement(tile)
    }

    private fun workOnImprovement(tile: TileInfo) {
        tile.turnsToImprovement -= 1
        if (tile.turnsToImprovement != 0) return
        when {
            tile.improvementInProgress!!.startsWith("Remove") -> tile.terrainFeature = null
            tile.improvementInProgress == "Road" -> tile.roadStatus = RoadStatus.Road
            tile.improvementInProgress == "Railroad" -> tile.roadStatus = RoadStatus.Railroad
            else -> tile.improvement = tile.improvementInProgress
        }
        tile.improvementInProgress = null
    }

    private fun getPriority(tileInfo: TileInfo): Int {
        var priority = 0
        if (tileInfo.workingCity != null) priority += 3
        if (tileInfo.owner == owner) priority += 2
        if (tileInfo.hasViewableResource(civInfo)) priority += 1
        else if (tileInfo.neighbors.any { it.owner != null }) priority += 1
        return priority
    }

    private fun findTileToWork(currentTile: TileInfo): TileInfo {
        val selectedTile = civInfo.gameInfo.tileMap.getTilesInDistance(currentTile.position, 4)
                .filter { (it.unit==null || it==currentTile ) && it.improvement==null && it.canBuildImprovement(chooseImprovement(it),civInfo) }
                .maxBy { getPriority(it) }
        if(selectedTile!=null && getPriority(selectedTile) > 1) return selectedTile
        else return currentTile
    }

    fun doAutomatedAction(tile: TileInfo) {
        var tile = tile
        val tileToWork = findTileToWork(tile)
        if (tileToWork != tile) {
            tile = headTowards(tile.position, tileToWork.position)
            doPreTurnAction(tile)
            return
        }
        if (tile.improvementInProgress == null) {
            val improvement = chooseImprovement(tile)
            if (tile.canBuildImprovement(improvement, civInfo))
            // What if we're stuck on this tile but can't build there?
                tile.startWorkingOnImprovement(improvement, civInfo)
        }
    }

    private fun chooseImprovement(tile: TileInfo): TileImprovement {
        return GameBasics.TileImprovements[chooseImprovementString(tile)]!!
    }

    private fun chooseImprovementString(tile: TileInfo): String? {
        when {
            tile.improvementInProgress != null -> return tile.improvementInProgress
            tile.terrainFeature == "Forest" -> return "Lumber mill"
            tile.terrainFeature == "Jungle" -> return "Trading post"
            tile.terrainFeature == "Marsh" -> return "Remove Marsh"
            tile.resource != null -> return tile.tileResource.improvement
            tile.baseTerrain == "Hill" -> return "Mine"
            tile.baseTerrain == "Grassland" || tile.baseTerrain == "Desert" || tile.baseTerrain == "Plains" -> return "Farm"
            tile.baseTerrain == "Tundra" -> return "Trading post"
            else -> return null
        }

    }

    /**
     *
     * @param origin
     * @param destination
     * @return The tile that we reached this turn
     */
    fun headTowards(origin: Vector2, destination: Vector2): TileInfo {
        val tileMap = civInfo.gameInfo.tileMap
        val isMachineryResearched = civInfo.tech.isResearched("Machinery")
        val distanceToTiles = tileMap.getDistanceToTilesWithinTurn(origin, currentMovement, isMachineryResearched)

        val destinationTileThisTurn:TileInfo
        if (distanceToTiles.containsKey(tileMap.get(destination)))
            destinationTileThisTurn = tileMap.get(destination)

        else { // If the tile is far away, we need to build a path how to get there, and then take the first step
            val path = tileMap.getShortestPath(origin, destination, currentMovement, maxMovement, isMachineryResearched)
            destinationTileThisTurn = path.first()
        }
        if (destinationTileThisTurn.unit != null) return tileMap[origin] // Someone is blocking tohe path to the final tile...
        val distanceToTile: Float = distanceToTiles[destinationTileThisTurn]!!
        tileMap[origin].moveUnitToTile(destinationTileThisTurn, distanceToTile)
        return destinationTileThisTurn
    }

    fun nextTurn(tileInfo: TileInfo) {
        doPostTurnAction(tileInfo)
        currentMovement = maxMovement.toFloat()
        doPreTurnAction(tileInfo)
    }

}