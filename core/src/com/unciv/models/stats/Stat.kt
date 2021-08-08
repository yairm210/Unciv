package com.unciv.models.stats

import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.UncivSound

enum class Stat(val notificationIcon: String, val purchaseSound: UncivSound) {
    Production(NotificationIcon.Production, UncivSound.Click),
    Food(NotificationIcon.Food, UncivSound.Click),
    Gold(NotificationIcon.Gold, UncivSound.Coin),
    Science(NotificationIcon.Science, UncivSound.Chimes),
    Culture(NotificationIcon.Culture, UncivSound.Paper),
    Happiness(NotificationIcon.Happiness, UncivSound.Click),
    Faith(NotificationIcon.Faith, UncivSound.Choir);
}