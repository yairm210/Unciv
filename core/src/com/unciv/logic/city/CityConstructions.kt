package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.automation.ConstructionAutomation
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.ruleset.Building
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.utils.withItem
import com.unciv.ui.utils.withoutItem
import java.util.*

/**
 * City constructions manager.
 *
 * @property cityInfo the city it refers to
 * @property currentConstruction the name of the construction is currently worked, default = "Monument"
 * @property currentConstructionIsUserSet a flag indicating if the [currentConstruction] has been set by the user or by the AI
 * @property constructionQueue a list of constructions names enqueued
 */
class CityConstructions {
    @Transient lateinit var cityInfo: CityInfo
    @Transient private var builtBuildingObjects = ArrayList<Building>()

    var builtBuildings = HashSet<String>()
    val inProgressConstructions = HashMap<String, Int>()
    var currentConstruction: String = "Monument"
    var currentConstructionIsUserSet = false
    var constructionQueue = mutableListOf<String>()
    val queueMaxSize = 10

    //region pure functions
    fun clone(): CityConstructions {
        val toReturn = CityConstructions()
        toReturn.builtBuildings.addAll(builtBuildings)
        toReturn.inProgressConstructions.putAll(inProgressConstructions)
        toReturn.currentConstruction=currentConstruction
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
        val currentConstructionSnapshot = currentConstruction // See below
        var result = currentConstructionSnapshot.tr()
        if (currentConstructionSnapshot!=""
                && SpecialConstruction.getSpecialConstructions().none { it.name==currentConstructionSnapshot  }) {
            val turnsLeft = turnsToConstruction(currentConstructionSnapshot)
            result += ("\r\n" + "Cost".tr() + " " + getConstruction(currentConstruction).getProductionCost(cityInfo.civInfo).toString()).tr()
            result += "\r\n" + turnsLeft + (if(turnsLeft>1) " {turns}".tr() else " {turn}".tr())
        }
        return result
    }

    fun getProductionForTileInfo(): String {
        /* this is because there were rare errors tht I assume were caused because
           currentContruction changed on another thread */
        val currentConstructionSnapshot = currentConstruction
        var result = currentConstructionSnapshot.tr()
        if (currentConstructionSnapshot!=""
                && SpecialConstruction.getSpecialConstructions().none { it.name==currentConstructionSnapshot }) {
            val turnsLeft = turnsToConstruction(currentConstructionSnapshot)
            result += "\r\n" + turnsLeft + (if(turnsLeft>1) " {turns}".tr() else " {turn}".tr())
        }
        return result
    }

    fun getCurrentConstruction(): IConstruction = getConstruction(currentConstruction)
    fun getIConstructionQueue(): List<IConstruction> = constructionQueue.map{ getConstruction(it) }

    fun isBuilt(buildingName: String): Boolean = builtBuildings.contains(buildingName)
    fun isBeingConstructed(constructionName: String): Boolean = currentConstruction == constructionName
    fun isEnqueued(constructionName: String): Boolean = constructionQueue.contains(constructionName)
    fun isBeingConstructedOrEnqueued(constructionName: String): Boolean = isBeingConstructed(constructionName) || isEnqueued(constructionName)

    fun isQueueFull(): Boolean = constructionQueue.size == queueMaxSize
    fun isQueueEmpty(): Boolean = constructionQueue.isEmpty()

    fun isBuildingWonder(): Boolean {
        val currentConstruction = getCurrentConstruction()
        return currentConstruction is Building && currentConstruction.isWonder
    }

    /** If the city is constructing multiple units of the same type, subsequent units will require the full cost  */
    fun isFirstConstructionOfItsKind(constructionQueueIndex: Int, name: String): Boolean {
        // idx = 1 is the currentConstruction, so it is the first
        if (constructionQueueIndex == -1)
            return true

        // if the construction name is the same as the current construction, it isn't the first
        return !isBeingConstructed(name) &&
                constructionQueueIndex == constructionQueue.indexOfFirst{it == name}
    }


