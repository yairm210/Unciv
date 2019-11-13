package com.unciv.logic.city

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.INamed

interface IConstruction : INamed {
    fun getProductionCost(civInfo: CivilizationInfo): Int
    fun getGoldCost(civInfo: CivilizationInfo): Int
    fun isBuildable(construction: CityConstructions): Boolean
    fun shouldBeDisplayed(construction: CityConstructions): Boolean
    fun postBuildEvent(construction: CityConstructions)  // Yes I'm hilarious.
    fun canBePurchased(): Boolean
}



open class SpecialConstruction(override var name: String, val description: String) : IConstruction{
    override fun shouldBeDisplayed(construction: CityConstructions): Boolean {
        return isBuildable(construction)
    }

    companion object {
        val science =  object:SpecialConstruction("Science", "Convert production to science at a rate of 4 to 1"){
            override fun isBuildable(construction: CityConstructions): Boolean {
                return construction.cityInfo.civInfo.tech.getTechUniques().contains("Enables conversion of city production to science")
            }
        }
        val gold =  object:SpecialConstruction("Gold", "Convert production to gold at a rate of 4 to 1"){
            override fun isBuildable(construction: CityConstructions): Boolean {
                return construction.cityInfo.civInfo.tech.getTechUniques().contains("Enables conversion of city production to gold")
            }
        }
        val idle =  object:SpecialConstruction("Nothing", "The city will not produce anything."){
            override fun isBuildable(construction: CityConstructions): Boolean {
                return true
            }
        }
        fun getSpecialConstructions(): List<SpecialConstruction> {
            return listOf(science,gold,idle)
        }
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

    override fun postBuildEvent(construction: CityConstructions) {
        throw Exception("Impossible!")
    }

}