package com.unciv.logic.map

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.getRandom
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.ArrayList

class MapUnit {
    @Transient lateinit var civInfo: CivilizationInfo
    @Transient lateinit var baseUnit: BaseUnit
    @Transient internal lateinit var currentTile :TileInfo

    lateinit var owner: String
    lateinit var name: String
    var currentMovement: Float = 0f
    var health:Int = 100
    var action: String? = null // work, automation, fortifying, I dunno what.
    var attacksThisTurn = 0
    var promotions = UnitPromotions()

    //region pure functions
    fun clone(): MapUnit {
        val toReturn = MapUnit()
        toReturn.owner=owner
        toReturn.name=name
        toReturn.currentMovement=currentMovement
        toReturn.health=health
        toReturn.action=action
        toReturn.attacksThisTurn=attacksThisTurn
        toReturn.promotions=promotions.clone()
        return toReturn
    }

    fun baseUnit(): BaseUnit = baseUnit
    fun getMovementString(): String = DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + getMaxMovement()
    fun getTile(): TileInfo =  currentTile
    fun getMaxMovement(): Int {
        var movement = baseUnit.movement
        movement += getUniques().count{it=="+1 Movement"}
        return movement
    }

    fun getDistanceToTiles(): HashMap<TileInfo, Float> {
        val tile = getTile()
        return movementAlgs().getDistanceToTilesWithinTurn(tile.position,currentMovement)
    }

    @Transient var tempUniques: List<String> = ArrayList()

    fun getUniques(): List<String> {
        return tempUniques
    }

    fun updateUniques(){
        val uniques = ArrayList<String>()
        val baseUnit = baseUnit()
        if(baseUnit.uniques!=null) uniques.addAll(baseUnit.uniques!!)
        uniques.addAll(promotions.promotions.map { GameBasics.UnitPromotions[it]!!.effect })
        tempUniques = uniques
    }

    fun hasUnique(unique:String): Boolean {
        return getUniques().contains(unique)
    }

    fun getViewableTiles(): MutableList<TileInfo> {
        var visibilityRange = 2
        visibilityRange += getUniques().count{it=="+1 Visibility Range"}
        if(hasUnique("Limited Visibility")) visibilityRange-=1
        if(civInfo.getNation().unique=="All land military units have +1 sight, 50% discount when purchasing tiles")
            visibilityRange += 1
        val tile = getTile()
        if (tile.baseTerrain == "Hill") visibilityRange += 1
        return tile.getViewableTiles(visibilityRange)
    }

    fun isFortified(): Boolean {
        return action!=null && action!!.startsWith("Fortify")
    }

    fun getFortificationTurns(): Int {
        if(!isFortified()) return 0
        return action!!.split(" ")[1].toInt()
    }

    fun movementAlgs() = UnitMovementAlgorithms(this)

    override fun toString(): String {
        return "$name - $owner"
    }

    /**
     * Designates whether we can walk to the tile - without attacking
     */
    fun canMoveTo(tile: TileInfo): Boolean {
        val tileOwner = tile.getOwner()
        if(tile.getBaseTerrain().type==TerrainType.Water && baseUnit.unitType.isLandUnit())
            return false
        if(tile.getBaseTerrain().type==TerrainType.Land && baseUnit.unitType.isWaterUnit())
            return false
        if(tileOwner!=null && tileOwner.civName!=owner
                && (tile.isCityCenter() || !civInfo.canEnterTiles(tileOwner))) return false

        if (baseUnit().unitType== UnitType.Civilian)
            return tile.civilianUnit==null && (tile.militaryUnit==null || tile.militaryUnit!!.owner==owner)
        else return tile.militaryUnit==null && (tile.civilianUnit==null || tile.civilianUnit!!.owner==owner)
    }

    fun isIdle(): Boolean {
        if (currentMovement == 0f) return false
        if (name == "Worker" && getTile().improvementInProgress != null) return false
        if (isFortified()) return false
        if (action=="Sleep") return false
        return true
    }

    fun canAttack(): Boolean {
        if(currentMovement==0f) return false
        if(attacksThisTurn>0 && !hasUnique("1 additional attack per turn")) return false
        if(attacksThisTurn>1) return false
        if(hasUnique("Must set up to ranged attack") && action != "Set Up") return false
        return true
    }

    fun getRange(): Int {
        if(baseUnit().unitType.isMelee()) return 1
        var range = baseUnit().range
        if(hasUnique("+1 Range")) range++
        return range
    }

    //endregion

    //region state-changing functions
    fun setTransients(){
        promotions.unit=this
        baseUnit=GameBasics.Units[name]!!
        updateUniques()
    }
    fun doPreTurnAction() {
        val currentTile = getTile()
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff

        val enemyUnitsInWalkingDistance = getDistanceToTiles().keys
                .filter { it.militaryUnit!=null && civInfo.isAtWarWith(it.militaryUnit!!.civInfo)}
        if(enemyUnitsInWalkingDistance.isNotEmpty()) {
            if (action != null && action!!.startsWith("moveTo")) action=null
            return  // Don't you dare move.
        }

        if (action != null && action!!.startsWith("moveTo")) {
            val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val destinationVector = Vector2(Integer.parseInt(destination[0]).toFloat(), Integer.parseInt(destination[1]).toFloat())
            val destinationTile = currentTile.tileMap[destinationVector]
            if(!movementAlgs().canReach(destinationTile)) return // That tile that we were moving towards is now unreachable
            val gotTo = movementAlgs().headTowards(destinationTile)
            if(gotTo==currentTile) // We didn't move at all
                return
            if (gotTo.position == destinationVector) action = null
            if (currentMovement != 0f) doPreTurnAction()
            return
        }

        if (action == "automation") WorkerAutomation(this).automateWorkerAction()

        if(action == "explore") UnitAutomation().automatedExplore(this)
    }

