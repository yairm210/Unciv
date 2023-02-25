package com.unciv.logic.city

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats

// if tableEnabled == true, then Stat != null
enum class CityFocus(val label: String, val tableEnabled: Boolean, val stat: Stat? = null) :
    IsPartOfGameInfoSerialization {
    NoFocus("Default Focus", true, null) {
        override fun getStatMultiplier(stat: Stat) = 1f  // actually redundant, but that's two steps to see
    },
    FoodFocus("[${Stat.Food.name}] Focus", true, Stat.Food),
    ProductionFocus("[${Stat.Production.name}] Focus", true, Stat.Production),
    GoldFocus("[${Stat.Gold.name}] Focus", true, Stat.Gold),
    ScienceFocus("[${Stat.Science.name}] Focus", true, Stat.Science),
    CultureFocus("[${Stat.Culture.name}] Focus", true, Stat.Culture),
    GoldGrowthFocus("Gold Growth Focus", false) {
        override fun getStatMultiplier(stat: Stat) = when (stat) {
            Stat.Gold, Stat.Food -> 2f
            else -> 1f
        }
    },
    ProductionGrowthFocus("Production Growth Focus", false) {
        override fun getStatMultiplier(stat: Stat) = when (stat) {
            Stat.Production, Stat.Food -> 2f
            else -> 1f
        }
    },
    FaithFocus("[${Stat.Faith.name}] Focus", true, Stat.Faith),
    HappinessFocus("[${Stat.Happiness.name}] Focus", false, Stat.Happiness);
    //GreatPersonFocus;

    open fun getStatMultiplier(stat: Stat) = when (this.stat) {
        stat -> 3f
        else -> 1f
    }

    fun applyWeightTo(stats: Stats) {
        for (stat in Stat.values()) {
            stats[stat] *= getStatMultiplier(stat)
        }
    }

    fun safeValueOf(stat: Stat): CityFocus {
        return values().firstOrNull { it.stat == stat } ?: NoFocus
    }
}
