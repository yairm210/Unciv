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
        toReturn.currentConstructionIsUserSet=currentConstructionIsUserSet
        return toReturn
    }

    internal fun getBuildableBuildings(): Sequence<Building> = cityInfo.getRuleset().buildings.values
            .asSequence().filter { it.isBuildable(this) }

    fun getConstructableUnits() = cityInfo.getRuleset().units.values
            .asSequence().filter { it.isBuildable(this) }

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
        val currentConstructionSnapshot = currentConstruction // this is because there were rare errors tht I assume were caused because currentContruction changed on another thread
        var result = currentConstructionSnapshot.tr()
        if (currentConstructionSnapshot!=""
                && SpecialConstruction.getSpecialConstructions().none { it.name==currentConstructionSnapshot }) {
            val turnsLeft = turnsToConstruction(currentConstructionSnapshot)
            result += "\r\n" + turnsLeft + (if(turnsLeft>1) " {turns}".tr() else " {turn}".tr())
        }
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
        val gameBasics = cityInfo.getRuleset()
        if (gameBasics.buildings.containsKey(constructionName))
            return gameBasics.buildings[constructionName]!!
        else if (gameBasics.units.containsKey(constructionName))
            return gameBasics.units[constructionName]!!
        else{
            if(constructionName=="") return getConstruction("Nothing")
            val special = SpecialConstruction.getSpecialConstructions().firstOrNull{it.name==constructionName}
            if(special!=null) return special
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

    fun getRemainingWork(constructionName: String): Int {
        val constr = getConstruction(constructionName)
        if (constr is SpecialConstruction) return 0
        return constr.getProductionCost(cityInfo.civInfo) - getWorkDone(constructionName)
    }

    fun turnsToConstruction(constructionName: String): Int {
        val workLeft = getRemainingWork(constructionName)
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
            cityInfo.civInfo.popupAlerts.add(PopupAlert(AlertType.WonderBuilt, construction.name))
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
        ConstructionAutomation(this).chooseNextConstruction()
    }
    //endregion

} // for json parsing, we need to have a default constructor