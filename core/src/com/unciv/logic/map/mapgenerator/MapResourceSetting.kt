package com.unciv.logic.map.mapgenerator

enum class MapResourceSetting(
    val label: String,
    val randomLuxuriesPercent: Int = 100,
    val regionalLuxuriesDelta: Int = 0,
    val specialLuxuriesTargetFactor: Float = 0.75f,
    val bonusFrequencyMultiplier: Float = 1f
) {
    sparse("Sparse", 80, -1, 0.5f, 1.5f),
    default("Default"),
    abundant("Abundant", 133, 1, 0.9f, 0.6667f),
    @Deprecated("Since 4.10.7, moved to mapParameters")
    strategicBalance("Strategic Balance"),
    @Deprecated("Since 4.10.7, moved to mapParameters")
    legendaryStart("Legendary Start"),
    ;
    private fun active() = declaringJavaClass.getField(name).getAnnotation(Deprecated::class.java) == null
    companion object {
        fun activeLabels() = entries.filter { it.active() }.map { it.label }
        fun safeValueOf(label: String) = entries.firstOrNull { it.label == label } ?: default
    }
}
