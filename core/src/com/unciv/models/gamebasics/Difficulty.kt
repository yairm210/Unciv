package com.unciv.models.gamebasics

import com.unciv.models.stats.INamed
import java.util.*

class Difficulty: INamed {
    override lateinit var name: String
    var baseHappiness: Int = 0
    var extraHappinessPerLuxuxy: Float = 0f
    var researchCostModifier:Float = 1f
    var unitCostModifier:Float = 1f
    var buildingCostModifier:Float = 1f
    var policyCostModifier:Float = 1f
    var unhappinessModifier:Float = 1f
    var aiCityGrowthModifier:Float = 1f
    var aiUnitCostModifier:Float = 1f
    var aiBuildingCostModifier:Float = 1f
    var aiWonderCostModifier:Float = 1f
    var aiBuildingMaintenanceModifier:Float = 1f
    var aiUnitMaintenanceModifier = 1f
    var aiFreeTechs = ArrayList<String>()
    var aiFreeUnits = ArrayList<String>()
    var aiUnhappinessModifier = 1f
    var aisExchangeTechs = false
}