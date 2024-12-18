package com.unciv.models.ruleset.tile

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.objectdescriptions.ImprovementDescriptions
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

    override fun legacyRequiredTechs() = if (techRequired == null) emptySequence() else sequenceOf(techRequired!!)

    fun getTurnsToBuild(civInfo: Civilization, unit: MapUnit): Int {
        val state = StateForConditionals(civInfo, unit = unit)
        
        val buildSpeedUniques = unit.getMatchingUniques(UniqueType.SpecificImprovementTime, state, checkCivInfoUniques = true)
            .filter { matchesFilter(it.params[1], state) }
        val buildSpeedIncreases = unit.getMatchingUniques(UniqueType.ImprovementTimeIncrease, state, checkCivInfoUniques = true)
            .filter { matchesFilter(it.params[0], state) }
        val increase = buildSpeedIncreases.sumOf { it.params[1].toDouble() }.toFloat().toPercent()
        val buildTime =  (civInfo.gameInfo.speed.improvementBuildLengthModifier * turnsToBuild / increase)

        return buildSpeedUniques.fold(buildTime) { calculatedTurnsToBuild, unique ->
                calculatedTurnsToBuild * unique.params[0].toPercent()
            }.roundToInt()
            .coerceAtLeast(1)
        // In some weird cases it was possible for something to take 0 turns, leading to it instead never finishing
    }

    fun getDescription(ruleset: Ruleset): String  = ImprovementDescriptions.getDescription(this, ruleset)
    fun getShortDecription() = ImprovementDescriptions.getShortDescription(this)

    fun isGreatImprovement() = hasUnique(UniqueType.GreatImprovement)
    fun isRoad() = RoadStatus.entries.any { it != RoadStatus.None && it.name == this.name }
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
    fun matchesFilter(filter: String, tileState: StateForConditionals? = null, multiFilter: Boolean = true): Boolean {
        return if (multiFilter) MultiFilter.multiFilter(filter, {
            matchesSingleFilter(it) ||
                tileState != null && hasUnique(it, tileState) ||
                tileState == null && hasTagUnique(it)
        })
        else matchesSingleFilter(filter) ||
            tileState != null && hasUnique(filter, tileState) ||
            tileState == null && hasTagUnique(filter)
    }

    private fun matchesSingleFilter(filter: String): Boolean {
        return when (filter) {
            "all", "All" -> true
            "Improvement" -> true // For situations involving tileFilter
            "All Road" -> isRoad()
            "Great Improvement", "Great" -> isGreatImprovement()
            else -> filter == name || filter == replaces // 2 string equalities is better than hashmap lookup
        }
    }

    override fun makeLink() = "Improvement/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> =
        ImprovementDescriptions.getCivilopediaTextLines(this, ruleset)

    fun getConstructorUnits(ruleset: Ruleset): List<BaseUnit> {
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
            yieldAll(
                TerrainType.entries.asSequence()
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

    fun getCreatingUnits(ruleset: Ruleset): List<BaseUnit> {
        return ruleset.units.values.asSequence()
            .filter { unit ->
                unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly, StateForConditionals.IgnoreConditionals)
                    .any { it.params[0] == name }
            }.toList()
    }
}
