package com.unciv.models.ruleset

import com.unciv.models.ModConstants
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget

class ModOptions : IHasUniques {
    //region Modder choices
    var isBaseRuleset = false

    var beliefsToRemove = HashSet<String>()
    var buildingsToRemove = HashSet<String>()
    var cityStateTypesToRemove = HashSet<String>()
    var difficultiesToRemove = HashSet<String>()
    var eventsToRemove = HashSet<String>()
    var improvementsToRemove = HashSet<String>()
    var nationsToRemove = HashSet<String>()
    var personalitiesToRemove = HashSet<String>()
    var policyBranchesToRemove = HashSet<String>()
    var policiesToRemove = HashSet<String>()
    var questsToRemove = HashSet<String>()
    var religionsToRemove = HashSet<String>()
    var resourcesToRemove = HashSet<String>()
    var ruinRewardsToRemove = HashSet<String>()
    var specialistsToRemove = HashSet<String>()
    var speedsToRemove = HashSet<String>()
    var techsToRemove = HashSet<String>()
    var terrainsToRemove = HashSet<String>()
    var tutorialsToRemove = HashSet<String>()
    var unitsToRemove = HashSet<String>()
    var unitNameGroupsToRemove = HashSet<String>()
    var unitPromotionsToRemove = HashSet<String>()
    var unitTypesToRemove = HashSet<String>()
    var victoriesToRemove = HashSet<String>()

    val constants = ModConstants()
    var unitset: String? = null
    var tileset: String? = null
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
