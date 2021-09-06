package com.unciv.models.ruleset.tile

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.IHasUniques
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.NamedStats
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import com.unciv.ui.utils.colorFromRGB

class Terrain : NamedStats(), ICivilopediaText, IHasUniques {

    lateinit var type: TerrainType

    var overrideStats = false

    /** If true, nothing can be built here - not even resource improvements */
    var unbuildable = false

    /** For terrain features */
    val occursOn = ArrayList<String>()

    /** Used by Natural Wonders: it is the baseTerrain on top of which the Natural Wonder is placed */
    val turnsInto: String? = null

    /** Uniques (Properties such as Temp/humidity, Fresh water, elevation, rough, defense, Natural Wonder specials) */
    override var uniques = ArrayList<String>()
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }

    /** Natural Wonder weight: probability to be picked */
    var weight = 10

    /** RGB color of base terrain  */
    @Suppress("PropertyName")   // RGB is expected to be in caps
    var RGB: List<Int>? = null
    var movementCost = 1
    var defenceBonus:Float = 0f
    var impassable = false

    override var civilopediaText = listOf<FormattedLine>()

    fun isRough(): Boolean = uniques.contains("Rough terrain")
    
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

    fun getColor(): Color { // Can't be a lazy initialize, because we play around with the resulting color with lerp()s and the like
        if (RGB == null) return Color.GOLD
        return colorFromRGB(RGB!!)
    }

    override fun makeLink() = "Terrain/$name"
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        //todo where should we explain Rivers?

        val textList = ArrayList<FormattedLine>()

        if (turnsInto != null) {
            textList += FormattedLine("Natural Wonder", header=3, color="#3A0")
        }

        val stats = this.clone()
        if (!stats.isEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("$stats")
        }

        if (occursOn.isNotEmpty()) {
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
        uniqueObjects.forEach {
            textList += FormattedLine(it)
        }

        textList += FormattedLine()
        textList += if (impassable) FormattedLine(Constants.impassable, color="#A00")
                    else FormattedLine("{Movement cost}: $movementCost")

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
}
