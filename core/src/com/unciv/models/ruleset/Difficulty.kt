package com.unciv.models.ruleset

import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import java.util.*
import kotlin.collections.ArrayList

class Difficulty: INamed {
    override lateinit var name: String
    var baseHappiness: Int = 0
    var extraHappinessPerLuxury: Float = 0f
    var researchCostModifier:Float = 1f
    var unitCostModifier:Float = 1f
    var buildingCostModifier:Float = 1f
    var policyCostModifier:Float = 1f
    var unhappinessModifier:Float = 1f
    var barbarianBonus:Float = 0f

    var aiCityGrowthModifier:Float = 1f
    var aiUnitCostModifier:Float = 1f
    var aiBuildingCostModifier:Float = 1f
    var aiWonderCostModifier:Float = 1f
    var aiBuildingMaintenanceModifier:Float = 1f
    var aiUnitMaintenanceModifier = 1f
    var aiFreeTechs = ArrayList<String>()
    var aiFreeUnits = ArrayList<String>()
    var aiUnhappinessModifier = 1f
    var turnBarbariansCanEnterPlayerTiles = 0
    var clearBarbarianCampReward = 25


    fun getDescription(): String {
        val lines = ArrayList<String>()
        lines += "Player settings"
        lines += " - {Base Happiness}: $baseHappiness"
        lines += " - {Happiness per luxury}: $extraHappinessPerLuxury"
        lines += " - {Research cost modifier}: $researchCostModifier"
        lines += " - {Unit cost modifier}: $researchCostModifier"
        lines += " - {Building cost modifier}: $buildingCostModifier"
        lines += " - {Policy cost modifier}: $policyCostModifier"
        lines += " - {Unhappiness modifier}: $unhappinessModifier"
        lines += " - {Bonus vs. Barbarians}: $barbarianBonus"
        lines += ""
        lines += "AI settings"
        lines += " - {AI city growth modifier}: $aiCityGrowthModifier"
        lines += " - {AI unit cost modifier}: $aiUnitCostModifier"
        lines += " - {AI building cost modifier}: $aiBuildingCostModifier"
        lines += " - {AI wonder cost modifier}: $aiWonderCostModifier"
        lines += " - {AI building maintenance modifier}: $aiBuildingMaintenanceModifier"
        lines += " - {AI unit maintenance modifier}: $aiUnitMaintenanceModifier"
//        lines += " - {AI free techs}: $aiFreeTechs"
//        lines += " - {AI free units}: $aiFreeUnits"
        lines += " - {AI unhappiness modifier}: $aiUnhappinessModifier"
        lines += ""
        lines += "{Turns until barbarians enter player tiles}: $turnBarbariansCanEnterPlayerTiles"
        lines += "{Gold reward for clearing barbarian camps}: $clearBarbarianCampReward"

        return lines.joinToString("\n") { it.tr() }
    }

}