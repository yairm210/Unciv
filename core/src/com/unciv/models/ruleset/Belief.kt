package com.unciv.models.ruleset

import com.unciv.models.stats.INamed
import com.unciv.ui.civilopedia.CivilopediaText
import com.unciv.ui.civilopedia.FormattedLine
import java.awt.Color
import java.util.ArrayList

class Belief: INamed, CivilopediaText() {
    override var name: String = ""
    var type: BeliefType = BeliefType.None
    var uniques = ArrayList<String>()
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }

    override fun getCivilopediaTextHeader() = FormattedLine(name, icon="Belief/$name", header=2)
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
}

enum class BeliefType(val color: String) {
    None(""),
    Pantheon("#44c6cc"),
    Follower("#ccaa44"),
    Founder("#c00000")
}