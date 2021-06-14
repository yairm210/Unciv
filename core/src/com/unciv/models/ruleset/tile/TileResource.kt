package com.unciv.models.ruleset.tile

import com.unciv.Constants
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import java.util.*

/**
 * Class representing a Resource.
 * 
 * This is deserialized from json, therefore is has a default constructor
 */
class TileResource : NamedStats() {

    var resourceType: ResourceType = ResourceType.Bonus
    var terrainsCanBeFoundOn: List<String> = listOf()
    var improvement: String? = null
    var improvementStats: Stats? = null
    var revealedBy: String? = null

    @Deprecated("As of 3.15.x", ReplaceWith("uniques"))
    var unique: String? = null
    var uniques = ArrayList<String>()
    val uniqueObjects:List<Unique> by lazy {
        // Compatibility code for deprecated [unique]
        if (unique != null) {
            if (unique!!.isNotEmpty()) uniques.add(unique!!)
            unique = null
        }
        uniques.map { Unique(it) } 
    }

    /** Can map generator/editor add this resource to a given [tile]?
     * @param tile The [TileInfo] to check
     * @return `true` = resource is allowed there, `false` = don't place there
     */
    fun isAllowedOnTile(tile: TileInfo): Boolean {
        // uniques / terrainsCanBeFoundOn: Cannot unique should have highest precedence,
        // while terrainsCanBeFoundOn and Can unique should be treated like a set union

        getMatchingUniques("Cannot occur on [] tiles").forEach { 
            if (tile.matchesUniqueFilter(it.params[0])) return false
        }
        getMatchingUniques("Can occur on [] tiles").forEach {
            if (tile.matchesUniqueFilter(it.params[0])) return true
        }

        // From old code...
        if (tile.baseTerrain == Constants.snow && tile.isHill()) return false  // todo: this should not be hardcoded
        // Original logic:
        return isAllowedOnTerrain(tile.getLastTerrain())
    }

    /** Should [TileInfo.normalizeToRuleset] remove this resource from a given [tile]?
     * @param tile The tile to check
     * @return `true` if normalization should remove the resource 
     */
    fun mustRemoveFromTile(tile: TileInfo): Boolean {
        if (isAllowedOnTerrain(tile.baseTerrain)) return false
        return tile.terrainFeatures.none { isAllowedOnTerrain(it) }
    }

    /** Is this resource allowed on a given [terrain]? */
    private fun isAllowedOnTerrain(terrain: Terrain): Boolean {
        return isAllowedOnTerrain(terrain.name)
    }

    /** Is this resource allowed on a [terrain] given as String? */
    fun isAllowedOnTerrain(terrain: String): Boolean {
        // todo: Called from Terrain.getDescription - how to support tileFilter=terrain uniques?
        return terrain in terrainsCanBeFoundOn
    }

    /** Get a terrain the map editor can use as background
     * @param ruleset The ruleset to search for viable terrains
     * @return Terrain name or null if impossible due to [ruleset] inconsistency 
     */
    fun getSampleTerrain(ruleset: Ruleset): String? {
        return terrainsCanBeFoundOn.firstOrNull { it in ruleset.terrains }
    }

    /** Check if this resource has a certain [unique]
     * @param placeholderText The unique key to match with [Unique.placeholderText]
     * @return True if this resource has at least one matching unique
     */
    fun hasUnique(placeholderText: String) = getMatchingUniques(placeholderText).any()

    /** Get a sequence of uniques matching a placeholder string
     * @param placeholderText The unique key to match with [Unique.placeholderText]
     * @return Sequence<Unique> matching the given placeholderText
     */
    fun getMatchingUniques(placeholderText: String): Sequence<Unique> =
        uniqueObjects.asSequence().filter { it.placeholderText == placeholderText }

    /** Get wonder production bonus - cached
     * @return Percentage bonus as Float 
     */
    fun getWonderProductionBonus(): Float = _wonderProductionBonus
    private val _wonderProductionBonus: Float by lazy {
        if (hasUnique("+15% production towards Wonder construction")) 15f
        else getMatchingUniques("[]% production towards Wonder construction")
            .map { it.params[0].toFloatOrNull() }.filterNotNull().maxOfOrNull { it } ?: 0f
    }

    /** Get a description for use by the Civilopedia
     * @param ruleset The ruleset to check for references to this resource
     * @return One multiline String describing the properties of this resource
     */
    // Needs work, but that is already part of the Civilopedia WIP
    fun getDescription(ruleset: Ruleset): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine(resourceType.name.tr())
        stringBuilder.appendLine(this.clone().toString())
        val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
        terrainsCanBeBuiltOnString.addAll(terrainsCanBeFoundOn.map { it.tr() })
        stringBuilder.appendLine("Can be found on ".tr() + terrainsCanBeBuiltOnString.joinToString(", "))
        stringBuilder.appendLine()
        stringBuilder.appendLine("Improved by [$improvement]".tr())
        stringBuilder.appendLine("{Bonus stats for improvement}: ".tr() + "$improvementStats".tr())

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirements().containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty())
            stringBuilder.appendLine("{Buildings that consume this resource}: ".tr()
                    + buildingsThatConsumeThis.joinToString { it.name.tr() })

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirements().containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty())
            stringBuilder.appendLine("{Units that consume this resource}: ".tr()
                    + unitsThatConsumeThis.joinToString { it.name.tr() })

        for(unique in uniques)
            stringBuilder.appendLine(Translations.translateBonusOrPenalty(unique))

        return stringBuilder.toString()
    }

    /**
     * Test mod consistency: Are all foreign keys used in this resource present in a given [ruleset]?
     * @param ruleset RuleSet to test against
     * @param lines Array to add error messages to
     * @return Count of non-fatal lines (used to determine if the end result can be just a 'warning')
     */
    fun checkModLinks(ruleset: Ruleset, lines: ArrayList<String>): Int {
        if (revealedBy != null && !ruleset.technologies.containsKey(revealedBy!!))
            lines += "$name revealed by tech $revealedBy which does not exist!"
        if (improvement != null && !ruleset.tileImprovements.containsKey(improvement!!))
            lines += "$name improved by improvement $improvement which does not exist!"
        for (terrain in terrainsCanBeFoundOn)
            if (!ruleset.terrains.containsKey(terrain))
                lines += "$name can be found on terrain $terrain which does not exist!"
        return 0
    }
}

data class ResourceSupply(val resource:TileResource,var amount:Int, val origin:String)

class ResourceSupplyList:ArrayList<ResourceSupply>() {
    fun add(resource: TileResource, amount: Int, origin: String) {
        val existingResourceSupply = firstOrNull { it.resource == resource && it.origin == origin }
        if (existingResourceSupply != null) {
            existingResourceSupply.amount += amount
            if (existingResourceSupply.amount == 0) remove(existingResourceSupply)
        } else add(ResourceSupply(resource, amount, origin))
    }

    fun add(resourceSupplyList: ResourceSupplyList) {
        for (resourceSupply in resourceSupplyList)
            add(resourceSupply.resource, resourceSupply.amount, resourceSupply.origin)
    }
}