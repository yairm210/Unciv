package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.automation.ConstructionAutomation
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.cityscreen.ConstructionInfoTable
import com.unciv.ui.utils.withItem
import com.unciv.ui.utils.withoutItem
import java.util.*
import kotlin.math.roundToInt

/**
 * City constructions manager.
 *
 * @property cityInfo the city it refers to
 * @property currentConstructionFromQueue the name of the construction is currently worked, default = "Monument"
 * @property currentConstructionIsUserSet a flag indicating if the [currentConstructionFromQueue] has been set by the user or by the AI
 * @property constructionQueue a list of constructions names enqueued
 */
class CityConstructions {
    @Transient lateinit var cityInfo: CityInfo
    @Transient private var builtBuildingObjects = ArrayList<Building>()

    var builtBuildings = HashSet<String>()
    val inProgressConstructions = HashMap<String, Int>()
    @Deprecated("As of 3.7.5, all constructions are in the queue")
    var currentConstruction=""
    var currentConstructionFromQueue: String
        get() {
            if(constructionQueue.isEmpty()) return "" else return constructionQueue.first()
        }
        set(value) { if(constructionQueue.isEmpty()) constructionQueue.add(value) else constructionQueue[0]=value }
    var currentConstructionIsUserSet = false
    var constructionQueue = mutableListOf<String>()
    val queueMaxSize = 10

    //region pure functions
    fun clone(): CityConstructions {
        val toReturn = CityConstructions()
        toReturn.builtBuildings.addAll(builtBuildings)
        toReturn.inProgressConstructions.putAll(inProgressConstructions)
        toReturn.currentConstructionIsUserSet=currentConstructionIsUserSet
        toReturn.constructionQueue.addAll(constructionQueue)
        return toReturn
    }

    internal fun getBuildableBuildings(): Sequence<Building> = cityInfo.getRuleset().buildings.values
            .asSequence().filter { it.isBuildable(this) }

    fun getConstructableUnits() = cityInfo.getRuleset().units.values
            .asSequence().filter { it.isBuildable(this) }

    fun getBasicCultureBuildings() = cityInfo.getRuleset().buildings.values
            .asSequence().filter { it.culture > 0f && !it.isWonder && !it.isNationalWonder && it.replaces == null }

    /**
     * @return [Stats] provided by all built buildings in city plus the bonus from Library
     */
    fun getStats(): Stats {
        val stats = Stats()
        for (building in getBuiltBuildings())
            stats.add(building.getStats(cityInfo.civInfo))
        stats.science += (cityInfo.getBuildingUniques().count { it == "+1 Science Per 2 Population" } * cityInfo.population.population / 2).toFloat()
        return stats
    }

    /**
     * @return Maintenance cost of all built buildings
     */
    fun getMaintenanceCosts(): Int {
        var maintenanceCost = getBuiltBuildings().sumBy { it.maintenance }
        val policyManager = cityInfo.civInfo.policies
        if (policyManager.isAdopted("Legalism") && cityInfo.id in policyManager.legalismState) {
            val buildingName = policyManager.legalismState[cityInfo.id]
            maintenanceCost -= cityInfo.getRuleset().buildings[buildingName]!!.maintenance
        }
        return maintenanceCost
    }

    /**
     * @return Bonus (%) [Stats] provided by all built buildings in city
     */
    fun getStatPercentBonuses(): Stats {
        val stats = Stats()
        for (building in getBuiltBuildings())
            stats.add(building.getStatPercentageBonuses(cityInfo.civInfo))
        return stats
    }

    fun getCityProductionTextForCityButton(): String {
        val currentConstructionSnapshot = currentConstructionFromQueue // See below
        var result = currentConstructionSnapshot.tr()
        if (currentConstructionSnapshot != "") {
            val construction = PerpetualConstruction.perpetualConstructionsMap[currentConstructionSnapshot]
            if (construction == null) {
                val turnsLeft = turnsToConstruction(currentConstructionSnapshot)
                result += ("\r\n" + "Cost".tr() + " " + getConstruction(currentConstructionFromQueue).getProductionCost(cityInfo.civInfo).toString()).tr()
                result += ConstructionInfoTable.turnOrTurns(turnsLeft)
            } else {
                result += construction.getProductionTooltip(cityInfo)
            }
        }
        return result
    }

