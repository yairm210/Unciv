package com.unciv.logic.map

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.action.MapUnitAction
import com.unciv.logic.map.action.StringAction
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.TechEra
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.ArrayList

class MapUnit {

    @Transient lateinit var civInfo: CivilizationInfo
    @Transient lateinit var baseUnit: BaseUnit
    @Transient internal lateinit var currentTile :TileInfo

    // This is saved per each unit because if we need to recalculate viewable tiles every time a unit moves,
    //  and we need to go over ALL the units, that's a lot of time spent on updating information we should already know!
    // About 10% of total NextTurn performance time, at the time of this change!
    @Transient var viewableTiles = listOf<TileInfo>()

    // These are for performance improvements to getMovementCostBetweenAdjacentTiles,
    // a major component of getDistanceToTilesWithinTurn,
    // which in turn is a component of getShortestPath and canReach
    @Transient var ignoresTerrainCost = false
    @Transient var roughTerrainPenalty = false
    @Transient var doubleMovementInCoast = false
    @Transient var doubleMovementInForestAndJungle = false

    lateinit var owner: String
    lateinit var name: String
    var currentMovement: Float = 0f
    var health:Int = 100

    var mapUnitAction : MapUnitAction? = null

    var action: String? // work, automation, fortifying, I dunno what.
        // getter and setter for compatibility: make sure string-based actions still work
        get() {
            val mapUnitActionVal=mapUnitAction
            if(mapUnitActionVal is StringAction)
                return mapUnitActionVal.action
            // any other unit action does count as a unit action, thus is not null. The actual logic is not based on an action string, but realized by extending MapUnitAction
            if(mapUnitActionVal!=null)
                return ""

            return null // unit has no action
        }
        set(value) { mapUnitAction = value?.let{ StringAction(this, value) } } // wrap traditional string-encoded actions into StringAction


    var attacksThisTurn = 0
    var promotions = UnitPromotions()
    var due: Boolean = true

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

    val type:UnitType
        get()=baseUnit.unitType

    fun baseUnit(): BaseUnit = baseUnit
    fun getMovementString(): String = DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + getMaxMovement()
    fun getTile(): TileInfo =  currentTile
    fun getMaxMovement(): Int {
        if(isEmbarked()) return getEmbarkedMovement()

        var movement = baseUnit.movement
        movement += getUniques().count{it=="+1 Movement"}

        if(type.isWaterUnit() && !type.isCivilian()
                && civInfo.getBuildingUniques().contains("All military naval units receive +1 movement and +1 sight"))
            movement += 1

        if(type.isWaterUnit() && civInfo.getNation().unique=="+2 movement for all naval units")
            movement+=2
        
        return movement
    }

    fun getDistanceToTiles(): HashMap<TileInfo, Float> {
        val tile = getTile()
        return movementAlgs().getDistanceToTilesWithinTurn(tile.position,currentMovement)
    }

    // This SHOULD NOT be a hashset, because if it is, thenn promotions with the same text (e.g. barrage I, barrage II)
    //  will not get counted twice!
    @Transient var tempUniques= ArrayList<String>()

    fun getUniques(): ArrayList<String> {
        return tempUniques
    }

    fun updateUniques(){
        val uniques = ArrayList<String>()
        val baseUnit = baseUnit()
        uniques.addAll(baseUnit.uniques)
        uniques.addAll(promotions.promotions.map { GameBasics.UnitPromotions[it]!!.effect })
        tempUniques = uniques

        if("Ignores terrain cost" in uniques) ignoresTerrainCost=true
        if("Rough terrain penalty" in uniques) roughTerrainPenalty=true
        if("Double movement in coast" in uniques) doubleMovementInCoast=true
        if("Double movement rate through Forest and Jungle" in uniques) doubleMovementInForestAndJungle=true
    }

    fun hasUnique(unique:String): Boolean {
        return getUniques().contains(unique)
    }

    // we need to map all the places that this could change: Unit changes locations, owners, gets promotion?
    fun updateViewableTiles() {
        var visibilityRange = 2
        visibilityRange += getUniques().count{it=="+1 Visibility Range"}
        if(hasUnique("Limited Visibility")) visibilityRange-=1
        if(civInfo.getNation().unique=="All land military units have +1 sight, 50% discount when purchasing tiles")
            visibilityRange += 1
        if(type.isWaterUnit() && !type.isCivilian()
                && civInfo.getBuildingUniques().contains("All military naval units receive +1 movement and +1 sight"))
            visibilityRange += 1
        val tile = getTile()
        if (tile.baseTerrain == Constants.hill && type.isLandUnit()) visibilityRange += 1
        viewableTiles = tile.getViewableTiles(visibilityRange, type.isWaterUnit())

        civInfo.updateViewableTiles() // for the civ
    }

