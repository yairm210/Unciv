package com.unciv.models.ruleset.tile

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.MultiFilter
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.objectdescriptions.uniquesToDescription
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen.Companion.showReligionInCivilopedia
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import kotlin.math.roundToInt

class TileImprovement : RulesetStatsObject() {

    var replaces: String? = null
    var terrainsCanBeBuiltOn: Collection<String> = ArrayList()
    var techRequired: String? = null
    var uniqueTo: String? = null
    override fun getUniqueTarget() = UniqueTarget.Improvement
    val shortcutKey: Char? = null
    // This is the base cost. A cost of 0 means created instead of buildable.
    var turnsToBuild: Int = -1


    fun getTurnsToBuild(civInfo: Civilization, unit: MapUnit): Int {
        val state = StateForConditionals(civInfo, unit = unit)
        val buildSpeedUniques = unit.getMatchingUniques(UniqueType.SpecificImprovementTime, state, checkCivInfoUniques = true)
                .filter { matchesFilter(it.params[1]) }
        return buildSpeedUniques
            .fold(turnsToBuild.toFloat() * civInfo.gameInfo.speed.improvementBuildLengthModifier) { calculatedTurnsToBuild, unique ->
                calculatedTurnsToBuild * unique.params[0].toPercent()
            }.roundToInt()
            .coerceAtLeast(1)
        // In some weird cases it was possible for something to take 0 turns, leading to it instead never finishing
    }

    fun getDescription(ruleset: Ruleset): String {
        val lines = ArrayList<String>()

        val statsDesc = cloneStats().toString()
        if (statsDesc.isNotEmpty()) lines += statsDesc
        if (uniqueTo != null) lines += "Unique to [$uniqueTo]".tr()
        if (replaces != null) lines += "Replaces [$replaces]".tr()
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

        uniquesToDescription(lines)

        return lines.joinToString("\n")
    }

    fun isGreatImprovement() = hasUnique(UniqueType.GreatImprovement)
    fun isRoad() = RoadStatus.values().any { it != RoadStatus.None && it.name == this.name }
    fun isAncientRuinsEquivalent() = hasUnique(UniqueType.IsAncientRuinsEquivalent)

