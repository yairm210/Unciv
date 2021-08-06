package com.unciv.models.stats

import com.unciv.logic.civilization.NotificationIcon

enum class Stat(val notificationIcon: String) {
    Production(NotificationIcon.Production),
    Food(NotificationIcon.Food),
    Gold(NotificationIcon.Gold),
    Science(NotificationIcon.Science),
    Culture(NotificationIcon.Culture),
    Happiness(NotificationIcon.Happiness),
    Faith(NotificationIcon.Faith);
}