    fun getProductionForTileInfo(): String {
        /* this is because there were rare errors tht I assume were caused because
           currentContruction changed on another thread */
        val currentConstructionSnapshot = currentConstructionFromQueue
        var result = currentConstructionSnapshot.tr()
        if (currentConstructionSnapshot!=""
                && !PerpetualConstruction.perpetualConstructionsMap.containsKey(currentConstructionSnapshot)) {
            val turnsLeft = turnsToConstruction(currentConstructionSnapshot)
            result += ConstructionInfoTable.turnOrTurns(turnsLeft)
        }
        return result
    }

    fun getCurrentConstruction(): IConstruction = getConstruction(currentConstructionFromQueue)

    fun isBuilt(buildingName: String): Boolean = builtBuildings.contains(buildingName)
    fun isBeingConstructed(constructionName: String): Boolean = currentConstructionFromQueue == constructionName
    fun isEnqueued(constructionName: String): Boolean = constructionQueue.contains(constructionName)
    fun isBeingConstructedOrEnqueued(constructionName: String): Boolean = isBeingConstructed(constructionName) || isEnqueued(constructionName)

    fun isQueueFull(): Boolean = constructionQueue.size == queueMaxSize

    fun isBuildingWonder(): Boolean {
        val currentConstruction = getCurrentConstruction()
        return currentConstruction is Building && currentConstruction.isWonder
    }

    /** If the city is constructing multiple units of the same type, subsequent units will require the full cost  */
    fun isFirstConstructionOfItsKind(constructionQueueIndex: Int, name: String): Boolean {
        // if the construction name is the same as the current construction, it isn't the first
        return constructionQueueIndex == constructionQueue.indexOfFirst { it == name }
    }


    internal fun getConstruction(constructionName: String): IConstruction {
        val gameBasics = cityInfo.getRuleset()
        when {
            gameBasics.buildings.containsKey(constructionName) -> return gameBasics.buildings[constructionName]!!
            gameBasics.units.containsKey(constructionName) -> return gameBasics.units[constructionName]!!
            constructionName == "" -> return getConstruction("Nothing")
            else -> {
                val special = PerpetualConstruction.perpetualConstructionsMap[constructionName]
                if (special != null) return special
            }
        }

        class NotBuildingOrUnitException(message:String):Exception(message)
        throw NotBuildingOrUnitException("$constructionName is not a building or a unit!")
    }

    internal fun getBuiltBuildings(): Sequence<Building> = builtBuildingObjects.asSequence()

    fun containsBuildingOrEquivalent(building: String): Boolean =
            isBuilt(building) || getBuiltBuildings().any{it.replaces==building}

    fun getWorkDone(constructionName: String): Int {
        if (inProgressConstructions.containsKey(constructionName)) return inProgressConstructions[constructionName]!!
        else return 0
    }

    fun getRemainingWork(constructionName: String, useStoredProduction: Boolean = true): Int {
        val constr = getConstruction(constructionName)
        return when {
            constr is PerpetualConstruction -> 0
            useStoredProduction -> constr.getProductionCost(cityInfo.civInfo) - getWorkDone(constructionName)
            else -> constr.getProductionCost(cityInfo.civInfo)
        }
    }

    fun turnsToConstruction(constructionName: String, useStoredProduction: Boolean = true): Int {
        val workLeft = getRemainingWork(constructionName, useStoredProduction)
        if(workLeft < 0) // we've done more work than actually necessary - possible if circumstances cause buildings to be cheaper later
            return 1 // we'll finish this next turn

        val cityStatsForConstruction: Stats
        if (currentConstructionFromQueue == constructionName) cityStatsForConstruction = cityInfo.cityStats.currentCityStats
        else {
            // The ol' Switcharoo - what would our stats be if that was our current construction?
            // Since this is only ever used for UI purposes, I feel fine with having it be a bit inefficient
            //   and recalculating the entire city stats
            // We don't want to change our current construction queue - what if we have an empty queue, too many changes to check for -
            //  So we ust clone it and see what would happen f that was our construction
            val cityConstructionsClone = clone()
            cityConstructionsClone.currentConstructionFromQueue = constructionName
            cityConstructionsClone.cityInfo = cityInfo
            cityConstructionsClone.setTransients()
            cityInfo.cityConstructions = cityConstructionsClone
            cityInfo.cityStats.update()
            cityStatsForConstruction = cityInfo.cityStats.currentCityStats
            // revert!
            cityInfo.cityConstructions = this
            cityInfo.cityStats.update()
        }

        var production = cityStatsForConstruction.production.roundToInt()
        if (constructionName == Constants.settler) production += cityStatsForConstruction.food.toInt()

        return Math.ceil((workLeft / production.toDouble())).toInt()
    }
    //endregion

