package com.unciv.ui.components.extensions

import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.fonts.Fonts
import com.unciv.view.CityConstructionsView

/** @param constructionName needs to be a non-perpetual construction, else an empty string is returned */
fun CityConstructionsView.getTurnsToConstructionString(constructionName: String, useStoredProduction: Boolean = true): String =
    getTurnsToConstructionString(getConstruction(constructionName), useStoredProduction)

/** @param construction needs to be a non-perpetual construction, else an empty string is returned */
fun CityConstructionsView.getTurnsToConstructionString(construction: IConstruction, useStoredProduction: Boolean = true): String {
    if (construction !is INonPerpetualConstruction) return ""   // shouldn't happen
    val city = getCityConstructions().city
    val cost = construction.getProductionCost(city.civ, city)
    val turns = turnsToConstruction(construction.name, useStoredProduction)
    val currentProgress = if (useStoredProduction) getWorkDone(construction.name) else 0
    val lines = ArrayList<String>()
    val buildable = !construction.getMatchingUniques(UniqueType.Unbuildable)
        .any { it.conditionalsApply(city.state) }
    if (buildable)
        lines += (if (currentProgress == 0) "" else "$currentProgress/") +
                "$cost${Fonts.production} $turns${Fonts.turn}"
    val otherStats = Stat.entries.filter {
        (it != Stat.Gold || !buildable) &&  // Don't show rush cost for consistency
            construction.canBePurchasedWithStat(city, it)
    }.joinToString(" / ") { "${construction.getStatBuyCost(city, it)}${it.character}" }
    if (otherStats.isNotEmpty()) lines += otherStats
    return lines.joinToString("\n", "\n")
}
