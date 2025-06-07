package com.unciv.models.ruleset.nation

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.stats.INamed
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText

class Difficulty: INamed, ICivilopediaText {
    override lateinit var name: String
    var baseHappiness: Int = 0
    var extraHappinessPerLuxury: Float = 0f
    var researchCostModifier: Float = 1f
    var unitCostModifier: Float = 1f
    var unitSupplyBase: Int = 5
    var unitSupplyPerCity: Int = 2
    var buildingCostModifier: Float = 1f
    var policyCostModifier: Float = 1f
    var unhappinessModifier: Float = 1f
    var barbarianBonus: Float = 0f
    var barbarianSpawnDelay: Int = 0
    var playerBonusStartingUnits = ArrayList<String>()

    var aiDifficultyLevel: String? = null
    var aiCityGrowthModifier: Float = 1f
    var aiUnitCostModifier: Float = 1f
    var aiBuildingCostModifier: Float = 1f
    var aiWonderCostModifier: Float = 1f
    var aiBuildingMaintenanceModifier: Float = 1f
    var aiUnitMaintenanceModifier: Float = 1f
    var aiUnitSupplyModifier: Float = 0f
    var aiFreeTechs = ArrayList<String>()
    var aiMajorCivBonusStartingUnits = ArrayList<String>()
    var aiCityStateBonusStartingUnits = ArrayList<String>()
    var aiUnhappinessModifier: Float = 1f
    var turnBarbariansCanEnterPlayerTiles: Int = 0
    var clearBarbarianCampReward: Int = 25

    // property defined in json but so far unused:
    // var aisExchangeTechs = false

    override var civilopediaText = listOf<FormattedLine>()


    override fun makeLink() = "Difficulty/$name"

    private fun Float.toPercent() = (this * 100).toInt()
    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lines = ArrayList<FormattedLine>()
        lines += FormattedLine("Player settings", header = 3)
        lines += FormattedLine("{Base happiness}: $baseHappiness ${Fonts.happiness}", indent = 1)
        lines += FormattedLine("{Extra happiness per luxury}: ${extraHappinessPerLuxury.toInt()} ${Fonts.happiness}", indent = 1)
        lines += FormattedLine("{Research cost modifier}: ${researchCostModifier.toPercent()}% ${Fonts.science}", indent = 1)
        lines += FormattedLine("{Unit cost modifier}: ${unitCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{Building cost modifier}: ${buildingCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{Policy cost modifier}: ${policyCostModifier.toPercent()}% ${Fonts.culture}", indent = 1)
        lines += FormattedLine("{Unhappiness modifier}: ${unhappinessModifier.toPercent()}%", indent = 1)
        lines += FormattedLine("{Bonus vs. Barbarians}: ${barbarianBonus.toPercent()}% ${Fonts.strength}", indent = 1)
        lines += FormattedLine("{Barbarian spawning delay}: $barbarianSpawnDelay", indent = 1)

        if (playerBonusStartingUnits.isNotEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine("{Bonus starting units}:", indent = 1)
            playerBonusStartingUnits.groupBy { it }.map {
                it.key to it.value.size     // name to Pair.first and count to Pair.second
            }.forEach {
                // Through a virtual Unique was the simplest way to prevent white icons showing for stuff like eraSpecificUnit
                lines += FormattedLine(Unique(if (it.second == 1) "[${it.first}]" else "${it.second} [${it.first}]"), indent = 2)
            }
        }

        lines += FormattedLine()
        lines += FormattedLine("AI settings", header = 3)
        lines += FormattedLine("{AI difficulty level}: {$aiDifficultyLevel}", indent = 1)
        lines += FormattedLine("{AI city growth modifier}: ${aiCityGrowthModifier.toPercent()}% ${Fonts.food}", indent = 1)
        lines += FormattedLine("{AI unit cost modifier}: ${aiUnitCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{AI building cost modifier}: ${aiBuildingCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{AI wonder cost modifier}: ${aiWonderCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        lines += FormattedLine("{AI building maintenance modifier}: ${aiBuildingMaintenanceModifier.toPercent()}% ${Fonts.gold}", indent = 1)
        lines += FormattedLine("{AI unit maintenance modifier}: ${aiUnitMaintenanceModifier.toPercent()}% ${Fonts.gold}", indent = 1)
        lines += FormattedLine("{AI unhappiness modifier}: ${aiUnhappinessModifier.toPercent()}%", indent = 1)

        if (aiFreeTechs.isNotEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine("{AI free techs}:", indent = 1)
            aiFreeTechs.forEach {
                lines += FormattedLine(it, link = "Technology/$it", indent = 2)
            }
        }
        if (aiMajorCivBonusStartingUnits.isNotEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine("{Major AI civilization bonus starting units}:", indent = 1)
            aiMajorCivBonusStartingUnits.groupBy { it }.map {
                it.key to it.value.size
            }.forEach {
                lines += FormattedLine(Unique(if (it.second == 1) "[${it.first}]" else "${it.second} [${it.first}]"), indent = 2)
            }
        }
        if (aiCityStateBonusStartingUnits.isNotEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine("{City state bonus starting units}:", indent = 1)
            aiCityStateBonusStartingUnits.groupBy { it }.map {
                it.key to it.value.size
            }.forEach {
                lines += FormattedLine(Unique(if (it.second == 1) "[${it.first}]" else "${it.second} [${it.first}]"), indent = 2)
            }
        }

        lines += FormattedLine()
        lines += FormattedLine("{Turns until barbarians enter player tiles}: $turnBarbariansCanEnterPlayerTiles ${Fonts.turn}")
        lines += FormattedLine("{Gold reward for clearing barbarian camps}: $clearBarbarianCampReward ${Fonts.gold}")
        return lines
    }

}
