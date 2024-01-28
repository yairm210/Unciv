package com.unciv.models.ruleset

import com.unciv.models.ModConstants
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget

object ModOptionsConstants {
    const val diplomaticRelationshipsCannotChange = "Diplomatic relationships cannot change"
    const val convertGoldToScience = "Can convert gold to science with sliders"
    const val allowCityStatesSpawnUnits = "Allow City States to spawn with additional units"
    const val tradeCivIntroductions = "Can trade civilization introductions for [] Gold"
    const val disableReligion = "Disable religion"
    const val allowRazeCapital = "Allow raze capital"
    const val allowRazeHolyCity = "Allow raze holy city"
}

class ModOptions : IHasUniques {
    //region Modder choices
    var isBaseRuleset = false
    var techsToRemove = HashSet<String>()
    var buildingsToRemove = HashSet<String>()
    var unitsToRemove = HashSet<String>()
    var nationsToRemove = HashSet<String>()
    val constants = ModConstants()
    //endregion

    //region Metadata, automatic
    var modUrl = ""
    var defaultBranch = "master"
    var author = ""
    var lastUpdated = ""
    var modSize = 0
    var topics = mutableListOf<String>()
    //endregion

    //region IHasUniques
    override var name = "ModOptions"
    override var uniques = ArrayList<String>()

    @delegate:Transient
    override val uniqueObjects: List<Unique> by lazy (::uniqueObjectsProvider)
    @delegate:Transient
    override val uniqueMap: UniqueMap by lazy(::uniqueMapProvider)

    override fun getUniqueTarget() = UniqueTarget.ModOptions
    //endregion
}
