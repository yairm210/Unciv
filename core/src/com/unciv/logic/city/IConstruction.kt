package com.unciv.logic.city

import com.unciv.models.linq.Linq
import com.unciv.models.stats.INamed

interface IConstruction : INamed {
    fun getProductionCost(adoptedPolicies: Linq<String>): Int
    fun getGoldCost(adoptedPolicies: Linq<String>): Int
    fun isBuildable(construction: CityConstructions): Boolean
    fun postBuildEvent(construction: CityConstructions)  // Yes I'm hilarious.
}
