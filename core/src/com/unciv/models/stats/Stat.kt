package com.unciv.models.stats

import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.UncivSound
import com.unciv.ui.utils.Fonts

enum class Stat(
    val notificationIcon: String,
    val purchaseSound: UncivSound,
    val character: Char,
    val isPotentialCurrency: Boolean = true
) {
    Production(NotificationIcon.Production, UncivSound.Click, Fonts.production, false),
    Food(NotificationIcon.Food, UncivSound.Click, Fonts.food),
    Gold(NotificationIcon.Gold, UncivSound.Coin, Fonts.gold),
    Science(NotificationIcon.Science, UncivSound.Chimes, Fonts.science),
    Culture(NotificationIcon.Culture, UncivSound.Paper, Fonts.culture),
    Happiness(NotificationIcon.Happiness, UncivSound.Click, Fonts.happiness, false),
    Faith(NotificationIcon.Faith, UncivSound.Choir, Fonts.faith);
}

// Should the well-known colours for these be needed:
// Production = "#c14d00", Food = "#38ff70", Gold = "#ffeb7f", Science = "#8c9dff", Culture = "#8b60ff", Happiness = "#ffd800", Faith = "#cbdfff"
