package com.unciv.models.ruleset

import com.unciv.UncivGame
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import kotlin.collections.ArrayList

class Belief : RulesetObject() {
    var type: BeliefType = BeliefType.None

    override fun getUniqueTarget() =
        if (type == BeliefType.Founder || type == BeliefType.Enhancer)  UniqueTarget.FounderBelief
        else UniqueTarget.FollowerBelief

    override fun makeLink() = "Belief/$name"
    override fun getCivilopediaTextHeader() = FormattedLine(name, icon = makeLink(), header = 2, color = if (type == BeliefType.None) "#e34a2b" else "")
    override fun getSortGroup(ruleset: Ruleset) = type.ordinal
    override fun getIconName() = if (type == BeliefType.None) "Religion" else type.name

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        return getCivilopediaTextLines(ruleset, false)
    }

    fun getCivilopediaTextLines(ruleset: Ruleset, centerType: Boolean): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()
        if (type != BeliefType.None)
            textList += FormattedLine("{Type}: $type", color = type.color, centered = centerType)
        uniqueObjects.forEach {
            if (!it.hasFlag(UniqueFlag.HideInCivilopedia))
                textList += FormattedLine(it)
        }
        return textList
    }

    companion object {
        // private but potentially reusable, therefore not folded into getCivilopediaTextMatching
        private fun getBeliefsMatching(name: String, ruleset: Ruleset): Sequence<Belief> {
            if (!UncivGame.isCurrentInitialized()) return sequenceOf()
            if (!UncivGame.Current.isGameInfoInitialized()) return sequenceOf()
            if (!UncivGame.Current.gameInfo.isReligionEnabled()) return sequenceOf()
            return ruleset.beliefs.asSequence().map { it.value }
                .filter { belief -> belief.uniqueObjects.any { unique -> unique.params.any { it == name } }
            }
        }

        /** Get CivilopediaText lines for all Beliefs referencing a given name in an unique parameter,
         *  With optional spacing and "See Also:" header.
         */
        fun getCivilopediaTextMatching(
            name: String,
            ruleset: Ruleset,
            withSeeAlso: Boolean = true
        ): Sequence<FormattedLine> = sequence {
            val matchingBeliefs = getBeliefsMatching(name, ruleset)
            if (matchingBeliefs.none()) return@sequence
            if (withSeeAlso) { yield(FormattedLine()); yield(FormattedLine("{See also}:")) }
            yieldAll(matchingBeliefs.map { FormattedLine(it.name, link = it.makeLink(), indent = 1) })
        }

        fun getCivilopediaReligionEntry(ruleset: Ruleset) = Belief().apply {
            name = "Religions"
            val lines = ArrayList<FormattedLine>()
            lines += FormattedLine(separator = true)
            ruleset.religions.sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale(), { it.tr() })).forEach {
                lines += FormattedLine(it, icon = "Belief/$it")
            }
            civilopediaText = lines
        }
    }
}

enum class BeliefType(val color: String) {
    None(""),
    Pantheon("#44c6cc"),
    Founder("#c00000"),
    Follower("#ccaa44"),
    Enhancer("#72cc45"),
    Any(""),
}
