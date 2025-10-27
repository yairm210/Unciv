package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.translations.tr
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Readonly

class Belief() : RulesetObject() {
    var type: BeliefType = BeliefType.None

    constructor(type: BeliefType) : this() {
        this.type = type
    }

    override fun getUniqueTarget() =
        if (type.isFounder)  UniqueTarget.FounderBelief
        else UniqueTarget.FollowerBelief

    override fun makeLink() = "Belief/$name"
    override fun getCivilopediaTextHeader() = FormattedLine(name, icon = makeLink(), header = 2, color = if (type == BeliefType.None) "#e34a2b" else "")
    override fun getSortGroup(ruleset: Ruleset) = type.ordinal

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        return getCivilopediaTextLines(false)
    }

    // This special overload is called from Religion overview and Religion picker
    fun getCivilopediaTextLines(withHeader: Boolean): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()
        if (withHeader) {
            textList += FormattedLine(name, size = Constants.headingFontSize, centered = true, link = makeLink())
            textList += FormattedLine()
        }
        if (type != BeliefType.None)
            textList += FormattedLine("{Type}: {$type}", color = type.color, centered = withHeader)
        uniquesToCivilopediaTextLines(textList, leadingSeparator = null)
        return textList
    }

    companion object {
        // private but potentially reusable, therefore not folded into getCivilopediaTextMatching
        @Readonly
        private fun getBeliefsMatching(name: String, ruleset: Ruleset) =
            ruleset.beliefs.values.asSequence()
            .filterNot { it.isHiddenFromCivilopedia(ruleset) }
            .filter { belief -> belief.uniqueObjects.any { unique -> unique.params.any { it == name } } }

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
            ruleset.religions.sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.tr(hideIcons = true) }).forEach {
                lines += FormattedLine(it, icon = "Belief/$it")
            }
            civilopediaText = lines
        }
    }
}

/** Subtypes of Beliefs - directly deserialized.
 *  @param isFollower - Behaves as "follower" belief, Uniques processed per city
 *  @param isFounder - Behaves as "founder" belief, Uniques processed globally for founding civ only
 * */
enum class BeliefType(val color: String, val isFollower: Boolean = false, val isFounder: Boolean = false) {
    None(""),
    Pantheon("#44c6cc", isFollower = true),
    Founder("#c00000", isFounder = true),
    Follower("#ccaa44", isFollower = true),
    Enhancer("#72cc45", isFounder = true),
    Any(""),
}
