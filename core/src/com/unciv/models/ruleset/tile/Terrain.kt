package com.unciv.models.ruleset.tile

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.NamedStats
import com.unciv.models.translations.tr
import com.unciv.ui.utils.colorFromRGB

class Terrain : NamedStats() {

    lateinit var type: TerrainType

    var overrideStats = false

    /** If true, other terrain layers can come over this one. For mountains, lakes etc. this is false  */
    var canHaveOverlay = true

    /** If true, nothing can be built here - not even resource improvements */
    var unbuildable = false

    /** For terrain features */
    val occursOn: Collection<String>? = null

    /** Used by Natural Wonders: it is the baseTerrain on top of which the Natural Wonder is placed */
    val turnsInto: String? = null

    /** Uniques (currently used only for Natural Wonders) */
    val uniques = ArrayList<String>()

    /** Natural Wonder weight: probability to be picked */
    var weight = 10

    /** RGB color of base terrain  */
    var RGB: List<Int>? = null
    var movementCost = 1
    var defenceBonus:Float = 0f
    var impassable = false
    var rough = false


    fun getColor(): Color { // Can't be a lazy initialize, because we play around with the resulting color with lerp()s and the like
        if (RGB == null) return Color.GOLD
        return colorFromRGB(RGB!![0], RGB!![1], RGB!![2])
    }


    fun getDescription(ruleset: Ruleset): String {
        val sb = StringBuilder()
        sb.appendln(this.clone().toString())
        if (occursOn != null)
            sb.appendln("Occurs on [${occursOn.joinToString(", ") { it.tr() }}]".tr())

        if (turnsInto != null)
            sb.appendln("Placed on [$turnsInto]".tr())

        val resourcesFound = ruleset.tileResources.values.filter { it.terrainsCanBeFoundOn.contains(name) }
        if (resourcesFound.isNotEmpty())
            sb.appendln("May contain [${resourcesFound.joinToString(", ") { it.name.tr() }}]".tr())

        if(uniques.isNotEmpty())
            sb.appendln(uniques.joinToString { it.tr() })

        if (impassable)
            sb.appendln(Constants.impassable.tr())
        else
            sb.appendln("{Movement cost}: $movementCost".tr())

        if (defenceBonus != 0f)
            sb.appendln("{Defence bonus}: ".tr() + (defenceBonus * 100).toInt() + "%")

        if (rough)
            sb.appendln("Rough Terrain".tr())

        return sb.toString()
    }
}