package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.Unit
import com.unciv.models.gamebasics.unit.UnitType
import java.text.DecimalFormat

class MapUnit {
    @Transient
    lateinit var civInfo: CivilizationInfo

    lateinit var owner: String
    lateinit var name: String
    var maxMovement: Int = 0
    var currentMovement: Float = 0f
    var health:Int = 100
    var action: String? = null // work, automation, fortifying, I dunno what.
    var attacksThisTurn = 0
    var promotions = UnitPromotions()

    fun getBaseUnit(): Unit = GameBasics.Units[name]!!
    fun getMovementString(): String = DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + maxMovement
    fun getTile(): TileInfo {
        return civInfo.gameInfo.tileMap.values.first{it.militaryUnit==this || it.civilianUnit==this}
    }

    fun getDistanceToTiles(): HashMap<TileInfo, Float> {
        val tile = getTile()
        return movementAlgs().getDistanceToTilesWithinTurn(tile.position,currentMovement)
    }

    fun doPreTurnAction() {
        val currentTile = getTile()
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff

        val enemyUnitsInWalkingDistance = getDistanceToTiles().keys
                .filter { it.militaryUnit!=null && it.militaryUnit!!.civInfo!=civInfo }
        if(enemyUnitsInWalkingDistance.isNotEmpty()) return  // Don't you dare move.

        if (action != null && action!!.startsWith("moveTo")) {
            val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val destinationVector = Vector2(Integer.parseInt(destination[0]).toFloat(), Integer.parseInt(destination[1]).toFloat())
            val gotTo = movementAlgs().headTowards(currentTile.tileMap[destinationVector])
            if(gotTo==currentTile) // We didn't move at all
                return
            if (gotTo.position == destinationVector) action = null
            if (currentMovement != 0f) doPreTurnAction()
            return
        }

        if (action == "automation") WorkerAutomation().automateWorkerAction(this)
    }

    private fun doPostTurnAction() {
        if (name == "Worker" && getTile().improvementInProgress != null) workOnImprovement()
        if(currentMovement==maxMovement.toFloat()
                && isFortified()){
            val currentTurnsFortified = getFortificationTurns()
            if(currentTurnsFortified<2) action = "Fortify ${currentTurnsFortified+1}"
        }
    }

    private fun workOnImprovement() {
        val tile=getTile()
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
     * @return The tile that we reached this turn
     */

    private fun heal(){
        val tile = getTile()
        health += when{
            tile.isCityCenter() -> 20
            tile.getOwner()?.civName == owner -> 15 // home territory
            tile.getOwner() == null -> 10 // no man's land (neutral)
            else -> 5 // enemy territory
        }
        if(health>100) health=100
    }

    fun moveToTile(otherTile: TileInfo) {
        if(otherTile==getTile()) return // already here!
        val distanceToTiles = getDistanceToTiles()
        if (!distanceToTiles.containsKey(otherTile))
            throw Exception("You can't get there from here!")
        if(!canMoveTo(otherTile)) throw Exception("Can't enter this tile!")
        if(otherTile.isCityCenter() && otherTile.getOwner()!=civInfo) throw Exception("This is an enemy city, you can't go here!")

        currentMovement -= distanceToTiles[otherTile]!!
        if (currentMovement < 0.1) currentMovement = 0f // silly floats which are "almost zero"
        removeFromTile()
        putInTile(otherTile)
    }

    fun endTurn() {
        doPostTurnAction()
        if(currentMovement==maxMovement.toFloat()){ // didn't move this turn
            heal()
        }
    }

    fun startTurn(){
        currentMovement = maxMovement.toFloat()
        attacksThisTurn=0
        doPreTurnAction()
    }

    fun getUniques(): MutableList<String> {
        val uniques = mutableListOf<String>()
        val baseUnit = getBaseUnit()
        if(baseUnit.uniques!=null) uniques.addAll(baseUnit.uniques!!)
        return uniques
    }

    fun hasUnique(unique:String): Boolean {
        return getUniques().contains(unique)
    }

    fun movementAlgs() = UnitMovementAlgorithms(this)

    override fun toString(): String {
        return "$name - $owner"
    }

    fun getViewableTiles(): MutableList<TileInfo> {
        var visibilityRange = 2
        visibilityRange += getUniques().count{it=="+1 Visibility Range"}
        if(hasUnique("Limited Visibility")) visibilityRange-=1
        return getTile().getViewableTiles(visibilityRange)
    }

    fun isFortified(): Boolean {
        return action!=null && action!!.startsWith("Fortify")
    }

    fun getFortificationTurns(): Int {
        if(!isFortified()) return 0
        return action!!.split(" ")[1].toInt()
    }

    fun removeFromTile(){
        if (getBaseUnit().unitType== UnitType.Civilian) getTile().civilianUnit=null
        else getTile().militaryUnit=null
    }

    fun putInTile(tile:TileInfo){
        if(!canMoveTo(tile)) throw Exception("I can't go there!")
        if(getBaseUnit().unitType== UnitType.Civilian)
            tile.civilianUnit=this
        else tile.militaryUnit=this
    }

    /**
     * Designates whether we can walk to the tile - without attacking
     */
    fun canMoveTo(tile: TileInfo): Boolean {
        if(tile.isCityCenter() && tile.getOwner()!!.civName!=owner) return false
        if (getBaseUnit().unitType== UnitType.Civilian)
            return tile.civilianUnit==null && (tile.militaryUnit==null || tile.militaryUnit!!.owner==owner)
       else return tile.militaryUnit==null && (tile.civilianUnit==null || tile.civilianUnit!!.owner==owner)
    }

    fun isIdle(): Boolean {
        if (currentMovement == 0f) return false
        if (name == "Worker" && getTile().improvementInProgress != null) return false
        if (isFortified()) return false
        return true
    }

    fun canAttack(): Boolean {
        if(currentMovement==0f) return false
        if(attacksThisTurn>0) return false
        if(hasUnique("Must set up to ranged attack") && action != "Set Up") return false
        return true
    }
}