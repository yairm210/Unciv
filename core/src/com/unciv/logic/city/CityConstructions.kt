package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.withItem
import com.unciv.ui.utils.withoutItem
import java.util.*
import kotlin.collections.ArrayList


class CityConstructions {
    @Transient lateinit var cityInfo: CityInfo
    @Transient private var builtBuildingObjects=ArrayList<Building>()

    var builtBuildings = HashSet<String>()
    val inProgressConstructions = HashMap<String, Int>()
    var currentConstruction: String = "Monument" // default starting building!
    var currentConstructionIsUserSet = false

    //region pure functions
    fun clone(): CityConstructions {
        val toReturn = CityConstructions()
        toReturn.builtBuildings.addAll(builtBuildings)
        toReturn.inProgressConstructions.putAll(inProgressConstructions)
        toReturn.currentConstruction=currentConstruction
        return toReturn
    }

    internal fun getBuildableBuildings(): List<Building> = GameBasics.Buildings.values
            .filter { it.isBuildable(this) }

    fun getConstructableUnits() = GameBasics.Units.values
            .filter { it.isBuildable(this) }

    fun getStats(): Stats {
        val stats = Stats()
        for (building in getBuiltBuildings())
            stats.add(building.getStats(cityInfo.civInfo))
        stats.science += (cityInfo.getBuildingUniques().count { it == "+1 Science Per 2 Population" } * cityInfo.population.population / 2).toFloat()
        return stats
    }

    fun getMaintenanceCosts(): Int = getBuiltBuildings().sumBy { it.maintenance }

    fun getStatPercentBonuses(): Stats {
        val stats = Stats()
        for (building in getBuiltBuildings())
            stats.add(building.getStatPercentageBonuses(cityInfo.civInfo))
        return stats
    }

    fun getCityProductionTextForCityButton(): String {
        val currentConstructionSnapshot = currentConstruction // See below
        var result = currentConstructionSnapshot .tr()
        if (currentConstructionSnapshot!=""
                && SpecialConstruction.getSpecialConstructions().none { it.name==currentConstructionSnapshot  })
            result += "\r\n" + turnsToConstruction(currentConstructionSnapshot ) + " {turns}".tr()
        return result
    }

    fun getProductionForTileInfo(): String {
        val currentConstructionSnapshot = currentConstruction // this is because there were rare errors tht I assume were caused because currentContruction changed on another thread
        var result = currentConstructionSnapshot.tr()
        if (currentConstructionSnapshot!=""
                && SpecialConstruction.getSpecialConstructions().none { it.name==currentConstructionSnapshot })
            result += "\r\n{in} ".tr() + turnsToConstruction(currentConstructionSnapshot) + " {turns}".tr()
        return result
    }

    fun getCurrentConstruction(): IConstruction = getConstruction(currentConstruction)

    fun isBuilt(buildingName: String): Boolean = builtBuildings.contains(buildingName)

    fun isBeingConstructed(constructionName: String): Boolean = currentConstruction == constructionName

    fun isBuildingWonder(): Boolean {
        val currentConstruction = getCurrentConstruction()
        if (currentConstruction is Building) {
            return currentConstruction.isWonder
        }
        return false
    }

    internal fun getConstruction(constructionName: String): IConstruction {
        if (GameBasics.Buildings.containsKey(constructionName))
            return GameBasics.Buildings[constructionName]!!
        else if (GameBasics.Units.containsKey(constructionName))
            return GameBasics.Units[constructionName]!!
        else{
            if(constructionName=="") return getConstruction("Nothing")
            val special = SpecialConstruction.getSpecialConstructions().firstOrNull{it.name==constructionName}
            if(special!=null) return special
        }

        class NotBuildingOrUnitException(message:String):Exception(message)
        throw NotBuildingOrUnitException("$constructionName is not a building or a unit!")
    }

    internal fun getBuiltBuildings(): List<Building> = builtBuildingObjects // toList os to avoid concurrency problems

    fun containsBuildingOrEquivalent(building: String): Boolean =
            isBuilt(building) || getBuiltBuildings().any{it.replaces==building}

    fun getWorkDone(constructionName: String): Int {
        if (inProgressConstructions.containsKey(constructionName)) return inProgressConstructions[constructionName]!!
        else return 0
    }