    fun isFortified(): Boolean {
        return action?.startsWith("Fortify") == true
    }

    fun getFortificationTurns(): Int {
        if(!isFortified()) return 0
        return action!!.split(" ")[1].toInt()
    }

    fun movementAlgs() = UnitMovementAlgorithms(this)

    override fun toString(): String {
        return "$name - $owner"
    }

    // This is the most called function in the entire game,
    // so multiple callees of this function have been optimized,
    // because optimization on this function results in massive benefits!
    fun canPassThrough(tile: TileInfo):Boolean{

        if(tile.getBaseTerrain().impassable) return false
        if(tile.isLand && type.isWaterUnit() && !tile.isCityCenter())
            return false

        if(tile.isWater && type.isLandUnit()){
            if(!civInfo.tech.unitsCanEmbark) return false
            if(tile.isOcean && !civInfo.tech.embarkedUnitsCanEnterOcean)
                return false
        }
        if(tile.isOcean && baseUnit.uniques.contains("Cannot enter ocean tiles")) return false
        if(tile.isOcean && baseUnit.uniques.contains("Cannot enter ocean tiles until Astronomy")
                && !civInfo.tech.isResearched("Astronomy"))
            return false

        val tileOwner = tile.getOwner()
        if(tileOwner!=null && tileOwner.civName!=owner) {
            if (tile.isCityCenter()) return false
            if (!civInfo.canEnterTiles(tileOwner)
                    && !(civInfo.isPlayerCivilization() && tileOwner.isCityState())) return false
            // AIs won't enter city-state's border.
        }

        val unitsInTile = tile.getUnits()
        if(unitsInTile.isNotEmpty()){
            val firstUnit = unitsInTile.first()
            if(firstUnit.civInfo != civInfo && civInfo.isAtWarWith(firstUnit.civInfo))
                return false
        }

        return true
    }

    /**
     * Designates whether we can enter the tile - without attacking
     * DOES NOT designate whether we can reach that tile in the current turn
     */
    fun canMoveTo(tile: TileInfo): Boolean {
        if(!canPassThrough(tile)) return false

        if (type.isCivilian())
            return tile.civilianUnit==null && (tile.militaryUnit==null || tile.militaryUnit!!.owner==owner)
        else return tile.militaryUnit==null && (tile.civilianUnit==null || tile.civilianUnit!!.owner==owner)
    }

    fun isIdle(): Boolean {
        if (currentMovement == 0f) return false
        if (name == Constants.worker && getTile().improvementInProgress != null) return false
        if (hasUnique("Can construct roads") && currentTile.improvementInProgress=="Road") return false
        if (isFortified()) return false
        if (action=="Sleep") return false
        return true
    }

    fun canAttack(): Boolean {
        if(currentMovement==0f) return false
        if(attacksThisTurn>0 && !hasUnique("1 additional attack per turn")) return false
        if(attacksThisTurn>1) return false
        return true
    }

    fun getRange(): Int {
        if(type.isMelee()) return 1
        var range = baseUnit().range
        if(hasUnique("+1 Range")) range++
        return range
    }


    fun isEmbarked(): Boolean {
        if(!type.isLandUnit()) return false
        return currentTile.getBaseTerrain().type==TerrainType.Water
    }

    fun isInvisible(): Boolean {
        if(hasUnique("Invisible to others"))
            return true
        return false
    }

    fun getEmbarkedMovement(): Int {
        var movement=2
        movement += civInfo.tech.getTechUniques().count { it == "Increases embarked movement +1" }
        return movement
    }

    fun getUnitToUpgradeTo(): BaseUnit {
        var unit = baseUnit()

        // Go up the upgrade tree until you find the last one which is buildable
        while (unit.upgradesTo!=null && civInfo.tech.isResearched(unit.getDirectUpgradeUnit(civInfo).requiredTech!!))
            unit = unit.getDirectUpgradeUnit(civInfo)
        return unit
    }

    fun canUpgrade(): Boolean {
        // We need to remove the unit from the civ for this check,
        // because if the unit requires, say, horses, and so does its upgrade,
        // and the civ currently has 0 horses,
        // if we don't remove the unit before the check it's return false!

        val unitToUpgradeTo = getUnitToUpgradeTo()
        if (name == unitToUpgradeTo.name) return false
        civInfo.removeUnit(this)
        val canUpgrade = unitToUpgradeTo.isBuildable(civInfo)
        civInfo.addUnit(this)
        return canUpgrade
    }