    private fun doPostTurnAction() {
        if (name == "Worker" && getTile().improvementInProgress != null) workOnImprovement()
        if(currentMovement== getMaxMovement().toFloat()
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
            tile.improvementInProgress!!.startsWith("Remove") -> {
                val tileImprovement = tile.getTileImprovement()
                if(tileImprovement!=null
                        && tileImprovement.terrainsCanBeBuiltOn.contains(tile.terrainFeature)
                        && !tileImprovement.terrainsCanBeBuiltOn.contains(tile.baseTerrain)) {
                    tile.improvement = null // We removed a terrain (e.g. Forest) and the improvement (e.g. Lumber mill) requires it!
                }

                tile.terrainFeature = null
            }
            tile.improvementInProgress == "Road" -> tile.roadStatus = RoadStatus.Road
            tile.improvementInProgress == "Railroad" -> tile.roadStatus = RoadStatus.Railroad
            else -> tile.improvement = tile.improvementInProgress
        }
        tile.improvementInProgress = null
    }

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

    /**
     * @return The tile that we reached this turn
     */
    fun moveToTile(otherTile: TileInfo) {
        if(otherTile==getTile()) return // already here!
        val distanceToTiles = getDistanceToTiles()

        class YouCantGetThereFromHereException : Exception()
        if (!distanceToTiles.containsKey(otherTile))

            throw YouCantGetThereFromHereException()

        class CantEnterThisTileException : Exception()
        if(!canMoveTo(otherTile))
            throw CantEnterThisTileException()
        if(otherTile.isCityCenter() && otherTile.getOwner()!=civInfo) throw Exception("This is an enemy city, you can't go here!")

        currentMovement -= distanceToTiles[otherTile]!!
        if (currentMovement < 0.1) currentMovement = 0f // silly floats which are "almost zero"
        if(isFortified() || action=="Set Up") action=null // unfortify/setup after moving
        removeFromTile()
        putInTile(otherTile)
    }

    fun endTurn() {
        doPostTurnAction()
        if(currentMovement== getMaxMovement().toFloat() // didn't move this turn
                || getUniques().contains("Unit will heal every turn, even if it performs an action")){
            heal()
        }
    }

    fun startTurn(){
        currentMovement = getMaxMovement().toFloat()
        attacksThisTurn=0
        val tileOwner = getTile().getOwner()
        if(tileOwner!=null && !civInfo.canEnterTiles(tileOwner)) // if an enemy city expanded onto this tile while I was in it
            movementAlgs().teleportToClosestMoveableTile()
        doPreTurnAction()
    }

    fun destroy(){
        removeFromTile()
        civInfo.units.remove(this)
    }

    fun removeFromTile(){
        if (baseUnit().unitType== UnitType.Civilian) getTile().civilianUnit=null
        else getTile().militaryUnit=null
    }

    fun putInTile(tile:TileInfo){
        if(!canMoveTo(tile)) throw Exception("I can't go there!")
        if(baseUnit().unitType== UnitType.Civilian)
            tile.civilianUnit=this
        else tile.militaryUnit=this
        currentTile = tile
        if(tile.improvement=="Ancient ruins" && !civInfo.isBarbarianCivilization())
            getAncientRuinBonus()
    }

    private fun getAncientRuinBonus() {
        currentTile.improvement=null
        val actions: ArrayList<() -> Unit> = ArrayList()
        if(civInfo.cities.isNotEmpty()) actions.add {
            val city = civInfo.cities.getRandom()
            city.population.population++
            city.population.autoAssignPopulation()
            civInfo.addNotification("We have found survivors the ruins - population added to ["+city.name+"]",city.location, Color.GREEN)
        }
        val researchableAncientEraTechs = GameBasics.Technologies.values.filter { civInfo.tech.canBeResearched(it.name)}
        if(researchableAncientEraTechs.isNotEmpty())
            actions.add {
                val tech = researchableAncientEraTechs.getRandom().name
                civInfo.tech.techsResearched.add(tech)
                if(civInfo.tech.techsToResearch.contains(tech)) civInfo.tech.techsToResearch.remove(tech)
                civInfo.addNotification("We have discovered the lost technology of [$tech] in the ruins!",null, Color.BLUE)
            }

        actions.add {
            val chosenUnit = listOf("Settler","Worker","Warrior").getRandom()
            civInfo.placeUnitNearTile(currentTile.position,chosenUnit)
            civInfo.addNotification("A [$chosenUnit] has joined us!",null, Color.BLUE)
        }

        if(baseUnit.unitType!=UnitType.Civilian)
            actions.add {
                promotions.XP+=10
                civInfo.addNotification("An ancient tribe trains our [$name] in their ways of combat!",null, Color.RED)
            }

        actions.add {
            val amount = listOf(25,60,100).getRandom()
            civInfo.gold+=amount
            civInfo.addNotification("We have found a stash of [$amount] gold in the ruins!!",null, Color.RED)
        }

        (actions.getRandom())()
    }

    fun assignOwner(civInfo:CivilizationInfo){
        owner=civInfo.civName
        this.civInfo=civInfo
        civInfo.units.add(this)
    }
    //endregion
}