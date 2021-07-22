package com.unciv.models.ruleset.tile

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.NamedStats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import com.unciv.ui.utils.colorFromRGB

class Terrain : NamedStats(), ICivilopediaText {

    lateinit var type: TerrainType

    var overrideStats = false

    /** If true, nothing can be built here - not even resource improvements */
    var unbuildable = false

    /** For terrain features */
    val occursOn = ArrayList<String>()

    /** Used by Natural Wonders: it is the baseTerrain on top of which the Natural Wonder is placed */
    val turnsInto: String? = null

    /** Uniques (Properties such as Temp/humidity, Fresh water, elevation, rough, defense, Natural Wonder specials) */
    val uniques = ArrayList<String>()
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }

    /** Natural Wonder weight: probability to be picked */
    var weight = 10

    /** RGB color of base terrain  */
    var RGB: List<Int>? = null
    var movementCost = 1
    var defenceBonus:Float = 0f
    var impassable = false

    override var civilopediaText = listOf<FormattedLine>()

    fun isRough(): Boolean = uniques.contains("Rough terrain")

    fun getColor(): Color { // Can't be a lazy initialize, because we play around with the resulting color with lerp()s and the like
        if (RGB == null) return Color.GOLD
        return colorFromRGB(RGB!!)
    }

    override fun getCivilopediaTextHeader() = FormattedLine(name, icon="Terrain/$name", header=2)
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
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
                    textList += FormattedLine("{Occurs on} {$this}", link="Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Occurs on}:")
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
                    textList += FormattedLine("{May contain} {$this}", link="Resource/$this")
                }
            } else {
                textList += FormattedLine("{May contain}:")
                resourcesFound.forEach {
                    textList += FormattedLine("$it", link="Resource/$it", indent=1)
                }
            }
        }

        textList += FormattedLine()
        textList += FormattedLine(if (isRough()) "Rough terrain" else "Open terrain")

        if (uniques.isNotEmpty()) {
            textList += FormattedLine()
            uniqueObjects.forEach {
                textList += FormattedLine(it)
            }
        }

        textList += FormattedLine()
        textList += if (impassable) FormattedLine(Constants.impassable, color="#A00")
                    else FormattedLine("{Movement cost}: $movementCost")

        if (defenceBonus != 0f)
            textList += FormattedLine("{Defence bonus}: ${(defenceBonus * 100).toInt()}%")

        return textList
    }
}
