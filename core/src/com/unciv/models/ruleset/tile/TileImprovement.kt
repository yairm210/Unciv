package com.unciv.models.ruleset.tile

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.toPercent
import com.unciv.ui.worldscreen.unit.UnitActions
import java.util.*
import kotlin.math.roundToInt

class TileImprovement : RulesetStatsObject() {

    var terrainsCanBeBuiltOn: Collection<String> = ArrayList()
    var techRequired: String? = null
    var uniqueTo:String? = null
    override fun getUniqueTarget() = UniqueTarget.Improvement
    val shortcutKey: Char? = null
    // This is the base cost. A cost of 0 means created instead of buildable.
    val turnsToBuild: Int = 0 


    fun getTurnsToBuild(civInfo: CivilizationInfo, unit: MapUnit): Int {
        val state = StateForConditionals(civInfo, unit = unit)
        return unit.getMatchingUniques(UniqueType.TileImprovementTime, state, checkCivInfoUniques = true)
            .fold(turnsToBuild.toFloat() * civInfo.gameInfo.getGameSpeed().modifier) { it, unique ->
                it * unique.params[0].toPercent()
            }.roundToInt()
            .coerceAtLeast(1)
        // In some weird cases it was possible for something to take 0 turns, leading to it instead never finishing
    }

    fun getDescription(ruleset: Ruleset): String {
        val lines = ArrayList<String>()

        val statsDesc = cloneStats().toString()
        if (statsDesc.isNotEmpty()) lines += statsDesc
        if (!terrainsCanBeBuiltOn.isEmpty()) {
            val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
            for (i in terrainsCanBeBuiltOn) {
                terrainsCanBeBuiltOnString.add(i.tr())
            }
            lines += "Can be built on".tr() + terrainsCanBeBuiltOnString.joinToString(", ", " ") //language can be changed when setting changes.
        }
        for (resource: TileResource in ruleset.tileResources.values.filter { it.isImprovedBy(name) }) {
            if (resource.improvementStats == null) continue
            val statsString = resource.improvementStats.toString()
            lines += "[${statsString}] <in [${resource.name}] tiles>".tr()
        }
        if (techRequired != null) lines += "Required tech: [$techRequired]".tr()

        for (unique in uniques)
            lines += unique.tr()

        return lines.joinToString("\n")
    }

    fun isGreatImprovement() = hasUnique(UniqueType.GreatImprovement)
    fun isRoad() = RoadStatus.values().any { it != RoadStatus.None && it.name == this.name }
    fun isAncientRuinsEquivalent() = hasUnique(UniqueType.IsAncientRuinsEquivalent)

    fun canBeBuiltOn(terrain: String): Boolean {
        return terrain in terrainsCanBeBuiltOn
    }
    
    fun handleImprovementCompletion(builder: MapUnit) {
        val tile = builder.getTile()
        if (hasUnique(UniqueType.TakesOverAdjacentTiles))
            UnitActions.takeOverTilesAround(builder)
        if (tile.resource != null) {
            val city = builder.getTile().getCity()
            if (city != null) {
                city.cityStats.update()
                city.civInfo.updateDetailedCivResources()
            }
        }
        if (hasUnique(UniqueType.RemovesFeaturesIfBuilt)) {
            // Remove terrainFeatures that a Worker can remove
            // and that aren't explicitly allowed under the improvement
            val removableTerrainFeatures = tile.terrainFeatures.filter { feature ->
                val removingAction = "${Constants.remove}$feature"
                
                removingAction in tile.ruleset.tileImprovements
                && !isAllowedOnFeature(feature)
                && tile.ruleset.tileImprovements[removingAction]!!.let {
                    it.techRequired == null || builder.civInfo.tech.isResearched(it.techRequired!!)
                }
            }
            
            tile.setTerrainFeatures(tile.terrainFeatures.filterNot { it in removableTerrainFeatures })
        }
    }
    
