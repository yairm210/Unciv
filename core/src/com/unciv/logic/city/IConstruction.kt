package com.unciv.logic.city

import com.unciv.models.stats.INamed

interface IConstruction : INamed {
    fun getProductionCost(adoptedPolicies: List<String>): Int
    fun getGoldCost(adoptedPolicies: List<String>): Int
    fun isBuildable(construction: CityConstructions): Boolean
    fun postBuildEvent(construction: CityConstructions)  // Yes I'm hilarious.
}
