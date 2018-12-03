package com.unciv.models.gamebasics

import com.unciv.models.stats.INamed
import java.util.*

class Difficulty: INamed {
    override lateinit var name: String
    var baseHappiness: Int = 0
    var researchCostModifier:Float = 1f
    var unhappinessModifier = 1f
    var aiCityGrowthModifier = 1f
    var aiUnitMaintenanceModifier = 1f
    var aiYieldModifier = 1f
    var aiFreeTechs = ArrayList<String>()
    var aiUnhappinessModifier = 1f
}