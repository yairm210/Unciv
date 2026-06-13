package com.unciv.models.ruleset.nation

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder.Companion.buildCivilopediaText

class Difficulty : RulesetObject() {
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

    // Note: Difficulty uniques will be treated as part of GlobalUniques
    override fun getUniqueTarget(): UniqueTarget = UniqueTarget.Difficulty

    override fun makeLink() = "Difficulty/$name"

    override fun getSortGroup(ruleset: Ruleset) = ruleset.difficulties.keys.indexOf(name)

    private fun Float.toPercent() = (this * 100).toInt()
    override fun getCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        add("Player settings", header = 3)
        add("{Base happiness}: $baseHappiness ${Fonts.happiness}", indent = 1)
        add("{Extra happiness per luxury}: ${extraHappinessPerLuxury.toInt()} ${Fonts.happiness}", indent = 1)
        add("{Research cost modifier}: ${researchCostModifier.toPercent()}% ${Fonts.science}", indent = 1)
        add("{Unit cost modifier}: ${unitCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        add("{Building cost modifier}: ${buildingCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        add("{Policy cost modifier}: ${policyCostModifier.toPercent()}% ${Fonts.culture}", indent = 1)
        add("{Unhappiness modifier}: ${unhappinessModifier.toPercent()}%", indent = 1)
        add("{Bonus vs. Barbarians}: ${barbarianBonus.toPercent()}% ${Fonts.strength}", indent = 1)
        add("{Barbarian spawning delay}: $barbarianSpawnDelay", indent = 1)

        if (playerBonusStartingUnits.isNotEmpty()) {
            space()
            add("{Bonus starting units}:", indent = 1)
            playerBonusStartingUnits.groupBy { it }.map {
                it.key to it.value.size     // name to Pair.first and count to Pair.second
            }.forEach {
                // Through a virtual Unique was the simplest way to prevent white icons showing for stuff like eraSpecificUnit
                add(Unique(if (it.second == 1) "[${it.first}]" else "${it.second} [${it.first}]"), indent = 2)
            }
        }

        space()
        add("AI settings", header = 3)
        add("{AI difficulty level}: {$aiDifficultyLevel}", indent = 1)
        add("{AI city growth modifier}: ${aiCityGrowthModifier.toPercent()}% ${Fonts.food}", indent = 1)
        add("{AI unit cost modifier}: ${aiUnitCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        add("{AI building cost modifier}: ${aiBuildingCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        add("{AI wonder cost modifier}: ${aiWonderCostModifier.toPercent()}% ${Fonts.production}", indent = 1)
        add("{AI building maintenance modifier}: ${aiBuildingMaintenanceModifier.toPercent()}% ${Fonts.gold}", indent = 1)
        add("{AI unit maintenance modifier}: ${aiUnitMaintenanceModifier.toPercent()}% ${Fonts.gold}", indent = 1)
        add("{AI unhappiness modifier}: ${aiUnhappinessModifier.toPercent()}%", indent = 1)

        if (aiFreeTechs.isNotEmpty()) {
            space()
            add("{AI free techs}:", indent = 1)
            aiFreeTechs.forEach {
                add(it, link = "Technology/$it", indent = 2)
            }
        }
        if (aiMajorCivBonusStartingUnits.isNotEmpty()) {
            space()
            add("{Major AI civilization bonus starting units}:", indent = 1)
            aiMajorCivBonusStartingUnits.groupBy { it }.map {
                it.key to it.value.size
            }.forEach {
                add(Unique(if (it.second == 1) "[${it.first}]" else "${it.second} [${it.first}]"), indent = 2)
            }
        }
        if (aiCityStateBonusStartingUnits.isNotEmpty()) {
            space()
            add("{City state bonus starting units}:", indent = 1)
            aiCityStateBonusStartingUnits.groupBy { it }.map {
                it.key to it.value.size
            }.forEach {
                add(Unique(if (it.second == 1) "[${it.first}]" else "${it.second} [${it.first}]"), indent = 2)
            }
        }

        space()
        add("{Turns until barbarians enter player tiles}: $turnBarbariansCanEnterPlayerTiles ${Fonts.turn}")
        add("{Gold reward for clearing barbarian camps}: $clearBarbarianCampReward ${Fonts.gold}")

        addUniques()
    }

}
