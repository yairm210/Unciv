package com.unciv.models.ruleset

import com.unciv.UncivGame
import com.unciv.models.stats.INamed
import com.unciv.ui.civilopedia.CivilopediaText
import com.unciv.ui.civilopedia.FormattedLine
import java.util.ArrayList

class Belief: INamed, CivilopediaText() {
    override var name: String = ""
    var type: BeliefType = BeliefType.None
    var uniques = ArrayList<String>()
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }


    override fun makeLink() = "Belief/$name"
    override fun replacesCivilopediaDescription() = true
    override fun hasCivilopediaTextLines() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()
        textList += FormattedLine("{Type}: $type", color=type.color )
        uniqueObjects.forEach { 
            textList += FormattedLine(it)
        }
        return textList
    }
    
    companion object {
        // private but potentially reusable, therefore not folded into getCivilopediaTextMatching
        private fun getBeliefsMatching(name: String, ruleset: Ruleset): Sequence<Belief> {
            if (!UncivGame.isCurrentInitialized()) return sequenceOf()
            if (!UncivGame.Current.isGameInfoInitialized()) return sequenceOf()
            if (!UncivGame.Current.gameInfo.hasReligionEnabled()) return sequenceOf()
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
            yieldAll(matchingBeliefs.map { FormattedLine(it.name, link=it.makeLink(), indent = 1) })
        }
    }
}

enum class BeliefType(val color: String) {
    None(""),
    Pantheon("#44c6cc"),
    Follower("#ccaa44"),
    Founder("#c00000")
}