    fun canBeBuiltOn(terrain: String): Boolean {
        return terrain in terrainsCanBeBuiltOn
    }
    fun canBeBuiltOn(terrain: Terrain): Boolean {
        return terrainsCanBeBuiltOn.any{ terrain.matchesFilter(it) }
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
    fun isAllowedOnFeature(terrain: Terrain) = canBeBuiltOn(terrain)
        || getMatchingUniques(UniqueType.NoFeatureRemovalNeeded).any { terrain.matchesFilter(it.params[0]) }

    /** Implements [UniqueParameterType.ImprovementFilter][com.unciv.models.ruleset.unique.UniqueParameterType.ImprovementFilter] */
    fun matchesFilter(filter: String): Boolean {
        return MultiFilter.multiFilter(filter, ::matchesSingleFilter)
    }

    private fun matchesSingleFilter(filter: String): Boolean {
        return when (filter) {
            name -> true
            replaces -> true
            in Constants.all -> true
            "Improvement" -> true // For situations involving tileFilter
            "All Road" -> isRoad()
            "Great Improvement", "Great" -> isGreatImprovement()
            in uniqueMap -> true
            else -> false
        }
    }

    override fun makeLink() = "Improvement/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val statsDesc = cloneStats().toString()
        if (statsDesc.isNotEmpty()) textList += FormattedLine(statsDesc)

        if (uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
        }
        if (replaces != null) {
            val replaceImprovement = ruleset.tileImprovements[replaces]
            textList += FormattedLine("Replaces [$replaces]", link=replaceImprovement?.makeLink() ?: "", indent = 1)
        }

        val constructorUnits = getConstructorUnits(ruleset)
        val creatingUnits = getCreatingUnits(ruleset)
        val creatorExists = constructorUnits.isNotEmpty() || creatingUnits.isNotEmpty()

        if (creatorExists && terrainsCanBeBuiltOn.isNotEmpty()) {
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
        for (resource in ruleset.tileResources.values) {
            if (resource.improvementStats == null || !resource.isImprovedBy(name)) continue
            if (!addedLineBeforeResourceBonus) {
                addedLineBeforeResourceBonus = true
                textList += FormattedLine()
            }
            val statsString = resource.improvementStats.toString()
            // Line intentionally modeled as UniqueType.Stats + ConditionalInTiles
            textList += FormattedLine("[${statsString}] <in [${resource.name}] tiles>", link = resource.makeLink())
        }

        if (techRequired != null) {
            textList += FormattedLine()
            textList += FormattedLine("Required tech: [$techRequired]", link="Technology/$techRequired")
        }

        uniquesToCivilopediaTextLines(textList)

        // Be clearer when one needs to chop down a Forest first... A "Can be built on Plains" is clear enough,
        // but a "Can be built on Land" is not - how is the user to know Forest is _not_ Land?
        if (creatorExists &&
                !isEmpty() && // Has any Stats
                !hasUnique(UniqueType.NoFeatureRemovalNeeded) &&
                !hasUnique(UniqueType.RemovesFeaturesIfBuilt) &&
                terrainsCanBeBuiltOn.none { it in ruleset.terrains }
        )
            textList += FormattedLine("Needs removal of terrain features to be built")

        if (isAncientRuinsEquivalent() && ruleset.ruinRewards.isNotEmpty()) {
            val difficulty = if (!UncivGame.isCurrentInitialized() || UncivGame.Current.gameInfo == null)
                    "Prince"  // most factors == 1
                else UncivGame.Current.gameInfo!!.gameParameters.difficulty
            val religionEnabled = showReligionInCivilopedia(ruleset)
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

        if (creatorExists)
            textList += FormattedLine()
        for (unit in constructorUnits)
            textList += FormattedLine("{Can be constructed by} {$unit}", unit.makeLink())
        for (unit in creatingUnits)
            textList += FormattedLine("{Can be created instantly by} {$unit}", unit.makeLink())

        val seeAlso = ArrayList<FormattedLine>()
        for (alsoImprovement in ruleset.tileImprovements.values) {
            if (alsoImprovement.replaces == name)
                seeAlso += FormattedLine(alsoImprovement.name, link = alsoImprovement.makeLink(), indent = 1)
        }

        seeAlso += Belief.getCivilopediaTextMatching(name, ruleset)

        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

    private fun getConstructorUnits(ruleset: Ruleset): List<BaseUnit> {
        //todo Why does this have to be so complicated? A unit's "Can build [Land] improvements on tiles"
        //     creates the _justified_ expectation that an improvement it can build _will_ have
        //     `matchesFilter("Land")==true` - but that's not the case.
        //     A kludge, but for display purposes the test below is meaningful enough.
        if (hasUnique(UniqueType.Unbuildable)) return emptyList()

        val canOnlyFilters = getMatchingUniques(UniqueType.CanOnlyBeBuiltOnTile)
            .map { it.params[0].run { if (this == "Coastal") "Land" else this } }.toSet()
        val cannotFilters = getMatchingUniques(UniqueType.CannotBuildOnTile).map { it.params[0] }.toSet()
        val resourcesImprovedByThis = ruleset.tileResources.values.filter { it.isImprovedBy(name) }

        val expandedTerrainsCanBeBuiltOn = sequence {
            yieldAll(terrainsCanBeBuiltOn)
            yieldAll(terrainsCanBeBuiltOn.asSequence().mapNotNull { ruleset.terrains[it] }.flatMap { it.occursOn.asSequence() })
            if (hasUnique(UniqueType.CanOnlyImproveResource))
                yieldAll(resourcesImprovedByThis.asSequence().flatMap { it.terrainsCanBeFoundOn })
            if (name.startsWith(Constants.remove)) name.removePrefix(Constants.remove).apply {
                yield(this)
                ruleset.terrains[this]?.occursOn?.let { yieldAll(it) }
                ruleset.tileImprovements[this]?.terrainsCanBeBuiltOn?.let { yieldAll(it) }
            }
        }.filter { it !in cannotFilters }.toMutableSet()

        val terrainsCanBeBuiltOnTypes = sequence {
            yieldAll(expandedTerrainsCanBeBuiltOn.asSequence()
                .mapNotNull { ruleset.terrains[it]?.type })
            yieldAll(TerrainType.values().asSequence()
                .filter { it.name in expandedTerrainsCanBeBuiltOn })
        }.filter { it.name !in cannotFilters }.toMutableSet()

        if (canOnlyFilters.isNotEmpty() && canOnlyFilters.intersect(expandedTerrainsCanBeBuiltOn).isEmpty()) {
            expandedTerrainsCanBeBuiltOn.clear()
            if (terrainsCanBeBuiltOnTypes.none { it.name in canOnlyFilters })
                terrainsCanBeBuiltOnTypes.clear()
        }

        fun matchesBuildImprovementsFilter(filter: String) =
            matchesFilter(filter) ||
            filter in expandedTerrainsCanBeBuiltOn ||
            terrainsCanBeBuiltOnTypes.any { it.name == filter }

        return ruleset.units.values.asSequence()
            .filter { unit ->
                turnsToBuild != -1
                    && unit.getMatchingUniques(UniqueType.BuildImprovements, StateForConditionals.IgnoreConditionals)
                        .any { matchesBuildImprovementsFilter(it.params[0]) }
                || unit.hasUnique(UniqueType.CreateWaterImprovements)
                    && terrainsCanBeBuiltOnTypes.contains(TerrainType.Water)
            }.toList()
    }

    private fun getCreatingUnits(ruleset: Ruleset): List<BaseUnit> {
        return ruleset.units.values.asSequence()
            .filter { unit ->
                unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly, StateForConditionals.IgnoreConditionals)
                    .any { it.params[0] == name }
            }.toList()
    }
}
