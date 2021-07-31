package com.unciv.models.stats

/**
 * Enum of all possible 'Stats' you cann accumulate in Unciv
 * @param canBePlundered    used for the plunder unique to prevent plundering a Stat that does not have a Civ-wide 'account'
 */
enum class Stat(val canBePlundered: Boolean = false){
    Production,
    Food,
    Gold(true),
    Science(true),
    Culture(true),
    Happiness,
    Faith(true);
    
    companion object {
        /** a version of valueOf that will not throw IllegalArgumentException */
        fun valueOfOrNull(name: String) = values().firstOrNull { it.name == name }

        // should be a little more efficient than
        // fun valueOfOrNull(name: String) = try { valueOf(name) } catch (ex: Throwable) { null }
    }
}
