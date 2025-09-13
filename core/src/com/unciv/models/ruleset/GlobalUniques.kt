package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Readonly

class GlobalUniques: RulesetObject() {
    override var name = "Global Uniques"
    @Readonly override fun makeLink() = "Tutorial/Global Uniques"

    var unitUniques: ArrayList<String> = ArrayList()
    override fun getUniqueTarget() = UniqueTarget.GlobalUniques

    /** @return Whether or not there are global uniques that should be displayed to the user. */
    @Readonly fun hasUniques(): Boolean =
        uniqueObjects.any { !it.isHiddenToUsers() } || unitUniques.isNotEmpty()

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lines = mutableListOf<FormattedLine>()
        lines.add(FormattedLine("Global uniques are ruleset-wide modifiers that apply to all civilizations."))
        val visibleUniques = uniqueObjects.filter { !it.isHiddenToUsers() }
        if (visibleUniques.isNotEmpty()) {
            lines.add(FormattedLine(""))
            lines.add(FormattedLine("Global Effect", header=4))
            for (unique in visibleUniques) {
                lines.add(FormattedLine(unique))
            }
        }

        val visibleUnitUniques = unitUniques.map { Unique(it) }.filter { !it.isHiddenToUsers() }
        if (visibleUnitUniques.isNotEmpty()) {
            lines.add(FormattedLine(""))
            lines.add(FormattedLine("Units", header=4))
            for (unique in visibleUnitUniques) {
                lines.add(FormattedLine(unique))
            }
        }
        return lines
    }

    companion object {
        @Readonly
        fun getUniqueSourceDescription(unique: Unique): String {
            if (unique.modifiers.isEmpty())
                return "Global Effect"

            return when (unique.modifiers.first().type) {
                UniqueType.ConditionalGoldenAge -> "Golden Age"
                UniqueType.ConditionalHappy -> "Happiness"
                UniqueType.ConditionalWLTKD -> "We Love The King Day"
                else -> "Global Effect"
            }
        }

        fun combine(globalUniques: GlobalUniques, vararg otherSources: IHasUniques) = GlobalUniques().apply {
            /** This must happen before [uniqueMap] and [uniqueObjects] are triggered */
            uniques.addAll(globalUniques.uniques)
            unitUniques = globalUniques.unitUniques
            for (source in otherSources) {
                uniques.addAll(source.uniques)
            }
        }
    }
}
