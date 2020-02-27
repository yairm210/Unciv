package com.unciv.logic.city

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import kotlin.math.roundToInt

interface IConstruction : INamed {
    fun getProductionCost(civInfo: CivilizationInfo): Int
    fun getGoldCost(civInfo: CivilizationInfo): Int
    fun isBuildable(construction: CityConstructions): Boolean
    fun shouldBeDisplayed(construction: CityConstructions): Boolean
    fun postBuildEvent(construction: CityConstructions): Boolean  // Yes I'm hilarious.
    fun canBePurchased(): Boolean
}



open class SpecialConstruction(override var name: String, val description: String) : IConstruction{
    override fun shouldBeDisplayed(construction: CityConstructions): Boolean {
        return isBuildable(construction)
    }
    open fun getProductionTooltip(cityInfo: CityInfo) : String
            = "\r\n${(cityInfo.cityStats.currentCityStats.production / CONVERSION_RATE).roundToInt()}/${"{turn}".tr()}"

    companion object {
        const val CONVERSION_RATE: Int = 4
        val science = object : SpecialConstruction("Science", "Convert production to science at a rate of $CONVERSION_RATE to 1") {
            override fun isBuildable(construction: CityConstructions): Boolean {
                return construction.cityInfo.civInfo.tech.getTechUniques().contains("Enables conversion of city production to science")
            }
        }
        val gold = object : SpecialConstruction("Gold", "Convert production to gold at a rate of $CONVERSION_RATE to 1") {
            override fun isBuildable(construction: CityConstructions): Boolean {
                return construction.cityInfo.civInfo.tech.getTechUniques().contains("Enables conversion of city production to gold")
            }
        }
        val idle = object : SpecialConstruction("Nothing", "The city will not produce anything.") {
            override fun isBuildable(construction: CityConstructions): Boolean = true

            override fun getProductionTooltip(cityInfo: CityInfo): String = ""
        }

        val specialConstructionsMap: Map<String, SpecialConstruction>
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

    override fun isBuildable(construction: CityConstructions): Boolean {
        throw Exception("Impossible!")
    }

    override fun postBuildEvent(construction: CityConstructions): Boolean {
        throw Exception("Impossible!")
    }

}