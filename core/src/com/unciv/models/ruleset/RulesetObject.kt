package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.stats.INamed
import com.unciv.models.stats.NamedStats
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText

abstract class RulesetObject: INamed, IHasUniques, ICivilopediaText {
    override lateinit var name: String
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    @delegate:Transient
    override val uniqueObjects: List<Unique> by lazy {
        uniques.map { Unique(it, getUniqueTarget(), name) }
    }
    override var civilopediaText = listOf<FormattedLine>()
    override fun toString() = name
}

// Same, but inherits from NamedStats - I couldn't find a way to unify the declarations but this is fine
abstract class RulesetStatsObject: NamedStats(), IHasUniques, ICivilopediaText {
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    @delegate:Transient
    override val uniqueObjects: List<Unique> by lazy {
        uniques.map { Unique(it, getUniqueTarget(), name) }
    }
    override var civilopediaText = listOf<FormattedLine>()
}