    internal fun getConstruction(constructionName: String): IConstruction {
        val gameBasics = cityInfo.getRuleset()
        when {
            gameBasics.buildings.containsKey(constructionName) -> return gameBasics.buildings[constructionName]!!
            gameBasics.units.containsKey(constructionName) -> return gameBasics.units[constructionName]!!
            constructionName=="" -> return getConstruction("Nothing")
            else -> {
                val special = SpecialConstruction.getSpecialConstructions()
                        .firstOrNull{it.name==constructionName}
                if(special!=null) return special
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
            constr is SpecialConstruction -> 0
            useStoredProduction -> constr.getProductionCost(cityInfo.civInfo) - getWorkDone(constructionName)
            else -> constr.getProductionCost(cityInfo.civInfo)
        }
    }

    fun turnsToConstruction(constructionName: String, useStoredProduction: Boolean = true): Int {
        val workLeft = getRemainingWork(constructionName, useStoredProduction)
        if(workLeft < 0) // we've done more work than actually necessary - possible if circumstances cause buildings to be cheaper later
            return 1 // we'll finish this next turn

        val currConstruction = currentConstruction

        val cityStatsForConstruction: Stats
        if (currentConstruction == constructionName) cityStatsForConstruction = cityInfo.cityStats.currentCityStats
        else {
            // The ol' Switcharoo - what would our stats be if that was our current construction?
            // Since this is only ever used for UI purposes, I feel fine with having it be a bit inefficient
            //   and recalculating the entire city stats
            currentConstruction = constructionName
            cityInfo.cityStats.update()
            cityStatsForConstruction = cityInfo.cityStats.currentCityStats
            // revert!
            currentConstruction = currConstruction
            cityInfo.cityStats.update()
        }

        var production = Math.round(cityStatsForConstruction.production)
        if (constructionName == Constants.settler) production += cityStatsForConstruction.food.toInt()

        return Math.ceil((workLeft / production.toDouble())).toInt()
    }
    //endregion

    //region state changing functions
    fun setTransients(){
        builtBuildingObjects = ArrayList(builtBuildings.map { cityInfo.getRuleset().buildings[it]!! })
    }

    fun addProductionPoints(productionToAdd: Int) {
        if (!inProgressConstructions.containsKey(currentConstruction)) inProgressConstructions[currentConstruction] = 0
        inProgressConstructions[currentConstruction] = inProgressConstructions[currentConstruction]!! + productionToAdd
    }

    fun constructIfEnough(){
        stopUnbuildableConstruction()

        val construction = getConstruction(currentConstruction)
        if(construction is SpecialConstruction) chooseNextConstruction() // check every turn if we could be doing something better, because this doesn't end by itself
        else {
            val productionCost = construction.getProductionCost(cityInfo.civInfo)
            if (inProgressConstructions.containsKey(currentConstruction)
                    && inProgressConstructions[currentConstruction]!! >= productionCost) {
                constructionComplete(construction)
                cancelCurrentConstruction()
            }
        }
    }

    fun endTurn(cityStats: Stats) {
        stopUnbuildableConstruction()
        validateConstructionQueue()

        if(getConstruction(currentConstruction) !is SpecialConstruction)
            addProductionPoints(Math.round(cityStats.production))
    }

    private fun stopUnbuildableConstruction() {
        // Let's try to remove the building from the city, and see if we can still build it (we need to remove because of wonders etc.)
        val construction = getConstruction(currentConstruction)

        val saveCurrentConstruction = currentConstruction
        currentConstruction = ""
        if (!construction.isBuildable(this)) {
            // We can't build this building anymore! (Wonder has been built / resource is gone / etc.)
            cityInfo.civInfo.addNotification("[${cityInfo.name}] cannot continue work on [$saveCurrentConstruction]", cityInfo.location, Color.BROWN)
            cancelCurrentConstruction()
        } else
            currentConstruction = saveCurrentConstruction
    }

    private fun validateConstructionQueue() {
        val queueSnapshot = mutableListOf<String>().apply { addAll(constructionQueue) }
        constructionQueue.clear()

        for (construction in queueSnapshot) {
            if (getConstruction(construction).isBuildable(this))
                constructionQueue.add(construction)
        }
    }

    private fun constructionComplete(construction: IConstruction) {
        construction.postBuildEvent(this)
        if (construction.name in inProgressConstructions)
            inProgressConstructions.remove(construction.name)

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

    fun purchaseConstruction(constructionName: String) {
        cityInfo.civInfo.gold -= getConstruction(constructionName).getGoldCost(cityInfo.civInfo)
        getConstruction(constructionName).postBuildEvent(this)
        if (currentConstruction == constructionName)
            cancelCurrentConstruction()

        cityInfo.cityStats.update()
        cityInfo.civInfo.updateDetailedCivResources() // this building/unit could be a resource-requiring one
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

        if (isBeingConstructed(cultureBuildingToBuild))
            cancelCurrentConstruction()
        else if (isEnqueued(cultureBuildingToBuild))
            removeFromQueue(cultureBuildingToBuild)

        return cultureBuildingToBuild
    }

    private fun cancelCurrentConstruction() {
        currentConstructionIsUserSet = false
        currentConstruction = ""
        chooseNextConstruction()
    }

    fun chooseNextConstruction() {
        if(currentConstructionIsUserSet) return

        if (constructionQueue.isNotEmpty()) {

            currentConstructionIsUserSet = true
            currentConstruction = constructionQueue.removeAt(0)
            stopUnbuildableConstruction()

            if (currentConstruction != "") return
        }

        ConstructionAutomation(this).chooseNextConstruction()
    }

    fun addToQueue(constructionName: String) {
        if (!isQueueFull()) {
            if (isQueueEmpty() && currentConstruction == "" || currentConstruction == "Nothing") {
                currentConstruction = constructionName
                currentConstructionIsUserSet = true
            } else
                constructionQueue.add(constructionName)
        }
    }

    fun removeFromQueue(constructionName: String) {
        if (constructionName in constructionQueue)
            constructionQueue.remove(constructionName)
    }

    fun removeFromQueue(constructionQueueIndex: Int) {
        // constructionQueueIndex -1 is the current construction
        if (constructionQueueIndex < 0) {
            // To prevent Construction Automation
            if (constructionQueue.isEmpty()) constructionQueue.add("Nothing")
            cancelCurrentConstruction()
        } else
            constructionQueue.removeAt(constructionQueueIndex)
    }

    fun raisePriority(constructionQueueIndex: Int) {
        // change current construction
        if(constructionQueueIndex == 0) {
            // Add current construction to queue after the first element
            constructionQueue.add(1, currentConstruction)
            cancelCurrentConstruction()
        }
        else
            constructionQueue.swap(constructionQueueIndex-1, constructionQueueIndex)
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