    fun getRemainingWork(constructionName: String) =
            getConstruction(constructionName).getProductionCost(cityInfo.civInfo.policies.adoptedPolicies) - getWorkDone(constructionName)

    fun turnsToConstruction(constructionName: String): Int {
        val workLeft = getRemainingWork(constructionName)

        // The ol' Switcharoo - what would our stats be if that was our current construction?
        // Since this is only ever used for UI purposes, I feel fine with having it be a bit inefficient
        //   and recalculating the entire city stats
        val currConstruction = currentConstruction
        currentConstruction = constructionName
        cityInfo.cityStats.update()
        val cityStatsForConstruction = cityInfo.cityStats.currentCityStats
        // revert!
        currentConstruction = currConstruction
        cityInfo.cityStats.update()

        var production = Math.round(cityStatsForConstruction.production)
        if (constructionName == Constants.settler) production += cityStatsForConstruction.food.toInt()

        return Math.ceil((workLeft / production.toDouble())).toInt()
    }
    //endregion

    //region state changing functions
    fun setTransients(){
        builtBuildingObjects = ArrayList(builtBuildings.map { GameBasics.Buildings[it]!! })
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
            val productionCost = construction.getProductionCost(cityInfo.civInfo.policies.adoptedPolicies)
            if (inProgressConstructions.containsKey(currentConstruction)
                    && inProgressConstructions[currentConstruction]!! >= productionCost) {
                constructionComplete(construction)
            }
        }
    }

    fun endTurn(cityStats: Stats) {
        stopUnbuildableConstruction()

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

    fun constructionComplete(construction: IConstruction) {
        construction.postBuildEvent(this)
        inProgressConstructions.remove(currentConstruction)

        if (construction is Building && construction.isWonder) {
            for (civ in cityInfo.civInfo.gameInfo.civilizations) {
                if (civ.exploredTiles.contains(cityInfo.location))
                    civ.addNotification("[$currentConstruction] has been built in [${cityInfo.name}]", cityInfo.location, Color.BROWN)
                else
                    civ.addNotification("[$currentConstruction] has been built in a faraway land",null,Color.BROWN)

            }
        } else
            cityInfo.civInfo.addNotification("[$currentConstruction] has been built in [" + cityInfo.name + "]", cityInfo.location, Color.BROWN)

        cancelCurrentConstruction()
    }

    fun addBuilding(buildingName:String){
        val buildingObject = GameBasics.Buildings[buildingName]!!
        builtBuildingObjects = builtBuildingObjects.withItem(buildingObject)
        builtBuildings.add(buildingName)
    }

    fun removeBuilding(buildingName:String){
        val buildingObject = GameBasics.Buildings[buildingName]!!
        builtBuildingObjects = builtBuildingObjects.withoutItem(buildingObject)
        builtBuildings.remove(buildingName)
    }

    fun purchaseBuilding(buildingName: String) {
        cityInfo.civInfo.gold -= getConstruction(buildingName).getGoldCost(cityInfo.civInfo)
        getConstruction(buildingName).postBuildEvent(this)
        if (currentConstruction == buildingName)
            cancelCurrentConstruction()

        cityInfo.cityStats.update()
        cityInfo.civInfo.updateDetailedCivResources() // this building could be a resource-requiring one
    }

    fun addCultureBuilding() {
        val basicCultureBuildings = listOf("Monument", "Temple", "Opera House", "Museum")
                .map { cityInfo.civInfo.getEquivalentBuilding(it) }

        val buildableCultureBuildings = basicCultureBuildings
                .filter { it.isBuildable(this)}

        if (buildableCultureBuildings.isEmpty()) return
        val cultureBuildingToBuild = buildableCultureBuildings.minBy { it.cost }!!.name
        addBuilding(cultureBuildingToBuild)
        if (currentConstruction == cultureBuildingToBuild)
            cancelCurrentConstruction()
    }

    fun cancelCurrentConstruction(){
        currentConstructionIsUserSet=false
        currentConstruction=""
        chooseNextConstruction()
    }

    fun chooseNextConstruction() {
        if(currentConstructionIsUserSet) return
        Automation().chooseNextConstruction(this)
    }
    //endregion

} // for json parsing, we need to have a default constructor