    fun getCostOfUpgrade(): Int {
        val unitToUpgradeTo = getUnitToUpgradeTo()
        var goldCostOfUpgrade = (unitToUpgradeTo.cost - baseUnit().cost) * 2 + 10
        if (civInfo.policies.isAdopted("Professional Army"))
            goldCostOfUpgrade = (goldCostOfUpgrade * 0.66f).toInt()
        if(civInfo.getBuildingUniques().contains("Gold cost of upgrading military units reduced by 33%"))
            goldCostOfUpgrade = (goldCostOfUpgrade * 0.66f).toInt()
        return goldCostOfUpgrade
    }


    fun canFortify(): Boolean {
        if(type.isWaterUnit()) return false
        if(type.isCivilian()) return false
        if(isEmbarked()) return false
        if(hasUnique("No defensive terrain bonus")) return false
        if(isFortified()) return false
        return true
    }

    fun adjacentHealingBonus():Int{
        var healingBonus = 0
        if(hasUnique("This unit and all others in adjacent tiles heal 5 additional HP per turn")) healingBonus +=5
        if(hasUnique("This unit and all others in adjacent tiles heal 5 additional HP. This unit heals 5 additional HP outside of friendly territory.")) healingBonus +=5
        return healingBonus
    }

    //endregion

    //region state-changing functions
    fun setTransients(){
        promotions.unit=this
        mapUnitAction?.unit = this
        baseUnit=GameBasics.Units[name]!!
        updateUniques()
    }

    fun useMovementPoints(amount:Float){
        currentMovement -= amount
        if(currentMovement<0) currentMovement = 0f
    }

    fun doPreTurnAction() {
        if(action==null) return
        val currentTile = getTile()
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff

        val enemyUnitsInWalkingDistance = getDistanceToTiles().keys
                .filter { it.militaryUnit!=null && civInfo.isAtWarWith(it.militaryUnit!!.civInfo)}
        if(enemyUnitsInWalkingDistance.isNotEmpty()) {
            if (mapUnitAction?.shouldStopOnEnemyInSight()==true)
                mapUnitAction=null
            return  // Don't you dare move.
        }

        mapUnitAction?.doPreTurnAction()

        if (action != null && action!!.startsWith("moveTo")) {
            val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val destinationVector = Vector2(Integer.parseInt(destination[0]).toFloat(), Integer.parseInt(destination[1]).toFloat())
            val destinationTile = currentTile.tileMap[destinationVector]
            if(!movementAlgs().canReach(destinationTile)) return // That tile that we were moving towards is now unreachable
            val gotTo = movementAlgs().headTowards(destinationTile)
            if(gotTo==currentTile) // We didn't move at all
                return
            if (gotTo.position == destinationVector) action = null
            if (currentMovement >0) doPreTurnAction()
            return
        }

        if (action == "automation") WorkerAutomation(this).automateWorkerAction()

        if(action == "Explore") UnitAutomation().automatedExplore(this)
    }

