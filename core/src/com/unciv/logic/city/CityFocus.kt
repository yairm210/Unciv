package com.unciv.logic.city

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.managers.CityPopulationManager
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.screens.cityscreen.CitizenManagementTable
import com.unciv.ui.images.ImageGetter

/**
 *  Controls automatic worker-to-tile assignment
 *  @param  label Display label, formatted for tr()
 *  @param  tableEnabled Whether to show or hide in CityScreen's [CitizenManagementTable]
 *  @param  stat Which stat the default [getStatMultiplier] emphasizes - unused if that is overridden w/o calling super
 *  @param  binding Bindable keyboard key in UI - this is an override, by default matching enum names in [KeyboardBinding] are assigned automatically
 *  @see    CityPopulationManager.autoAssignPopulation
 *  @see    Automation.rankStatsForCityWork
 */
enum class CityFocus(
    val label: String,
    val tableEnabled: Boolean,
    val stat: Stat? = null,
    binding: KeyboardBinding? = null
) : IsPartOfGameInfoSerialization {
    // region Enum values
    NoFocus("Default", true, null) {
        override fun getStatMultiplier(stat: Stat) = 1f  // actually redundant, but that's two steps to see
    },
    Manual("Manual", true, null) {
        override fun getStatMultiplier(stat: Stat) = 1f
    },
    FoodFocus("[${Stat.Food.name}]", true, Stat.Food),
    ProductionFocus("[${Stat.Production.name}]", true, Stat.Production),
    GoldFocus("[${Stat.Gold.name}]", true, Stat.Gold),
    ScienceFocus("[${Stat.Science.name}]", true, Stat.Science),
    CultureFocus("[${Stat.Culture.name}]", true, Stat.Culture),
    FaithFocus("[${Stat.Faith.name}]", true, Stat.Faith),
    GoldGrowthFocus("[${Stat.Gold.name}] [${Stat.Food.name}]", true) {
        override fun getStatMultiplier(stat: Stat) = when (stat) {
            Stat.Gold, Stat.Food -> 2f
            else -> 1f
        }
    },
    ProductionGrowthFocus("[${Stat.Production.name}] [${Stat.Food.name}]", true) {
        override fun getStatMultiplier(stat: Stat) = when (stat) {
            Stat.Production, Stat.Food -> 2f
            else -> 1f
        }
    },
    HappinessFocus("[${Stat.Happiness.name}]", false, Stat.Happiness),
    //GreatPersonFocus

    ;
    // endregion Enum values

    val binding: KeyboardBinding =
        binding ?:
        KeyboardBinding.values().firstOrNull { it.name == name } ?:
        KeyboardBinding.None

    open fun getStatMultiplier(stat: Stat) = when (this.stat) {
        stat -> 3f
        else -> 1f
    }

    fun applyWeightTo(stats: Stats) {
        for (stat in Stat.values()) {
            stats[stat] *= getStatMultiplier(stat)
        }
    }

    companion object {
        fun safeValueOf(stat: Stat): CityFocus {
            return values().firstOrNull { it.stat == stat } ?: NoFocus
        }
    }
}
