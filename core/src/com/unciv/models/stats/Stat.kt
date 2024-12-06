package com.unciv.models.stats

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.UncivSound
import com.unciv.ui.components.extensions.colorFromHex
import com.unciv.ui.components.fonts.Fonts

enum class Stat(
    val notificationIcon: String,
    val purchaseSound: UncivSound,
    val character: Char,
    val color: Color
) {
    Production(NotificationIcon.Production, UncivSound.Click, Fonts.production, colorFromHex(0xc14d00)),
    Food(NotificationIcon.Food, UncivSound.Click, Fonts.food, colorFromHex(0x24A348)),
    Gold(NotificationIcon.Gold, UncivSound.Coin, Fonts.gold, colorFromHex(0xffeb7f)),
    Science(NotificationIcon.Science, UncivSound.Chimes, Fonts.science, colorFromHex(0x8c9dff)),
    Culture(NotificationIcon.Culture, UncivSound.Paper, Fonts.culture, colorFromHex(0x8b60ff)),
    Happiness(NotificationIcon.Happiness, UncivSound.Click, Fonts.happiness, colorFromHex(0xffd800)),
    GoldenAge(NotificationIcon.Happiness, UncivSound.Click, Fonts.happiness, colorFromHex(0xffd800)),
    Faith(NotificationIcon.Faith, UncivSound.Choir, Fonts.faith, colorFromHex(0xcbdfff)),
    ;

    companion object {
        val statsUsableToBuy = setOf(Gold, Food, Science, Culture, Faith, GoldenAge)
        private val valuesAsMap = entries.associateBy { it.name }
        fun safeValueOf(name: String) = valuesAsMap[name]
        fun isStat(name: String) = name in valuesAsMap
        fun names() = valuesAsMap.keys
        val statsWithCivWideField = setOf(Gold, Science, Culture, Faith, GoldenAge)
    }
}
