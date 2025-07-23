package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

class GlobalUniques: RulesetObject() {
    override var name = "GlobalUniques"

    var unitUniques: ArrayList<String> = ArrayList()
    override fun makeLink() = "" // No own category on Civilopedia screen
    override fun getUniqueTarget() = UniqueTarget.GlobalUniques

    companion object {
        @Readonly
        fun getUniqueSourceDescription(unique: Unique): String {
            if (unique.modifiers.isEmpty())
                return "Global Effect"

            return when (unique.modifiers.first().type) {
                UniqueType.ConditionalGoldenAge -> "Golden Age"
                UniqueType.ConditionalHappy -> "Happiness"
                UniqueType.ConditionalBetweenHappiness, UniqueType.ConditionalBelowHappiness -> "Unhappiness"
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
