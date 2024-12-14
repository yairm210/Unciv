package com.unciv.models.stats

enum class SubStat : GameResource {
    GoldenAgePoints,
    TotalCulture,
    StoredFood,
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