    //region state changing functions
    fun setTransients(){
        builtBuildingObjects = ArrayList(builtBuildings.map { cityInfo.getRuleset().buildings[it]
                    ?: throw java.lang.Exception("Building $it is not found!")})
    }

    fun addProductionPoints(productionToAdd: Int) {
        if (!inProgressConstructions.containsKey(currentConstructionFromQueue))
            inProgressConstructions[currentConstructionFromQueue] = 0
        inProgressConstructions[currentConstructionFromQueue] = inProgressConstructions[currentConstructionFromQueue]!! + productionToAdd
    }

    fun constructIfEnough(){
        validateConstructionQueue()

        val construction = getConstruction(currentConstructionFromQueue)
        if(construction is PerpetualConstruction) chooseNextConstruction() // check every turn if we could be doing something better, because this doesn't end by itself
        else {
            val productionCost = construction.getProductionCost(cityInfo.civInfo)
            if (inProgressConstructions.containsKey(currentConstructionFromQueue)
                    && inProgressConstructions[currentConstructionFromQueue]!! >= productionCost) {
                constructionComplete(construction)
            }
        }
    }

    fun endTurn(cityStats: Stats) {
        validateConstructionQueue()
        validateInProgressConstructions()

        if(getConstruction(currentConstructionFromQueue) !is PerpetualConstruction)
            addProductionPoints(cityStats.production.roundToInt())
    }


    private fun validateConstructionQueue() {
        val queueSnapshot = constructionQueue.toMutableList()
        constructionQueue.clear()

        for (construction in queueSnapshot) {
            if (getConstruction(construction).isBuildable(this))
                constructionQueue.add(construction)
        }
    }

    private fun validateInProgressConstructions() {
        // remove obsolete stuff from in progress constructions - happens often and leaves clutter in memory and save files
        // should have NO visible consequences - any accumulated points that may be reused later should stay (nukes when manhattan project city lost, nat wonder when conquered an empty city...)
        // Needs only be called once in a while - endTurn is enough
        val inProgressSnapshot = inProgressConstructions.keys.filter { it != currentConstructionFromQueue }
        for (constructionName in inProgressSnapshot) {
            val rejectionReason: String =
                    when (val construction = getConstruction(constructionName)) {
                        is Building -> construction.getRejectionReason(this)
                        is BaseUnit -> construction.getRejectionReason(this)
                        else -> ""
                    }

            if (rejectionReason.endsWith("lready built")
                    || rejectionReason.startsWith("Cannot be built with")
                    || rejectionReason.startsWith("Don't need to build any more")
                    || rejectionReason.startsWith("Obsolete")
            ) inProgressConstructions.remove(constructionName)
        }
    }

    private fun constructionComplete(construction: IConstruction) {
        construction.postBuildEvent(this)
        if (construction.name in inProgressConstructions)
            inProgressConstructions.remove(construction.name)
        if(construction.name == currentConstructionFromQueue)
            removeCurrentConstruction()

        validateConstructionQueue() // if we've build e.g. the Great Lighthouse, then Lighthouse is no longer relevant in the queue

        if (construction is Building && construction.isWonder) {
            cityInfo.civInfo.popupAlerts.add(PopupAlert(AlertType.WonderBuilt, construction.name))
            for (civ in cityInfo.civInfo.gameInfo.civilizations) {
                if (civ.exploredTiles.contains(cityInfo.location))
                    civ.addNotification("[${construction.name}] has been built in [${cityInfo.name}]", cityInfo.location, Color.BROWN)
                else
                    civ.addNotification("[${construction.name}] has been built in a faraway land",null,Color.BROWN)
            }
        } else
            cityInfo.civInfo.addNotification("[${construction.name}] has been built in [" + cityInfo.name + "]", cityInfo.location, Color.BROWN)
    }

