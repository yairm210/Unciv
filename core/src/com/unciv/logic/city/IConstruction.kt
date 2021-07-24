package com.unciv.logic.city

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.Fonts
import kotlin.math.pow
import kotlin.math.roundToInt

interface IConstruction : INamed {
    fun isBuildable(cityConstructions: CityConstructions): Boolean
    fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean
    fun postBuildEvent(cityConstructions: CityConstructions, wasBought: Boolean = false): Boolean  // Yes I'm hilarious.
    fun getResourceRequirements(): HashMap<String,Int>
}

interface INonPerpetualConstruction : IConstruction, INamed {
    val hurryCostModifier: Int
    val uniqueObjects: List<Unique>
    val uniques: List<String>

    fun getProductionCost(civInfo: CivilizationInfo): Int
    fun getStatBuyCost(cityInfo: CityInfo, stat: Stat): Int?
    
    private fun getMatchingUniques(uniqueTemplate: String): Sequence<Unique> {
        return uniqueObjects.asSequence().filter { it.placeholderText == uniqueTemplate }
    }
    
    fun canBePurchasedWithStat(cityInfo: CityInfo, stat: Stat): Boolean {
        if (stat in listOf(Stat.Production, Stat.Happiness)) return false
        if ("Cannot be purchased" in uniques) return false
        if (stat == Stat.Gold) return !uniques.contains("Unbuildable")
        if (getMatchingUniques("Can be purchased with []")
                .filter { it.params[0] == stat.name }
                .any()
        ) return true
        if (getMatchingUniques("Can be purchased with [] []")
                .filter { it.params[0] == stat.name && cityInfo.matchesFilter(it.params[1]) }
                .any()
        ) return true
        if (getMatchingUniques("Can be purchased for [] []")
                .filter { it.params[1] == stat.name}
                .any()
        ) return true
        if (getMatchingUniques("Can be purchased for [] [] []")
                .filter { it.params[1] == stat.name && cityInfo.matchesFilter(it.params[2]) }
                .any()
        ) return true
        return false
    }
    
    fun canBePurchasedWithAnyStat(cityInfo: CityInfo): Boolean {
        return Stat.values().any { canBePurchasedWithStat(cityInfo, it) }
    }
    
    // I can't make this function protected or private :(
    fun getBaseGoldCost(civInfo: CivilizationInfo): Double {
        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        return (30.0 * getProductionCost(civInfo)).pow(0.75) * (1 + hurryCostModifier / 100f)
    }
    
    // I can't make this function protected or private :(
    fun getBaseBuyCost(cityInfo: CityInfo, stat: Stat): Double? {
        if (stat == Stat.Gold) return getBaseGoldCost(cityInfo.civInfo)

        val cost = getMatchingUniques("Can be purchased for [] []")
            .filter { it.params[1] == stat.name }
            .minByOrNull { it.params[0].toInt() }
            ?.params?.get(0)
            ?.toDouble()
        // I don't know if all these question marks are necessary, but Android Studio _really_ wants them
        if (cost != null) return cost

        val alternativeCost = getMatchingUniques("Can be purchased for [] [] []")
            .filter { it.params[1] == stat.name && cityInfo.matchesFilter(it.params[2]) }
            .minByOrNull { it.params[0].toInt() }
            ?.params?.get(0)
            ?.toDouble()
        if (alternativeCost != null) return alternativeCost

        if (getMatchingUniques("Can be purchased with []")
                .filter { it.params[0] == stat.name }
                .any()
            || getMatchingUniques("Can be purchased with [] []")
                .filter { it.params[0] == stat.name && cityInfo.matchesFilter(it.params[1])}
                .any()
        ) return cityInfo.civInfo.gameInfo.ruleSet.eras[cityInfo.civInfo.getEra()]!!.baseUnitBuyCost.toDouble()
        return null
    }
}



open class PerpetualConstruction(override var name: String, val description: String) : IConstruction {
    
    override fun shouldBeDisplayed(cityConstructions: CityConstructions) = isBuildable(cityConstructions)
    open fun getProductionTooltip(cityInfo: CityInfo) : String
            = "\r\n${(cityInfo.cityStats.currentCityStats.production / CONVERSION_RATE).roundToInt()}/${Fonts.turn}"
    open fun getConversionRate(cityInfo: CityInfo) : Int
            = CONVERSION_RATE

    companion object {
        const val CONVERSION_RATE: Int = 4
        val science = object : PerpetualConstruction("Science", "Convert production to science at a rate of [rate] to 1") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean {
                return cityConstructions.cityInfo.civInfo.hasUnique("Enables conversion of city production to science")
            }
            override fun getProductionTooltip(cityInfo: CityInfo): String {
                return "\r\n${(cityInfo.cityStats.currentCityStats.production / getConversionRate(cityInfo)).roundToInt()}/${Fonts.turn}"
            }
            override fun getConversionRate(cityInfo: CityInfo) = (1/cityInfo.cityStats.getScienceConversionRate()).roundToInt()
        }
        val gold = object : PerpetualConstruction("Gold", "Convert production to gold at a rate of $CONVERSION_RATE to 1") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean {
                return cityConstructions.cityInfo.civInfo.hasUnique("Enables conversion of city production to gold")
            }
        }
        val idle = object : PerpetualConstruction("Nothing", "The city will not produce anything.") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean = true

            override fun getProductionTooltip(cityInfo: CityInfo): String = ""
        }

        val perpetualConstructionsMap: Map<String, PerpetualConstruction>
                = mapOf(science.name to science, gold.name to gold, idle.name to idle)
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            throw Exception("Impossible!")

    override fun postBuildEvent(cityConstructions: CityConstructions, wasBought: Boolean) =
            throw Exception("Impossible!")

    override fun getResourceRequirements(): HashMap<String, Int> = hashMapOf()

}