package com.unciv.models.stats

import com.unciv.logic.civilization.NotificationIcon

enum class SubStat(val text: String, val icon: String) : GameResource {
    GoldenAgePoints("Golde Age points", NotificationIcon.Happiness),
    TotalCulture("Total Culture", NotificationIcon.Culture),
    StoredFood("Stored Food", NotificationIcon.Food),
    ;
    companion object {
        val useableToBuy = setOf(GoldenAgePoints, StoredFood)
        val civWideSubStats = setOf(GoldenAgePoints, TotalCulture)
        fun safeValueOf(name: String): SubStat? = entries.firstOrNull { it.text == name }
    }
}
