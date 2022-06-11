package com.unciv.models.ruleset.tile

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.ui.civilopedia.FormattedLine

class TileResource : RulesetStatsObject() {

    var resourceType: ResourceType = ResourceType.Bonus
    var terrainsCanBeFoundOn: List<String> = listOf()
    var improvement: String? = null
    var improvementStats: Stats? = null
    var revealedBy: String? = null
    var improvedBy: List<String> = listOf()
    var majorDepositAmount: DepositAmount = DepositAmount()
    var minorDepositAmount: DepositAmount = DepositAmount()

    private val _allImprovements by lazy {
        if (improvement == null) improvedBy
        else improvedBy + improvement!!
    }

    fun getImprovements(): List<String> {
        return _allImprovements
    }

    override fun getUniqueTarget() = UniqueTarget.Resource

    override fun makeLink() = "Resource/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        textList += FormattedLine("${resourceType.name} resource", header = 4, color = resourceType.color)
        textList += FormattedLine()

        textList += FormattedLine(cloneStats().toString())

        if (terrainsCanBeFoundOn.isNotEmpty()) {
            textList += FormattedLine()
            if (terrainsCanBeFoundOn.size == 1) {
                with (terrainsCanBeFoundOn[0]) {
                    textList += FormattedLine("{Can be found on} {$this}", link = "Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Can be found on}:")
                terrainsCanBeFoundOn.forEach {
                    textList += FormattedLine(it, link = "Terrain/$it", indent = 1)
                }
            }
        }

        for (improvement in getImprovements()) {
            textList += FormattedLine()
            textList += FormattedLine("Improved by [$improvement]", link = "Improvement/$improvement")
            if (improvementStats != null && !improvementStats!!.isEmpty())
                textList += FormattedLine("{Bonus stats for improvement}: " + improvementStats.toString())
        }

        val improvementsThatProvideThis = ruleset.tileImprovements.values
            .filter { improvement ->
                improvement.uniqueObjects.any { unique ->
                    unique.type == UniqueType.ProvidesResources && unique.params[1] == name
                }
            }
        if (improvementsThatProvideThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Improvements that provide this resource}:")
            improvementsThatProvideThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsThatProvideThis = ruleset.buildings.values
            .filter { building ->
                building.uniqueObjects.any { unique ->
                    unique.type == UniqueType.ProvidesResources && unique.params[1] == name
                }
            }
        if (buildingsThatProvideThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that provide this resource}:")
            buildingsThatProvideThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirements().containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that consume this resource}:")
            buildingsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirements().containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Units that consume this resource}: ")
            unitsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsRequiringThis =  ruleset.buildings.values.filter {
            it.requiredNearbyImprovedResources?.contains(name) == true
        }
        if (buildingsRequiringThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that require this resource worked near the city}: ")
            buildingsRequiringThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        textList += Belief.getCivilopediaTextMatching(name, ruleset)

        return textList
    }

    fun isImprovedBy(improvementName: String): Boolean {
        return getImprovements().contains(improvementName)
    }

    fun getImprovingImprovement(tile: TileInfo, civInfo: CivilizationInfo): String? {
        return getImprovements().firstOrNull {
            tile.canBuildImprovement(civInfo.gameInfo.ruleSet.tileImprovements[it]!!, civInfo)
        }
    }

    class DepositAmount {
        var sparse: Int = 1
        var default: Int = 2
        var abundant: Int = 3
    }

}
