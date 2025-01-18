package com.unciv.ui.objectdescriptions

import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.FormattedLine

object ImprovementDescriptions {
    /**
     * Lists differences: how a nation-unique Improvement compares to its replacement.
     *
     * Result as indented, non-linking [FormattedLine]s
     *
     * @param originalImprovement The "standard" Improvement
     * @param replacementImprovement The "uniqueTo" Improvement
     */
    fun getDifferences(
        ruleset: Ruleset, originalImprovement: TileImprovement, replacementImprovement: TileImprovement
    ): Sequence<FormattedLine> = sequence {
        for (stat in Stat.entries) // Do not iterate on object since that excludes zero values
            if (replacementImprovement[stat] != originalImprovement[stat])
                yield(FormattedLine( stat.name.tr() + " " +"[${replacementImprovement[stat].toInt()}] vs [${originalImprovement[stat].toInt()}]".tr(), indent=1))

        for (terrain in replacementImprovement.terrainsCanBeBuiltOn)
            if (terrain !in originalImprovement.terrainsCanBeBuiltOn)
                yield(FormattedLine("Can be built on [${terrain}]", link = ruleset.terrains[terrain]?.makeLink() ?: "", indent = 1))
        for (terrain in originalImprovement.terrainsCanBeBuiltOn)
            if (terrain !in replacementImprovement.terrainsCanBeBuiltOn)
                yield(FormattedLine("Cannot be built on [${terrain}]", link = ruleset.terrains[terrain]?.makeLink() ?: "", indent = 1))

        if (replacementImprovement.turnsToBuild != originalImprovement.turnsToBuild)
            yield(FormattedLine("{Turns to build} ".tr() + "[${replacementImprovement.turnsToBuild}] vs [${originalImprovement.turnsToBuild}]".tr(), indent=1))

        val newAbilityPredicate: (Unique)->Boolean = { it.text in originalImprovement.uniques || it.isHiddenToUsers() }
        for (unique in replacementImprovement.uniqueObjects.filterNot(newAbilityPredicate))
            yield(FormattedLine(unique.getDisplayText(), indent=1))  // FormattedLine(unique) would look worse - no indent and auto-linking could distract

        val lostAbilityPredicate: (Unique)->Boolean = { it.text in replacementImprovement.uniques || it.isHiddenToUsers() }
        for (unique in originalImprovement.uniqueObjects.filterNot(lostAbilityPredicate)) {
            // Need double translation of the "ability" here - unique texts may contain square brackets
            yield(FormattedLine("Lost ability (vs [${originalImprovement.name}]): [${unique.getDisplayText().tr()}]", indent=1))
        }
    }

