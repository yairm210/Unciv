package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Unit
import java.text.DecimalFormat

enum class UnitType{
    Civilian,
    Melee,
    Ranged

}

class MapUnit {
    @Transient
    lateinit var civInfo: CivilizationInfo

    lateinit var owner: String
    lateinit var name: String
    var maxMovement: Int = 0
    var currentMovement: Float = 0f
    var health:Int = 100
    var action: String? = null // work, automation, fortifying, I dunno what.

    fun getBaseUnit(): Unit = GameBasics.Units[name]!!
    fun getMovementString(): String = DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + maxMovement
    fun getTile(): TileInfo {
        return civInfo.gameInfo.tileMap.values.first{it.unit==this}
    }

    fun getDistanceToTiles(): HashMap<TileInfo, Float> {
        val tile = getTile()
        return UnitMovementAlgorithms(tile.tileMap).getDistanceToTilesWithinTurn(tile.position,currentMovement,
                civInfo)
    }

    fun doPreTurnAction(tile: TileInfo) {
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff
        if (action != null && action!!.startsWith("moveTo")) {
            val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val destinationVector = Vector2(Integer.parseInt(destination[0]).toFloat(), Integer.parseInt(destination[1]).toFloat())
            val gotTo = headTowards(destinationVector)
            if(gotTo==tile) // We didn't move at all
                return
            if (gotTo.position == destinationVector) action = null
            if (currentMovement != 0f) doPreTurnAction(gotTo)
            return
        }

        if (action == "automation") WorkerAutomation().automateWorkerAction(this)
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


    /**
     * @param origin
     * @param destination
     * @return The tile that we reached this turn
     */
    fun headTowards(destination: Vector2): TileInfo {
        val currentTile = getTile()
        val tileMap = currentTile.tileMap

        val finalDestinationTile = tileMap[destination]
        val distanceToTiles = getDistanceToTiles()

        val destinationTileThisTurn:TileInfo
        if (distanceToTiles.containsKey(finalDestinationTile)) { // we can get there this turn
            if (finalDestinationTile.unit == null)
                destinationTileThisTurn = finalDestinationTile
            else   // Someone is blocking to the path to the final tile...
            {
                val destinationNeighbors = tileMap[destination].neighbors
                if(destinationNeighbors.contains(currentTile)) // We're right nearby anyway, no need to move
                    return currentTile

                val reachableDestinationNeighbors = destinationNeighbors.filter { distanceToTiles.containsKey(it) && it.unit==null }
                if(reachableDestinationNeighbors.isEmpty()) // We can't get closer...
                    return currentTile

                destinationTileThisTurn = reachableDestinationNeighbors.minBy { distanceToTiles[it]!! }!!
            }
        }

        else { // If the tile is far away, we need to build a path how to get there, and then take the first step
            val path = UnitMovementAlgorithms(tileMap)
                    .getShortestPath(currentTile.position, destination, currentMovement, maxMovement, civInfo)
            destinationTileThisTurn = path.first()
        }

        moveToTile(destinationTileThisTurn)
        return destinationTileThisTurn
    }

    private fun heal(){
        val tile = getTile()
        health += when{
            tile.isCityCenter -> 20
            tile.getOwner()?.civName == owner -> 15 // home territory
            tile.getOwner() == null -> 10 // no man's land (neutral)
            else -> 5 // enemy territory
        }
        if(health>100) health=100
    }


    fun moveToTile(otherTile: TileInfo) {
        val distanceToTiles = getDistanceToTiles()
        if (!distanceToTiles.containsKey(otherTile)) throw Exception("You can't get there from here!")
        if (otherTile.unit != null ) throw Exception("Tile already contains a unit!")

        currentMovement -= distanceToTiles[otherTile]!!
        if (currentMovement < 0.1) currentMovement = 0f // silly floats which are "almost zero"
        getTile().unit = null
        otherTile.unit = this
    }

    fun nextTurn() {
        val tile = getTile()
        doPostTurnAction(tile)
        if(currentMovement==maxMovement.toFloat()){ // didn't move this turn
            heal()
        }
        currentMovement = maxMovement.toFloat()
        doPreTurnAction(tile)
    }

}