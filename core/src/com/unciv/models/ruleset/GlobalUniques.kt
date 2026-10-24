package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder.Companion.buildCivilopediaText
import yairm210.purity.annotations.Readonly

class GlobalUniques: RulesetObject() {
    override var name = "Global Uniques"
    @Readonly override fun makeLink() = "Tutorial/Global Uniques"

    var unitUniques: ArrayList<String> = ArrayList()
    override fun getUniqueTarget() = UniqueTarget.GlobalUniques

    /** @return Whether or not there are global uniques that should be displayed to the user. */
    @Readonly fun hasUniques(): Boolean =
        uniqueObjects.any { !it.isHiddenToUsers() } || unitUniques.isNotEmpty()

    override fun getCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        addUniques(extraSeparator = { add("Global Effect", header = 4) })
        addUniques(unitUniques.asSequence().map { Unique(it) }, extraSeparator = { add("Units", header = 4) })
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
            /** We're not copying [name] which means any assignments in actual jsons differing from the default will be lost, but still be picked up by TFW */
            civilopediaText = buildCivilopediaText {
                for (source in sequenceOf(globalUniques) + otherSources) {
                    uniques.addAll(source.uniques)
                    if (source !is GlobalUniques) continue
                    unitUniques.addAll(source.unitUniques)
                    add(source.civilopediaText)
                }
            }
        }
    }
}
