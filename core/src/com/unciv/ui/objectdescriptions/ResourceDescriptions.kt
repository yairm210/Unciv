package com.unciv.ui.objectdescriptions

import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.ui.screens.civilopediascreen.FormattedLine

object ResourceDescriptions {
    fun getCivilopediaTextLines(resource: TileResource, ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        textList += FormattedLine("${resource.resourceType.name} resource", header = 4, color = resource.resourceType.color)
        textList += FormattedLine()

        resource.uniquesToCivilopediaTextLines(textList)

        if (!(resource as Stats).isEmpty())
            textList += FormattedLine((resource as Stats).toString())

        if (!resource.revealedBy.isNullOrEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("Revealed by:")
            textList += FormattedLine(resource.revealedBy!!, link = "Technology/${resource.revealedBy}", indent = 1)
        }

        if (resource.terrainsCanBeFoundOn.isNotEmpty()) {
            textList += FormattedLine()
            if (resource.terrainsCanBeFoundOn.size == 1) {
                val terrainName = resource.terrainsCanBeFoundOn[0]
                textList += FormattedLine("{Can be found on} {$terrainName}", link = "Terrain/$terrainName")
            } else {
                textList += FormattedLine("{Can be found on}:")
                resource.terrainsCanBeFoundOn.forEach {
                    textList += FormattedLine(it, link = "Terrain/$it", indent = 1)
                }
            }
        }

        for (improvement in resource.getImprovements()) {
            textList += FormattedLine()
            textList += FormattedLine("Improved by [$improvement]", link = "Improvement/$improvement")
            if (resource.improvementStats != null && !resource.improvementStats!!.isEmpty())
                textList += FormattedLine("{Bonus stats for improvement}: " + resource.improvementStats.toString())
        }

        val improvementsThatProvideThis = ruleset.tileImprovements.values
            .filter { improvement ->
                improvement.uniqueObjects.any { unique ->
                    unique.type == UniqueType.ProvidesResources && unique.params[1] == resource.name
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
                    unique.type == UniqueType.ProvidesResources && unique.params[1] == resource.name
                }
            }
        if (buildingsThatProvideThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that provide this resource}:")
            buildingsThatProvideThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirementsPerTurn(
            GameContext.IgnoreConditionals).containsKey(resource.name) }
        if (buildingsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that consume this resource}:")
            buildingsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirementsPerTurn(
            GameContext.IgnoreConditionals).containsKey(resource.name) }
        if (unitsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Units that consume this resource}: ")
            unitsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsRequiringThis =  ruleset.buildings.values.filter {
            it.requiredNearbyImprovedResources?.contains(resource.name) == true
        }
        if (buildingsRequiringThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that require this resource improved near the city}: ")
            buildingsRequiringThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        textList += Belief.getCivilopediaTextMatching(resource.name, ruleset)

        return textList
    }
}
