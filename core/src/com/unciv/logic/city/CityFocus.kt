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
 *  Order matters for building the [CitizenManagementTable]
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
    FoodFocus("${Stat.Food.character}", true, Stat.Food),
    ProductionFocus("${Stat.Production.character}", true, Stat.Production),
    GoldFocus("${Stat.Gold.character}", true, Stat.Gold),
    ScienceFocus("${Stat.Science.character}", true, Stat.Science),
    CultureFocus("${Stat.Culture.character}", true, Stat.Culture),
    HappinessFocus("${Stat.Happiness.character}", false, Stat.Happiness),
    FaithFocus("${Stat.Faith.character}", true, Stat.Faith),
    GoldGrowthFocus("${Stat.Gold.character} ${Stat.Food.character}", true) {
        override fun getStatMultiplier(stat: Stat) = when (stat) {
            Stat.Gold -> 2f
            Stat.Food -> 1.5f
            else -> 1f
        }
    },
    ProductionGrowthFocus("${Stat.Production.character} ${Stat.Food.character}", true) {
        override fun getStatMultiplier(stat: Stat) = when (stat) {
            Stat.Production -> 2f
            Stat.Food -> 1.5f
            else -> 1f
        }
    },
    //GreatPersonFocus

    ;
    // endregion Enum values

    val binding: KeyboardBinding =
        binding ?:
        KeyboardBinding.values().firstOrNull { it.name == name } ?:
        KeyboardBinding.None

    open fun getStatMultiplier(stat: Stat) = when (this.stat) {
        stat -> 3.05f // on ties, prefer the Focus
        else -> 1f
    }

    private val statValuesForFocus: List<Stat> by lazy {
        Stat.values().filter { getStatMultiplier(it) != 1f }
    }

    fun applyWeightTo(stats: Stats) {
        for (stat in statValuesForFocus) {
            val currentStat = stats[stat]
            if (currentStat == 0f) continue
            val statMultiplier = getStatMultiplier(stat)
            stats[stat] = currentStat * statMultiplier
        }
    }

    companion object {
        fun safeValueOf(stat: Stat): CityFocus {
            return values().firstOrNull { it.stat == stat } ?: NoFocus
        }

        // set used in Automation. All non-Food Focuses, so targets 0 Surplus Food
        val zeroFoodFocuses = setOf(
            CultureFocus, FaithFocus, GoldFocus,
            HappinessFocus, ProductionFocus, ScienceFocus
        )
    }
}

