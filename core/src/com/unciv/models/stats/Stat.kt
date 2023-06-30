package com.unciv.models.stats

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.NotificationIcons
import com.unciv.models.UncivSound
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.colorFromHex

enum class Stat(
    val notificationIcon: String,
    val purchaseSound: UncivSound,
    val character: Char,
    val color:Color
) {
    Production(NotificationIcons.Production, UncivSound.Click, Fonts.production, colorFromHex(0xc14d00)),
    Food(NotificationIcons.Food, UncivSound.Click, Fonts.food, colorFromHex(0x24A348)),
    Gold(NotificationIcons.Gold, UncivSound.Coin, Fonts.gold, colorFromHex(0xffeb7f)),
    Science(NotificationIcons.Science, UncivSound.Chimes, Fonts.science, colorFromHex(0x8c9dff)),
    Culture(NotificationIcons.Culture, UncivSound.Paper, Fonts.culture, colorFromHex(0x8b60ff)),
    Happiness(NotificationIcons.Happiness, UncivSound.Click, Fonts.happiness, colorFromHex(0xffd800)),
    Faith(NotificationIcons.Faith, UncivSound.Choir, Fonts.faith, colorFromHex(0xcbdfff));

    companion object {
        val statsUsableToBuy = setOf(Gold, Food, Science, Culture, Faith)
        private val valuesAsMap = values().associateBy { it.name }
        fun safeValueOf(name: String) = valuesAsMap[name]
        fun isStat(name: String) = name in valuesAsMap
        fun names() = valuesAsMap.keys
        val statsWithCivWideField = setOf(Gold, Science, Culture, Faith)
    }
}
