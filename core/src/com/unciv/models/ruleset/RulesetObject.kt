package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.stats.INamed
import com.unciv.models.stats.NamedStats
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText

interface IRulesetObject: INamed, IHasUniques, ICivilopediaText{
    var originRuleset:String
}

abstract class RulesetObject: IRulesetObject {
    override var name = ""
    override var originRuleset = ""
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    @delegate:Transient
    override val uniqueObjects: List<Unique> by lazy {
        if (uniques.isEmpty()) emptyList()
        else uniques.map { Unique(it, getUniqueTarget(), name) }
    }
    @delegate:Transient
    override val uniqueMap: UniqueMap by lazy {
        if (uniques.isEmpty()) UniqueMap()
        val newUniqueMap = UniqueMap()
        newUniqueMap.addUniques(uniqueObjects)
        newUniqueMap
    }

    override var civilopediaText = listOf<FormattedLine>()
    override fun toString() = name
}

// Same, but inherits from NamedStats - I couldn't find a way to unify the declarations but this is fine
abstract class RulesetStatsObject: NamedStats(), IRulesetObject {
    override var originRuleset = ""
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    @delegate:Transient
    override val uniqueObjects: List<Unique> by lazy {
        if (uniques.isEmpty()) emptyList()
        else uniques.map { Unique(it, getUniqueTarget(), name) }
    }
    @delegate:Transient
    override val uniqueMap: Map<String, List<Unique>> by lazy {
        if (uniques.isEmpty()) emptyMap()
        else uniqueObjects.groupBy { it.placeholderText }
    }

    override var civilopediaText = listOf<FormattedLine>()
}