    fun getCivilopediaTextLines(improvement: TileImprovement, ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val statsDesc = improvement.cloneStats().toString()
        if (statsDesc.isNotEmpty()) textList += FormattedLine(statsDesc)

        if (improvement.uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [${improvement.uniqueTo}]", link="Nation/${improvement.uniqueTo}")
        }
        if (improvement.replaces != null) {
            val replaceImprovement = ruleset.tileImprovements[improvement.replaces]
            textList += FormattedLine("Replaces [${improvement.replaces}]", link=replaceImprovement?.makeLink() ?: "", indent = 1)
        }

        val constructorUnits = improvement.getConstructorUnits(ruleset)
        val creatingUnits = improvement.getCreatingUnits(ruleset)
        val creatorExists = constructorUnits.isNotEmpty() || creatingUnits.isNotEmpty()

        if (creatorExists && improvement.terrainsCanBeBuiltOn.isNotEmpty()) {
            textList += FormattedLine()
            if (improvement.terrainsCanBeBuiltOn.size == 1) {
                with (improvement.terrainsCanBeBuiltOn.first()) {
                    textList += FormattedLine("{Can be built on} {$this}", link="Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Can be built on}:")
                improvement.terrainsCanBeBuiltOn.forEach {
                    textList += FormattedLine(it, link="Terrain/$it", indent=1)
                }
            }
        }

        var addedLineBeforeResourceBonus = false
        for (resource in ruleset.tileResources.values) {
            if (resource.improvementStats == null || !resource.isImprovedBy(improvement.name)) continue
            if (!addedLineBeforeResourceBonus) {
                addedLineBeforeResourceBonus = true
                textList += FormattedLine()
            }
            val statsString = resource.improvementStats.toString()
            // Line intentionally modeled as UniqueType.Stats + ConditionalInTiles
            textList += FormattedLine("[${statsString}] <in [${resource.name}] tiles>", link = resource.makeLink())
        }

        if (improvement.techRequired != null) {
            textList += FormattedLine()
            textList += FormattedLine("Required tech: [${improvement.techRequired}]", link="Technology/${improvement.techRequired}")
        }

        improvement.uniquesToCivilopediaTextLines(textList)

        // Be clearer when one needs to chop down a Forest first... A "Can be built on Plains" is clear enough,
        // but a "Can be built on Land" is not - how is the user to know Forest is _not_ Land?
        if (creatorExists &&
            !improvement.isEmpty() && // Has any Stats
            !improvement.hasUnique(UniqueType.NoFeatureRemovalNeeded) &&
            !improvement.hasUnique(UniqueType.RemovesFeaturesIfBuilt) &&
            improvement.terrainsCanBeBuiltOn.none { it in ruleset.terrains }
        )
            textList += FormattedLine("Needs removal of terrain features to be built")

        if (improvement.isAncientRuinsEquivalent() && ruleset.ruinRewards.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("The possible rewards are:")
            ruleset.ruinRewards.values.asSequence()
                .filterNot { it.isHiddenFromCivilopedia(ruleset) }
                .forEach { reward ->
                    textList += FormattedLine(reward.name, starred = true, color = reward.color)
                    textList += reward.civilopediaText
                }
        }

        if (creatorExists)
            textList += FormattedLine()
        for (unit in constructorUnits)
            textList += FormattedLine("{Can be constructed by} {$unit}", unit.makeLink())
        for (unit in creatingUnits)
            textList += FormattedLine("{Can be created instantly by} {$unit}", unit.makeLink())

        val seeAlso = ArrayList<FormattedLine>()
        for (alsoImprovement in ruleset.tileImprovements.values) {
            if (alsoImprovement.replaces == improvement.name)
                seeAlso += FormattedLine(alsoImprovement.name, link = alsoImprovement.makeLink(), indent = 1)
        }

        seeAlso += Belief.getCivilopediaTextMatching(improvement.name, ruleset, false)

        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

    fun getDescription(improvement: TileImprovement, ruleset: Ruleset): String {
        val lines = ArrayList<String>()

        val statsDesc = improvement.cloneStats().toString()
        if (statsDesc.isNotEmpty()) lines += statsDesc
        if (improvement.uniqueTo != null) lines += "Unique to [${improvement.uniqueTo}]".tr()
        if (improvement.replaces != null) lines += "Replaces [${improvement.replaces}]".tr()
        if (!improvement.terrainsCanBeBuiltOn.isEmpty()) {
            val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
            for (i in improvement.terrainsCanBeBuiltOn) {
                terrainsCanBeBuiltOnString.add(i.tr())
            }
            lines += "Can be built on".tr() + terrainsCanBeBuiltOnString.joinToString(", ", " ") //language can be changed when setting changes.
        }
        for (resource: TileResource in ruleset.tileResources.values.filter { it.isImprovedBy(improvement.name) }) {
            if (resource.improvementStats == null) continue
            val statsString = resource.improvementStats.toString()
            lines += "[${statsString}] <in [${resource.name}] tiles>".tr()
        }
        if (improvement.techRequired != null) lines += "Required tech: [${improvement.techRequired}]".tr()

        improvement.uniquesToDescription(lines)

        return lines.joinToString("\n")
    }

    fun getShortDescription(improvement: TileImprovement) = sequence {
        if (improvement.terrainsCanBeBuiltOn.isNotEmpty()) {
            improvement.terrainsCanBeBuiltOn.withIndex().forEach {
                yield(
                    FormattedLine(
                        if (it.index == 0) "{Can be built on} {${it.value}}" else "or [${it.value}]",
                        link = "Terrain/${it.value}", indent = if (it.index == 0) 1 else 2
                    )
                )
            }
        }
        for (unique in improvement.uniqueObjects) {
            if (unique.isHiddenToUsers()) continue
            yield(FormattedLine(unique, indent = 1))
        }
    }
}