    fun addBuilding(buildingName:String){
        val buildingObject = cityInfo.getRuleset().buildings[buildingName]!!
        builtBuildingObjects = builtBuildingObjects.withItem(buildingObject)
        builtBuildings.add(buildingName)
    }

    fun removeBuilding(buildingName:String){
        val buildingObject = cityInfo.getRuleset().buildings[buildingName]!!
        builtBuildingObjects = builtBuildingObjects.withoutItem(buildingObject)
        builtBuildings.remove(buildingName)
    }

    /**
     *  Purchase a construction for gold
     *  called from NextTurnAutomation and the City UI
     *  Build / place the new item, deduct cost, and maintain queue.
     *
     *  @param constructionName What to buy (needed since buying something not queued is allowed)
     *  @param queuePosition    Position in the queue or -1 if not from queue
     *                          Note: -1 does not guarantee queue will remain unchanged (validation)
     *  @param automatic        Flag whether automation should try to choose what next to build (not coming from UI)
     *                          Note: settings.autoAssignCityProduction is handled later
     *  @return                 Success (false e.g. unit cannot be placed
     */
    fun purchaseConstruction(constructionName: String, queuePosition: Int, automatic: Boolean): Boolean {
        if (!getConstruction(constructionName).postBuildEvent(this, true))
            return false // nothing built - no pay

        cityInfo.civInfo.gold -= getConstruction(constructionName).getGoldCost(cityInfo.civInfo)

        if (queuePosition in 0 until constructionQueue.size)
            removeFromQueue(queuePosition, automatic)
        validateConstructionQueue()

        return true
    }

    fun hasBuildableCultureBuilding(): Boolean {
        return getBasicCultureBuildings()
                .map { cityInfo.civInfo.getEquivalentBuilding(it.name) }
                .filter { it.isBuildable(this) || isBeingConstructedOrEnqueued(it.name) }
                .any()
    }

    fun addCultureBuilding(): String? {
        val buildableCultureBuildings = getBasicCultureBuildings()
                .map { cityInfo.civInfo.getEquivalentBuilding(it.name) }
                .filter { it.isBuildable(this) || isBeingConstructedOrEnqueued(it.name) }

        if (!buildableCultureBuildings.any())
            return null

        val cultureBuildingToBuild = buildableCultureBuildings.minBy { it.cost }!!.name
        constructionComplete(getConstruction(cultureBuildingToBuild))

        return cultureBuildingToBuild
    }

    private fun removeCurrentConstruction() = removeFromQueue(0,true)

    fun chooseNextConstruction() {
        if(currentConstructionIsUserSet) return

        validateConstructionQueue()
        if (constructionQueue.isNotEmpty()) {
            currentConstructionIsUserSet = true
            if (currentConstructionFromQueue != "") return
        }

        ConstructionAutomation(this).chooseNextConstruction()
    }

    fun addToQueue(constructionName: String) {
        if (isQueueFull()) return
        if (currentConstructionFromQueue == "" || currentConstructionFromQueue == "Nothing") {
            currentConstructionFromQueue = constructionName
            currentConstructionIsUserSet = true
        } else
            constructionQueue.add(constructionName)
    }

    /** If this was done automatically, we should automatically try to choose a new construction and treat it as such */
    fun removeFromQueue(constructionQueueIndex: Int, automatic:Boolean) {
        constructionQueue.removeAt(constructionQueueIndex)
        if (constructionQueue.isEmpty()){
            if(automatic) chooseNextConstruction()
            else constructionQueue.add("Nothing") // To prevent Construction Automation
            currentConstructionIsUserSet = false
        }
        else currentConstructionIsUserSet = true // we're just continuing the regular queue
    }

    fun raisePriority(constructionQueueIndex: Int) {
        constructionQueue.swap(constructionQueueIndex - 1, constructionQueueIndex)
    }

    // Lowering == Highering next element in queue
    fun lowerPriority(constructionQueueIndex: Int) {
        raisePriority(constructionQueueIndex+1)
    }

    //endregion
    private fun MutableList<String>.swap(idx1: Int, idx2: Int) {
        val tmp = this[idx1]
        this[idx1] = this[idx2]
        this[idx2] = tmp
    }
} // for json parsing, we need to have a default constructor