package com.unciv.ui.screens.cityscreen

import com.unciv.logic.city.GreatPersonPointsBreakdown
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

class GreatPersonPointsBreakdownPopup(cityScreen: CityScreen, gppBreakdown: GreatPersonPointsBreakdown, greatPerson: String?) : Popup(cityScreen) {
    init {
        val lines = ArrayList<FormattedLine>(gppBreakdown.size + 2)
        val headerText = "«GOLD»{${greatPerson ?: "Great person points"}}«» ({${cityScreen.city.name}})"
        lines += FormattedLine(headerText, header = 2, centered = true)
        lines += FormattedLine(separator = true)

        for (entry in gppBreakdown) {
            val text = if (greatPerson == null) {
                entry.toString()
            } else {
                val amount = entry.counter[greatPerson]
                if (amount == 0) continue
                "{${entry.source}}: " +
                    amount.toStringSigned() + (if (entry.isPercentage) "%" else "") +
                    (if (entry.isAllGPP || !entry.isPercentage) "" else " {$greatPerson}")
            }
            lines += FormattedLine(text, entry.pediaLink ?: "")
        }

        val game = cityScreen.game
        val ruleset = game.gameInfo!!.ruleset
        //val wrapWidth = cityScreen.stage.width * (if (cityScreen.isPortrait()) 0.8f else 0.6f)
        add(MarkupRenderer.render(lines) {
            game.pushScreen(CivilopediaScreen(ruleset, link = it))
        })

        addCloseButton()
        open(true)
    }
}
