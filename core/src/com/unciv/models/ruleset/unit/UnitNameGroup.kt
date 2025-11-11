package com.unciv.models.ruleset.unit

import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Readonly
import java.net.URLEncoder

class UnitNameGroup : RulesetObject() {
    /** A list of names available for this historical figure group. */
    var unitNames = ArrayList<String>()

    fun clone(): UnitNameGroup {
        val newUnitNameGroup = UnitNameGroup()

        // RulesetObject fields
        newUnitNameGroup.name = name
        newUnitNameGroup.uniques = uniques

        // UnitNameGroup fields
        newUnitNameGroup.unitNames = unitNames
        return newUnitNameGroup
    }

    /**
     * Retrieve a list of units that match this unit group name instance.
     */
    @Readonly
    fun getUnits(ruleset: Ruleset, gameContext: GameContext = GameContext.IgnoreConditionals) =
        ruleset.units.values.filter { unit ->
            unit.getMatchingUniques(UniqueType.OneTimeUnitGetsName).any { unique ->
                // Match by using either the direct name, or a tag
                unique.params[1] == name || hasTagUnique(unique.params[1], gameContext)
            }
        }

    override fun getUniqueTarget() = UniqueTarget.UnitTriggerable
    override fun makeLink() = "Unit Names/$name"
    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lines = ArrayList<FormattedLine>()
        uniquesToCivilopediaTextLines(lines)

        // Units
        val units = getUnits(ruleset)
        if (units.isNotEmpty()) {
            lines.add(FormattedLine("Units", header = 4))
            for (unit in units) {
                lines.add(FormattedLine(unit.name, link = unit.makeLink()))
            }
        }

        // Names
        if (unitNames.isNotEmpty()) {
            lines.add(FormattedLine())
            lines.add(FormattedLine("Unit Names", header = 4))
            val (collator, language) = UncivGame.Current.settings.run { getCollatorFromLocale() to locale!!.language }
            fun wikilink(name: String) = "https://$language.wikipedia.org/w/index.php?search=${URLEncoder.encode(name.tr(), Charsets.UTF_8.name())}"
            for (name in unitNames.sortedWith(collator)) {
                lines.add(FormattedLine(name, link = wikilink(name)))
            }
        }

        return lines
    }
}
