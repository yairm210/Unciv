package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.stats.INamed
import com.unciv.models.stats.NamedStats
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText

interface IRulesetObject: INamed, IHasUniques, ICivilopediaText

abstract class RulesetObject: IRulesetObject {
    override var name = ""
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    @Transient
    override var uniqueObjectsInternal: List<Unique>? = null
    @Transient
    override var uniqueMapInternal: UniqueMap? = null

    override var civilopediaText = listOf<FormattedLine>()
    override fun toString() = name
}

// Same, but inherits from NamedStats - I couldn't find a way to unify the declarations but this is fine
abstract class RulesetStatsObject: NamedStats(), IRulesetObject {
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    @Transient
    override var uniqueObjectsInternal: List<Unique>? = null
    @Transient
    override var uniqueMapInternal: UniqueMap? = null

    override var civilopediaText = listOf<FormattedLine>()
}
