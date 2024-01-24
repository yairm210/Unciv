package com.unciv.models.ruleset.construction

import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.fonts.Fonts
import kotlin.math.roundToInt

open class PerpetualStatConversion(val stat: Stat) :
    PerpetualConstruction(stat.name, "Convert production to [${stat.name}] at a rate of [rate] to 1") {

    override fun getProductionTooltip(city: City, withIcon: Boolean) : String
            = "\r\n${(city.cityStats.currentCityStats.production / getConversionRate(city)).roundToInt()}${if (withIcon) stat.character else ""}/${Fonts.turn}"
    fun getConversionRate(city: City) : Int = (1/city.cityStats.getStatConversionRate(stat)).roundToInt()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        val city = cityConstructions.city
        if (stat == Stat.Faith && !city.civ.gameInfo.isReligionEnabled())
            return false

        val stateForConditionals = StateForConditionals(city.civ, city, tile = city.getCenterTile())
        return city.civ.getMatchingUniques(UniqueType.EnablesCivWideStatProduction, stateForConditionals)
            .any { it.params[0] == stat.name }
    }
}
