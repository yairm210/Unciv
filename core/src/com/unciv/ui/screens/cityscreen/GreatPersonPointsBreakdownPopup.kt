package com.unciv.ui.screens.cityscreen

import com.unciv.logic.city.GreatPersonPointsBreakdown
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

class GreatPersonPointsBreakdownPopup(cityScreen: CityScreen, gppBreakdown: GreatPersonPointsBreakdown, greatPerson: String?) : Popup(cityScreen) {
    init {
        val lines = ArrayList<FormattedLine>()
        val headerText = "«GOLD»{${greatPerson ?: "Great person points"}}«» ({${cityScreen.city.name}})"
        lines += FormattedLine(headerText, header = 2, centered = true)
        lines += FormattedLine(separator = true)

        fun addFormattedEntry(entry: GreatPersonPointsBreakdown.Entry, isPercentage: Boolean) {
            val text = if (greatPerson == null) {
                entry.toString(isPercentage)
            } else {
                val amount = entry.counter[greatPerson]
                if (amount == 0) return
                entry.toString(isPercentage, greatPerson, amount)
            }
            lines += FormattedLine(text, entry.pediaLink ?: "")
        }

        for (entry in gppBreakdown.basePoints)
            addFormattedEntry(entry, false)

        for (entry in gppBreakdown.percentBonuses)
            addFormattedEntry(entry, true)

        val game = cityScreen.game
        val ruleset = game.gameInfo!!.ruleset
        add(MarkupRenderer.render(lines) {
            game.pushScreen(CivilopediaScreen(ruleset, link = it))
        })

        addCloseButton()
        open(true)
    }

    private fun GreatPersonPointsBreakdown.Entry.toString(isPercentage: Boolean) =
        "{$source}: " +
        when {
            isAllGP -> (counter.values.firstOrNull() ?: 0).toStringSigned() + (if (isPercentage) "%" else "")
            isPercentage -> counter.entries.joinToString { it.value.toStringSigned() + "% {${it.key}}" }
            else -> counter.entries.joinToString { it.value.toStringSigned() + " {${it.key}}" }
        }

    private fun GreatPersonPointsBreakdown.Entry.toString(isPercentage: Boolean, greatPerson: String, amount: Int) =
        "{$source}: " +
            amount.toStringSigned() +
            (if (isPercentage) "%" else "") +
            (if (isAllGP || !isPercentage) "" else " {$greatPerson}")
}
