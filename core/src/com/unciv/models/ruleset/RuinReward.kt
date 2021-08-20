package com.unciv.models.ruleset

import com.unciv.models.stats.INamed
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText

class RuinReward : INamed, ICivilopediaText {
    override lateinit var name: String  // Displayed in Civilopedia!
    val notification: String = ""
    val uniques: List<String> = listOf()
    @delegate:Transient     // Defense in depth against mad modders
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    val excludedDifficulties: List<String> = listOf()
    val weight: Int = 1
    val color: String = ""  // For Civilopedia

    override var civilopediaText = listOf<FormattedLine>()
    override fun makeLink() = "" //No own category on Civilopedia screen
}
