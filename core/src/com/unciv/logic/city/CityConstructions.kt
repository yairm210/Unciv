package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.automation.Automation
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.tr
import com.unciv.ui.utils.withItem
import com.unciv.ui.utils.withoutItem
import java.util.*
import kotlin.collections.ArrayList


class CityConstructions {
    @Transient lateinit var cityInfo: CityInfo
    @Transient private var builtBuildingObjects=ArrayList<Building>()

    var builtBuildings = ArrayList<String>()
    private val inProgressConstructions = HashMap<String, Int>()
    var currentConstruction: String = "Monument" // default starting building!

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
            stats.add(building.getStats(cityInfo.civInfo.policies.adoptedPolicies))
        stats.science += (cityInfo.getBuildingUniques().count { it == "+1 Science Per 2 Population" } * cityInfo.population.population / 2).toFloat()
        return stats
    }

    fun getMaintenanceCosts(): Int = getBuiltBuildings().sumBy { it.maintenance }

    fun getStatPercentBonuses(): Stats {
        val stats = Stats()
        for (building in getBuiltBuildings().filter { it.percentStatBonus != null })
            stats.add(building.percentStatBonus!!)
        return stats
    }

    fun getCityProductionTextForCityButton(): String {
        var result = currentConstruction.tr()
        if (SpecialConstruction.getSpecialConstructions().none { it.name==currentConstruction })
            result += "\r\n" + turnsToConstruction(currentConstruction) + " {turns}".tr()
        return result
    }

    fun getProductionForTileInfo(): String {
        var result = currentConstruction.tr()
        if (SpecialConstruction.getSpecialConstructions().none { it.name==currentConstruction })
            result += "\r\n{in} ".tr() + turnsToConstruction(currentConstruction) + " {turns}".tr()
        return result
    }

    fun getAmountConstructedText(): String =
            if (SpecialConstruction.getSpecialConstructions().any { it.name== currentConstruction}) ""
            else " (" + getWorkDone(currentConstruction) + "/" +
                getCurrentConstruction().getProductionCost(cityInfo.civInfo.policies.adoptedPolicies) + ")"

    fun getCurrentConstruction(): IConstruction = getConstruction(currentConstruction)

    fun isBuilt(buildingName: String): Boolean = builtBuildings.contains(buildingName)

    fun isBuilding(buildingName: String): Boolean = currentConstruction == buildingName

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

    fun turnsToConstruction(constructionName: String): Int {
        val productionCost = getConstruction(constructionName).getProductionCost(cityInfo.civInfo.policies.adoptedPolicies)

        val workLeft = (productionCost - getWorkDone(constructionName)).toFloat() // needs to be float so that we get the cieling properly ;)

        val cityStats = cityInfo.cityStats.currentCityStats
        var production = Math.round(cityStats.production)
        if (constructionName == Settler) production += cityStats.food.toInt()

        return Math.ceil((workLeft / production.toDouble())).toInt()
    }
    //endregion

    //region state changing functions
    fun setTransients(){
        builtBuildingObjects = ArrayList(builtBuildings.map { GameBasics.Buildings[it]!! })
    }

    fun addProduction(productionToAdd: Int) {
        if (!inProgressConstructions.containsKey(currentConstruction)) inProgressConstructions[currentConstruction] = 0
        inProgressConstructions[currentConstruction] = inProgressConstructions[currentConstruction]!! + productionToAdd
    }

    fun nextTurn(cityStats: Stats) {
        var construction = getConstruction(currentConstruction)
        if(construction is SpecialConstruction) return

        // Let's try to remove the building from the city, and see if we can still build it (we need to remove because of wonders etc.)
        val saveCurrentConstruction = currentConstruction
        currentConstruction = ""
        if (!construction.isBuildable(this)) {
            // We can't build this building anymore! (Wonder has been built / resource is gone / etc.)
            cityInfo.civInfo.addNotification("Cannot continue work on [$saveCurrentConstruction]", cityInfo.location, Color.BROWN)
            Automation().chooseNextConstruction(this)
            construction = getConstruction(currentConstruction)
        } else
            currentConstruction = saveCurrentConstruction

        addProduction(Math.round(cityStats.production))
        val productionCost = construction.getProductionCost(cityInfo.civInfo.policies.adoptedPolicies)
        if (inProgressConstructions[currentConstruction]!! >= productionCost) {
            constructionComplete(construction)
        }

    }

    fun constructionComplete(construction: IConstruction) {
        construction.postBuildEvent(this)
        inProgressConstructions.remove(currentConstruction)

        if (construction is Building && construction.isWonder && construction.requiredBuildingInAllCities == null) {
            val playerCiv = cityInfo.civInfo.gameInfo.getPlayerCivilization()
            val builtLocation = if (playerCiv.exploredTiles.contains(cityInfo.location)) cityInfo.name else "a faraway land"
            playerCiv.addNotification("[$currentConstruction] has been built in [$builtLocation]", cityInfo.location, Color.BROWN)
        } else
            cityInfo.civInfo.addNotification("[$currentConstruction] has been built in [" + cityInfo.name + "]", cityInfo.location, Color.BROWN)

        currentConstruction = ""
        Automation().chooseNextConstruction(this)
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
        cityInfo.civInfo.gold -= getConstruction(buildingName).getGoldCost(cityInfo.civInfo.policies.adoptedPolicies)
        getConstruction(buildingName).postBuildEvent(this)
        if (currentConstruction == buildingName) {
            currentConstruction=""
            Automation().chooseNextConstruction(this)
        }
        cityInfo.cityStats.update()
    }

    fun addCultureBuilding() {
        val cultureBuildingToBuild = listOf("Monument", "Temple", "Opera House", "Museum").firstOrNull { !builtBuildings.contains(it) }
        if (cultureBuildingToBuild == null) return
        getConstruction(cultureBuildingToBuild).postBuildEvent(this)
        if (currentConstruction == cultureBuildingToBuild) {
            currentConstruction=""
            Automation().chooseNextConstruction(this)
        }
    }

    fun chooseNextConstruction() {
        Automation().chooseNextConstruction(this)
    }
    //endregion

    companion object {
        internal const val Worker = "Worker"
        internal const val Settler = "Settler"
    }

} // for json parsing, we need to have a default constructor