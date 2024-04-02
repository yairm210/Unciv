package com.unciv.ui.objectdescriptions

import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.civilopediascreen.FormattedLine

object ImprovementDescriptions {
    /**
     * Lists differences: how a nation-unique Building compares to its replacement.
     *
     * Cost is **is** included.
     * Result as indented, non-linking [FormattedLine]s
     *
     * @param originalImprovement The "standard" Improvement
     * @param replacementImprovement The "uniqueTo" Improvement
     */
    fun getDifferences(
        ruleset: Ruleset, originalImprovement: TileImprovement, replacementImprovement: TileImprovement
    ): Sequence<FormattedLine> = sequence {
        for ((key, value) in replacementImprovement)
            if (value != originalImprovement[key])
                yield(FormattedLine( key.name.tr() + " " +"[${value.toInt()}] vs [${originalImprovement[key].toInt()}]".tr(), indent=1))

        for (terrain in replacementImprovement.terrainsCanBeBuiltOn)
            if (terrain !in originalImprovement.terrainsCanBeBuiltOn)
                yield(FormattedLine("Is allowed on [${terrain}]", link = ruleset.terrains[terrain]?.makeLink() ?: "", indent = 1))
        for (terrain in originalImprovement.terrainsCanBeBuiltOn)
            if (terrain !in replacementImprovement.terrainsCanBeBuiltOn)
                yield(FormattedLine("Is not allowed on [${terrain}]", link = ruleset.terrains[terrain]?.makeLink() ?: "", indent = 1))

        if (replacementImprovement.turnsToBuild != originalImprovement.turnsToBuild)
            yield(FormattedLine("{Turns to build} ".tr() + "[${replacementImprovement.turnsToBuild}] vs [${originalImprovement.turnsToBuild}]".tr(), indent=1))

        val newAbilityPredicate: (Unique)->Boolean = { it.text in originalImprovement.uniques || it.isHiddenToUsers() }
        for (unique in replacementImprovement.uniqueObjects.filterNot(newAbilityPredicate))
            yield(FormattedLine(unique.text, indent=1))  // FormattedLine(unique) would look worse - no indent and autolinking could distract

        val lostAbilityPredicate: (Unique)->Boolean = { it.text in replacementImprovement.uniques || it.isHiddenToUsers() }
        for (unique in originalImprovement.uniqueObjects.filterNot(lostAbilityPredicate)) {
            // Need double translation of the "ability" here - unique texts may contain square brackets
            yield(FormattedLine("Lost ability (vs [${originalImprovement.name}]): [${unique.text.tr()}]", indent=1))
        }
    }
}
