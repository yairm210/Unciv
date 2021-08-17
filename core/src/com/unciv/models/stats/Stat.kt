package com.unciv.models.stats

import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.UncivSound
import com.unciv.ui.utils.Fonts

enum class Stat(
    val notificationIcon: String,
    val purchaseSound: UncivSound,
    val character: Char
) {
    Production(NotificationIcon.Production, UncivSound.Click, Fonts.production),
    Food(NotificationIcon.Food, UncivSound.Click, Fonts.food),
    Gold(NotificationIcon.Gold, UncivSound.Coin, Fonts.gold),
    Science(NotificationIcon.Science, UncivSound.Chimes, Fonts.science),
    Culture(NotificationIcon.Culture, UncivSound.Paper, Fonts.culture),
    Happiness(NotificationIcon.Happiness, UncivSound.Click, Fonts.happiness),
    Faith(NotificationIcon.Faith, UncivSound.Choir, Fonts.faith);
}
