package com.unciv.models.ruleset.tile

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

class Terrain : RulesetStatsObject() {

    lateinit var type: TerrainType

    /** For terrain features - indicates the stats of this terrain override those of all previous layers */
    var overrideStats = false

    /** If true, nothing can be built here - not even resource improvements */
    var unbuildable = false

    /** For terrain features */
    val occursOn = ArrayList<String>()

    /** Used by Natural Wonders: it is the baseTerrain on top of which the Natural Wonder is placed
     *  Omitting it means the Natural Wonder is placed on whatever baseTerrain the Tile already had (limited by occursOn)
     */
    var turnsInto: String? = null

    override fun getUniqueTarget() = UniqueTarget.Terrain

    /** Natural Wonder weight: probability to be picked */
    var weight = 10

    /** RGB color of base terrain  */
    @Suppress("PropertyName")   // RGB is expected to be in caps
    var RGB: List<Int>? = null
    var movementCost = 1
    var defenceBonus: Float = 0f
    var impassable = false

    @Transient
    var damagePerTurn = 0

    // Shouldn't this just be a lazy property so it's automatically cached?
    @Readonly
    fun isRough(): Boolean = hasUnique(UniqueType.RoughTerrain)

    /** Tests base terrains, features and natural wonders whether they should be treated as Land/Water.
     *  Currently only used for civilopedia display, as other code can test the tile itself.
     */
    fun displayAs(asType: TerrainType, ruleset: Ruleset) =
        type == asType
        || occursOn.any {
            occursName -> occursName in ruleset.terrains.values
                .filter { it.type == asType }
                .map { it.name }
        }
        || ruleset.terrains[this.turnsInto]?.type == asType

    /** Gets a new [Color] instance from the [RGB] property, mutation e.g. via [Color.lerp] allowed */
    fun getColor(): Color { // Can't be a lazy initialize, see above
        if (RGB == null) return Color.GOLD.cpy()
        return colorFromRGB(RGB!!)
    }

    override fun makeLink() = "Terrain/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        //todo where should we explain Rivers?

        val textList = ArrayList<FormattedLine>()

        if (type == TerrainType.NaturalWonder) {
            textList += FormattedLine("Natural Wonder", header=3, color="#3A0")
        }

        val stats = cloneStats()
        if (!stats.isEmpty() || overrideStats) {
            textList += FormattedLine()
            textList += FormattedLine(if (stats.isEmpty()) "No yields" else "$stats")
            if (overrideStats)
                textList += FormattedLine("Overrides yields from underlying terrain")
        }

        if (occursOn.isNotEmpty() && !hasUnique(UniqueType.NoNaturalGeneration)) {
            textList += FormattedLine()
            if (occursOn.size == 1) {
                with (occursOn[0]) {
                    textList += FormattedLine("Occurs on [$this]", link="Terrain/$this")
                }
            } else {
                textList += FormattedLine("Occurs on:")
                occursOn.forEach {
                    textList += FormattedLine(it, link="Terrain/$it", indent=1)
                }
            }
        }

        val improvementsThatCanBePlacedHere = ruleset.tileImprovements.values
            .filter { it.terrainsCanBeBuiltOn.contains(name) }
        if (improvementsThatCanBePlacedHere.isNotEmpty()){
            textList += FormattedLine("{Tile Improvements}:")
            for (improvement in improvementsThatCanBePlacedHere){
                textList += FormattedLine(improvement.name, improvement.makeLink(), indent=1)
            }
        }

        if (turnsInto != null) {
            textList += FormattedLine("Placed on [$turnsInto]", link="Terrain/$turnsInto")
        }

        val resourcesFound = ruleset.tileResources.values.filter { it.terrainsCanBeFoundOn.contains(name) }
        if (resourcesFound.isNotEmpty()) {
            textList += FormattedLine()
            if (resourcesFound.size == 1) {
                with (resourcesFound[0]) {
                    textList += FormattedLine("May contain [$this]", link="Resource/$this")
                }
            } else {
                textList += FormattedLine("May contain:")
                resourcesFound.forEach {
                    textList += FormattedLine("$it", link="Resource/$it", indent=1)
                }
            }
        }

        textList += FormattedLine()
        // For now, natural wonders show no "open terrain" - may change later
        if (turnsInto == null && displayAs(TerrainType.Land, ruleset) && !isRough())
            textList += FormattedLine("Open terrain")   // Rough is in uniques
        uniquesToCivilopediaTextLines(textList, leadingSeparator = null)

        textList += FormattedLine()
        if (impassable) textList += FormattedLine(Constants.impassable, color="#A00")
        else if (movementCost > 0) textList += FormattedLine("{Movement cost}: $movementCost")

        if (defenceBonus != 0f)
            textList += FormattedLine("{Defence bonus}: ${(defenceBonus * 100).toInt()}%")

        val seeAlso = (ruleset.buildings.values.asSequence() + ruleset.units.values.asSequence())
            .filter {
                construction -> construction.uniqueObjects.any {
                    unique -> unique.params.any { it == name }
                }
            }.map { FormattedLine(it.name, it.makeLink(), indent=1) } +
            Belief.getCivilopediaTextMatching(name, ruleset, false)
        if (seeAlso.any()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

    /** Terrain filter matching is "pure" - input always returns same output, and it's called a bajillion times */
    @Cache private val cachedMatchesFilterResult = HashMap<String, Boolean>()

    @Readonly
    fun matchesFilter(filter: String, state: GameContext? = null, multiFilter: Boolean = true): Boolean {
        return if (multiFilter) MultiFilter.multiFilter(filter, {
            cachedMatchesFilterResult.getOrPut(it) { matchesSingleFilter(it) } ||
                state != null && hasTagUnique(it, state) ||
                state == null && hasTagUnique(it)
        })
        else cachedMatchesFilterResult.getOrPut(filter) { matchesSingleFilter(filter) } ||
            state != null && hasTagUnique(filter, state) ||
            state == null && hasTagUnique(filter)
    }

    /** Implements [UniqueParameterType.TerrainFilter][com.unciv.models.ruleset.unique.UniqueParameterType.TerrainFilter] */
    @Readonly
    fun matchesSingleFilter(filter: String): Boolean {
        return when (filter) {
            "all", "All" -> true
            "Terrain" -> true
            "Open terrain" -> !isRough()
            "Rough terrain" -> isRough()
            "Natural Wonder" -> type == TerrainType.NaturalWonder
            "Terrain Feature" -> type == TerrainType.TerrainFeature
            else -> when(filter){ // non-constants
                name -> true
                type.name -> true
                else -> false
            }
        }
    }

    fun setTransients() {
        damagePerTurn = getMatchingUniques(UniqueType.DamagesContainingUnits).sumOf { it.params[0].toInt() }
    }
}
