package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.ICivilopediaText
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts

class Difficulty: INamed, ICivilopediaText {
    override lateinit var name: String
    var baseHappiness: Int = 0
    var extraHappinessPerLuxury: Float = 0f
    var researchCostModifier:Float = 1f
    var unitCostModifier:Float = 1f
    var buildingCostModifier:Float = 1f
    var policyCostModifier:Float = 1f
    var unhappinessModifier:Float = 1f
    var barbarianBonus:Float = 0f
    var startingUnits = ArrayList<String>() // Deprecated since 3.15.8
    var playerBonusStartingUnits = ArrayList<String>()

    var aiCityGrowthModifier:Float = 1f
    var aiUnitCostModifier:Float = 1f
    var aiBuildingCostModifier:Float = 1f
    var aiWonderCostModifier:Float = 1f
    var aiBuildingMaintenanceModifier:Float = 1f
    var aiUnitMaintenanceModifier = 1f
    var aiFreeTechs = ArrayList<String>()
    var aiMajorCivStartingUnits = ArrayList<String>() // Deprecated since 3.15.8
    var aiMajorCivBonusStartingUnits = ArrayList<String>()
    var aiCityStateStartingUnits = ArrayList<String>() // Deprecated since 3.15.8
    var aiCityStateBonusStartingUnits = ArrayList<String>()
    var aiUnhappinessModifier = 1f
    var turnBarbariansCanEnterPlayerTiles = 0
    var clearBarbarianCampReward = 25

    override var civilopediaText = listOf<FormattedLine>()

//    fun getDescription(): String {
//        return getCivilopediaTextLines(RulesetCache.getBaseRuleset()).joinToString("\n") { it.text.tr() }
//    }

    override fun makeLink() = "Difficulty/$name"
    override fun replacesCivilopediaDescription() = true
    override fun hasCivilopediaTextLines() = true

    private fun Float.toPercent() = (this * 100).toInt()
    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lines = ArrayList<FormattedLine>()
        lines += FormattedLine("Player settings", header = 3)
        lines += FormattedLine("{Base Happiness}: $baseHappiness ${Fonts.happiness}", indent = 1)
        lines += FormattedLine("{Extra happiness per luxury}: ${extraHappinessPerLuxury.toInt()} ${Fonts.happiness}", indent = 1)
        lines += FormattedLine("{Research cost modifier}: ${researchCostModifier.toPercent()}% ${Fonts.science}", indent = 1)
        lines += FormattedLine("{Unit cost modifier}: ${unitCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{Building cost modifier}: ${buildingCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{Policy cost modifier}: ${policyCostModifier.toPercent()}% ${Fonts.culture}", indent = 1)
        lines += FormattedLine("{Unhappiness modifier}: ${unhappinessModifier.toPercent()}%", indent = 1)
        lines += FormattedLine("{Bonus vs. Barbarians}: ${barbarianBonus.toPercent()}% ${Fonts.strength}", indent = 1)
//        lines += FormattedLine("{Starting units}: $startingUnits", indent = 1)
        lines += FormattedLine()
        lines += FormattedLine("AI settings", header = 3)
        lines += FormattedLine("{AI city growth modifier}: ${aiCityGrowthModifier.toPercent()}% ${Fonts.food}", indent = 1)
        lines += FormattedLine("{AI unit cost modifier}: ${aiUnitCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{AI building cost modifier}: ${aiBuildingCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{AI wonder cost modifier}: ${aiWonderCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{AI building maintenance modifier}: ${aiBuildingMaintenanceModifier.toPercent()}% ${Fonts.gold}", indent = 1)
        lines += FormattedLine("{AI unit maintenance modifier}: ${aiUnitMaintenanceModifier.toPercent()}% ${Fonts.gold}", indent = 1)
//        lines += FormattedLine("{AI free techs}: $aiFreeTechs", indent = 1)
//        lines += FormattedLine("{AI major civilizations starting units}: $aiMajorCivStartingUnits", indent = 1)
//        lines += FormattedLine("{AI city-state starting units}: $aiCityStateStartingUnits", indent = 1)
        lines += FormattedLine("{AI unhappiness modifier}: ${aiUnhappinessModifier.toPercent()}%", indent = 1)
        lines += FormattedLine()
        lines += FormattedLine("{Turns until barbarians enter player tiles}: $turnBarbariansCanEnterPlayerTiles ${Fonts.turn}")
        lines += FormattedLine("{Gold reward for clearing barbarian camps}: $clearBarbarianCampReward ${Fonts.gold}")
        return lines
    }

}
