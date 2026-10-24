package com.unciv.models.stats

import com.unciv.logic.civilization.NotificationIcon

enum class SubStat(val text: String, val icon: String) : GameResource {
    GoldenAgePoints("Golden Age points", NotificationIcon.Happiness),
    StoredFood("Stored Food", NotificationIcon.Food), // city-scoped
    ;
    companion object {
        fun safeValueOf(name: String): SubStat? = entries.firstOrNull { it.text == name }
    }
}