    /**
     * Check: Is this improvement allowed on a [given][name] terrain feature?
     *
     * Background: This not used for e.g. a lumbermill - it derives the right to be placed on forest
     * from [terrainsCanBeBuiltOn]. Other improvements may be candidates without fulfilling the
     * [terrainsCanBeBuiltOn] check - e.g. they are listed by a resource as 'their' improvement.
     * I such cases, the 'unbuildable' property of the Terrain feature might prevent the improvement,
     * so this check is done in conjunction - for the user, success means he does not need to remove
     * a terrain feature, thus the unique name.
     */
    fun isAllowedOnFeature(name: String) = terrainsCanBeBuiltOn.contains(name) || getMatchingUniques(UniqueType.NoFeatureRemovalNeeded).any { it.params[0] == name }

    /** Implements [UniqueParameterType.ImprovementFilter][com.unciv.models.ruleset.unique.UniqueParameterType.ImprovementFilter] */
    fun matchesFilter(filter: String): Boolean {
        return when (filter) {
            name -> true
            "All" -> true
            "All Road" -> isRoad()
            "Great Improvement", "Great" -> isGreatImprovement()
            in uniques -> true
            else -> false
        }
    }

    override fun makeLink() = "Improvement/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val statsDesc = cloneStats().toString()
        if (statsDesc.isNotEmpty()) textList += FormattedLine(statsDesc)

        if (uniqueTo!=null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
        }

        if (terrainsCanBeBuiltOn.isNotEmpty()) {
            textList += FormattedLine()
            if (terrainsCanBeBuiltOn.size == 1) {
                with (terrainsCanBeBuiltOn.first()) {
                    textList += FormattedLine("{Can be built on} {$this}", link="Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Can be built on}:")
                terrainsCanBeBuiltOn.forEach {
                    textList += FormattedLine(it, link="Terrain/$it", indent=1)
                }
            }
        }

        var addedLineBeforeResourceBonus = false
        for (resource in ruleset.tileResources.values.filter { it.isImprovedBy(name) }) {
            if (resource.improvementStats == null) continue
            if (!addedLineBeforeResourceBonus) {
                addedLineBeforeResourceBonus = true
                textList += FormattedLine()
            }
            val statsString = resource.improvementStats.toString()

            textList += FormattedLine("[${statsString}] <in [${resource.name}] tiles>", link = "Resource/${resource.name}")
        }

        if (techRequired != null) {
            textList += FormattedLine()
            textList += FormattedLine("Required tech: [$techRequired]", link="Technology/$techRequired")
        }

        if (uniques.isNotEmpty()) {
            textList += FormattedLine()
            for (unique in uniqueObjects)
                textList += FormattedLine(unique)
        }

        if (isAncientRuinsEquivalent() && ruleset.ruinRewards.isNotEmpty()) {
            val difficulty: String
            val religionEnabled: Boolean
            if (UncivGame.isCurrentInitialized() && UncivGame.Current.isGameInfoInitialized()) {
                difficulty = UncivGame.Current.gameInfo.gameParameters.difficulty
                religionEnabled = UncivGame.Current.gameInfo.isReligionEnabled()
            } else {
                difficulty = "Prince"  // most factors == 1
                religionEnabled = true
            }
            textList += FormattedLine()
            textList += FormattedLine("The possible rewards are:")
            ruleset.ruinRewards.values.asSequence()
                .filter { reward ->
                    difficulty !in reward.excludedDifficulties &&
                    (religionEnabled || !reward.hasUnique(UniqueType.HiddenWithoutReligion))
                }
                .forEach { reward ->
                    textList += FormattedLine(reward.name, starred = true, color = reward.color)
                    textList += reward.civilopediaText
                }
        }

        val unit = ruleset.units.asSequence().firstOrNull {
            entry -> entry.value.uniques.any { 
                it.startsWith("Can construct [$name]")
            }
        }?.key
        if (unit != null) {
            textList += FormattedLine()
            textList += FormattedLine("{Can be constructed by} {$unit}", link="Unit/$unit")
        }

        textList += Belief.getCivilopediaTextMatching(name, ruleset)

        return textList
    }
}