    private fun doPostTurnAction() {
        if (name == Constants.worker && getTile().improvementInProgress != null) workOnImprovement()
        if(hasUnique("Can construct roads") && currentTile.improvementInProgress=="Road") workOnImprovement()
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
                if(tile.improvementInProgress=="Remove Road" || tile.improvementInProgress=="Remove Railroad")
                    tile.roadStatus = RoadStatus.None
                else tile.terrainFeature = null
            }
            tile.improvementInProgress == "Road" -> tile.roadStatus = RoadStatus.Road
            tile.improvementInProgress == "Railroad" -> tile.roadStatus = RoadStatus.Railroad
            else -> tile.improvement = tile.improvementInProgress
        }
        tile.improvementInProgress = null
    }

    private fun heal(){
        if(isEmbarked()) return // embarked units can't heal
        health += rankTileForHealing(getTile())
        val adjacentUnits = currentTile.getTilesInDistance(1).flatMap { it.getUnits() }
        if(adjacentUnits.isNotEmpty())
            health += adjacentUnits.map { it.adjacentHealingBonus() }.max()!!
        if(health>100) health=100
    }

    fun rankTileForHealing(tileInfo:TileInfo): Int {
        return when{
            tileInfo.getOwner() == null -> 10 // no man's land (neutral)
            tileInfo.isCityCenter() -> 20
            !civInfo.isAtWarWith(tileInfo.getOwner()!!) -> 15 // home or allied territory
            else -> {  // enemy territory
                if(hasUnique("This unit and all others in adjacent tiles heal 5 additional HP. This unit heals 5 additional HP outside of friendly territory.")) 10
                else 5
            }
        }
    }

    fun moveToTile(otherTile: TileInfo) {
        if(otherTile==getTile()) return // already here!
        val distanceToTiles = getDistanceToTiles()

        class YouCantGetThereFromHereException(msg: String) : Exception(msg)
        if (!distanceToTiles.containsKey(otherTile))
            throw YouCantGetThereFromHereException("$this can't get from ${currentTile.position} to ${otherTile.position}.")

        class CantEnterThisTileException(msg: String) : Exception(msg)
        if(!canMoveTo(otherTile))
            throw CantEnterThisTileException("$this can't enter $otherTile")
        if(otherTile.isCityCenter() && otherTile.getOwner()!=civInfo)
            throw Exception("This is an enemy city, you can't go here!")

        currentMovement -= distanceToTiles[otherTile]!!
        if (currentMovement < 0.1) currentMovement = 0f // silly floats which are "almost zero"
        if(isFortified() || action=="Set Up" || action=="Sleep") action=null // unfortify/setup after moving
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
        due = true
        val tileOwner = getTile().getOwner()
        if(tileOwner!=null && !civInfo.canEnterTiles(tileOwner) && !tileOwner.isCityState()) // if an enemy city expanded onto this tile while I was in it
            movementAlgs().teleportToClosestMoveableTile()
        doPreTurnAction()
    }

    fun destroy(){
        removeFromTile()
        civInfo.removeUnit(this)
    }

    fun removeFromTile(){
        if (type.isCivilian()) getTile().civilianUnit=null
        else getTile().militaryUnit=null
    }

    fun putInTile(tile:TileInfo){
        if(!canMoveTo(tile)) throw Exception("I can't go there!")
        if(type.isCivilian())
            tile.civilianUnit=this
        else tile.militaryUnit=this
        currentTile = tile
        if(tile.improvement=="Ancient ruins" && !civInfo.isBarbarianCivilization())
            getAncientRuinBonus()

        updateViewableTiles()
    }

    fun disband(){
        destroy()
        if(currentTile.isCityCenter() && currentTile.getOwner()==civInfo)
            civInfo.gold += baseUnit.getDisbandGold()
    }

    private fun getAncientRuinBonus() {
        currentTile.improvement=null
        val actions: ArrayList<() -> Unit> = ArrayList()
        if(civInfo.cities.isNotEmpty()) actions.add {
            val city = civInfo.cities.random()
            city.population.population++
            city.population.autoAssignPopulation()
            civInfo.addNotification("We have found survivors in the ruins - population added to ["+city.name+"]",currentTile.position, Color.GREEN)
        }
        val researchableAncientEraTechs = GameBasics.Technologies.values
                .filter {
                    !civInfo.tech.isResearched(it.name)
                            && civInfo.tech.canBeResearched(it.name)
                            && it.era() == TechEra.Ancient
                }
        if(researchableAncientEraTechs.isNotEmpty())
            actions.add {
                val tech = researchableAncientEraTechs.random().name
                civInfo.tech.addTechnology(tech)
                civInfo.addNotification("We have discovered the lost technology of [$tech] in the ruins!",currentTile.position, Color.BLUE)
            }

        actions.add {
            val chosenUnit = listOf(Constants.settler, Constants.worker,"Warrior").random()
            if (!civInfo.isCityState() || chosenUnit != Constants.settler) { //City states don't get settler from ruins
                civInfo.placeUnitNearTile(currentTile.position, chosenUnit)
                civInfo.addNotification("A [$chosenUnit] has joined us!", currentTile.position, Color.BROWN)
            }
        }

        if(!type.isCivilian())
            actions.add {
                promotions.XP+=10
                civInfo.addNotification("An ancient tribe trains our [$name] in their ways of combat!",currentTile.position, Color.RED)
            }

        actions.add {
            val amount = listOf(25,60,100).random()
            civInfo.gold+=amount
            civInfo.addNotification("We have found a stash of [$amount] gold in the ruins!",currentTile.position, Color.GOLD)
        }

        (actions.random())()
    }

    fun assignOwner(civInfo:CivilizationInfo){
        owner=civInfo.civName
        this.civInfo=civInfo
        civInfo.addUnit(this)
    }

    //endregion
}