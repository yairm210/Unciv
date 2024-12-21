package com.unciv.models.stats

import com.unciv.logic.civilization.NotificationIcon

enum class SubStat(val icon: String) : GameResource {
    GoldenAgePoints(NotificationIcon.Happiness),
    TotalCulture(NotificationIcon.Culture),
    StoredFood(NotificationIcon.Food),
    ;
    companion object {
        val useableToBuy = setOf(GoldenAgePoints, StoredFood)
        val civWideSubStats = setOf(GoldenAgePoints, TotalCulture)
        fun safeValueOf(name: String): SubStat? {
            return when (name) {
                GoldenAgePoints.name -> GoldenAgePoints
                TotalCulture.name -> TotalCulture
                StoredFood.name -> StoredFood
                else -> null
            }
        }
    }
}
