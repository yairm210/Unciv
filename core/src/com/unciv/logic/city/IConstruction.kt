package com.unciv.logic.city

import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import kotlin.math.roundToInt

interface IConstruction : INamed {
    fun getProductionCost(civInfo: CivilizationInfo): Int
    fun getGoldCost(civInfo: CivilizationInfo): Int
    fun isBuildable(cityConstructions: CityConstructions): Boolean
    fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean
    fun postBuildEvent(construction: CityConstructions, wasBought: Boolean = false): Boolean  // Yes I'm hilarious.
    fun getResource(): String?
    fun canBePurchased(): Boolean
}



open class PerpetualConstruction(override var name: String, val description: String) : IConstruction {
    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        return isBuildable(cityConstructions)
    }
    open fun getProductionTooltip(cityInfo: CityInfo) : String
            = "\r\n${(cityInfo.cityStats.currentCityStats.production / CONVERSION_RATE).roundToInt()}/${"{turn}".tr()}"
    open fun getConversionRate(cityInfo: CityInfo) : Int
            = CONVERSION_RATE

    companion object {
        const val CONVERSION_RATE: Int = 4
        val science = object : PerpetualConstruction("Science", "Convert production to science at a rate of [rate] to 1") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean {
                return cityConstructions.cityInfo.civInfo.tech.getTechUniques().contains("Enables conversion of city production to science")
            }
            override fun getProductionTooltip(cityInfo: CityInfo): String {
                return "\r\n${(cityInfo.cityStats.currentCityStats.production / getConversionRate(cityInfo)).roundToInt()}/${"{turn}".tr()}"
            }
            override fun getConversionRate(cityInfo: CityInfo) = (1/cityInfo.cityStats.getScienceConversionRate()).roundToInt()
        }
        val gold = object : PerpetualConstruction("Gold", "Convert production to gold at a rate of $CONVERSION_RATE to 1") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean {
                return cityConstructions.cityInfo.civInfo.tech.getTechUniques().contains("Enables conversion of city production to gold")
            }
        }
        val idle = object : PerpetualConstruction("Nothing", "The city will not produce anything.") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean = true

            override fun getProductionTooltip(cityInfo: CityInfo): String = ""
        }

        val perpetualConstructionsMap: Map<String, PerpetualConstruction>
                = mapOf(science.name to science, gold.name to gold, idle.name to idle)
    }

    override fun canBePurchased(): Boolean {
        return false
    }

    override fun getProductionCost(civInfo: CivilizationInfo): Int {
        throw Exception("Impossible!")
    }

    override fun getGoldCost(civInfo: CivilizationInfo): Int {
        throw Exception("Impossible!")
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        throw Exception("Impossible!")
    }

    override fun postBuildEvent(construction: CityConstructions, wasBought: Boolean): Boolean {
        throw Exception("Impossible!")
    }

    override fun getResource(): String? =null

}