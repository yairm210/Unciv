package com.unciv.logic.city

import com.unciv.logic.Automation
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.stats.Stats
import java.util.*


class CityConstructions {
    @Transient
    lateinit var cityInfo: CityInfo

    var builtBuildings = ArrayList<String>()
    private val inProgressConstructions = HashMap<String, Int>()
    var currentConstruction: String = "Monument" // default starting building!


    internal fun getBuildableBuildings(): List<String> = GameBasics.Buildings.values
            .filter { it.isBuildable(this) }.map { it.name }

    // Library and public school unique (not actualy unique, though...hmm)
    fun getStats(): Stats {
        val stats = Stats()
        for (building in getBuiltBuildings())
            stats.add(building.getStats(cityInfo.civInfo.policies.adoptedPolicies))
        stats.science += (cityInfo.buildingUniques.count({ it == "SciencePer2Pop" }) * cityInfo.population.population / 2).toFloat()
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
        var result = currentConstruction
        if (result != "Science" && result != "Gold")
            result += "\r\n" + turnsToConstruction(currentConstruction) + " turns"
        return result
    }

    fun getProductionForTileInfo(): String {
        var result = currentConstruction
        if (result != "Science" && result != "Gold")
            result += "\r\nin " + turnsToConstruction(currentConstruction) + " turns,\r\n"
        return result
    }

    fun getAmountConstructedText(): String =
            if (currentConstruction == "Science" || currentConstruction == "Gold") ""
            else " (" + workDone(currentConstruction) + "/" +
                getCurrentConstruction().getProductionCost(cityInfo.civInfo.policies.adoptedPolicies) + ")"

    fun getCurrentConstruction(): IConstruction = getConstruction(currentConstruction)

    fun isBuilt(buildingName: String): Boolean = builtBuildings.contains(buildingName)

    fun isBuilding(buildingName: String): Boolean = currentConstruction == buildingName

    internal fun getConstruction(constructionName: String): IConstruction {
        if (GameBasics.Buildings.containsKey(constructionName))
            return GameBasics.Buildings[constructionName]!!
        else if (GameBasics.Units.containsKey(constructionName))
            return GameBasics.Units[constructionName]!!

        throw Exception("$constructionName is not a building or a unit!")
    }

    internal fun getBuiltBuildings(): List<Building> = builtBuildings.map { GameBasics.Buildings[it]!! }

    fun addConstruction(constructionToAdd: Int) {
        if (!inProgressConstructions.containsKey(currentConstruction)) inProgressConstructions[currentConstruction] = 0
        inProgressConstructions[currentConstruction] = inProgressConstructions[currentConstruction]!! + constructionToAdd
    }

    fun nextTurn(cityStats: Stats) {
        var construction = getConstruction(currentConstruction)

        // Let's try to remove the building from the city, and see if we can still build it (we need to remove because of wonders etc.)
        val saveCurrentConstruction = currentConstruction
        currentConstruction = "lie"
        if (!construction.isBuildable(this)) {
            // We can't build this building anymore! (Wonder has been built / resource is gone / etc.)
            cityInfo.civInfo.addNotification("Cannot continue work on $saveCurrentConstruction", cityInfo.location)
            Automation().chooseNextConstruction(this)
            construction = getConstruction(currentConstruction)
        } else
            currentConstruction = saveCurrentConstruction

        addConstruction(Math.round(cityStats.production))
        val productionCost = construction.getProductionCost(cityInfo.civInfo.policies.adoptedPolicies)
        if (inProgressConstructions[currentConstruction]!! >= productionCost) {
            construction.postBuildEvent(this)
            inProgressConstructions.remove(currentConstruction)

            if(construction is Building && construction.isWonder)
                cityInfo.civInfo.gameInfo.getPlayerCivilization().addNotification(currentConstruction + " has been built in " + cityInfo.name, cityInfo.location)
            else
                cityInfo.civInfo.addNotification(currentConstruction + " has been built in " + cityInfo.name, cityInfo.location)

            Automation().chooseNextConstruction(this)
        }

    }

    private fun workDone(constructionName: String): Int {
        if (inProgressConstructions.containsKey(constructionName)) return inProgressConstructions[constructionName]!!
        else return 0
    }

    fun turnsToConstruction(constructionName: String): Int {
        val productionCost = getConstruction(constructionName).getProductionCost(cityInfo.civInfo.policies.adoptedPolicies)

        val workLeft = (productionCost - workDone(constructionName)).toFloat() // needs to be float so that we get the cieling properly ;)

        val cityStats = cityInfo.cityStats.currentCityStats
        var production = Math.round(cityStats.production)
        if (constructionName == Settler) production += cityStats.food.toInt()

        return Math.ceil((workLeft / production.toDouble())).toInt()
    }

    fun purchaseBuilding(buildingName: String) {
        cityInfo.civInfo.gold -= getConstruction(buildingName).getGoldCost(cityInfo.civInfo.policies.adoptedPolicies)
        getConstruction(buildingName).postBuildEvent(this)
        if (currentConstruction == buildingName) Automation().chooseNextConstruction(this)
        cityInfo.cityStats.update()
    }

    fun addCultureBuilding() {
        val cultureBuildingToBuild = listOf("Monument", "Temple", "Opera House", "Museum").firstOrNull { !builtBuildings.contains(it) }
        if (cultureBuildingToBuild == null) return
        builtBuildings.add(cultureBuildingToBuild)
        if (currentConstruction == cultureBuildingToBuild)
            Automation().chooseNextConstruction(this)
    }

    companion object {
        internal const val Worker = "Worker"
        internal const val Settler = "Settler"
    }

    fun chooseNextConstruction() {
        Automation().chooseNextConstruction(this)
    }
} // for json parsing, we need to have a default constructor