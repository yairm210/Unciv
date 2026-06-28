package com.unciv.models.ruleset

import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.fonts.Fonts
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

open class PerpetualConstruction(override var name: String, val description: String) :
    IConstruction {

    override fun shouldBeDisplayed(cityConstructions: CityConstructions) = isBuildable(cityConstructions)
    @Readonly
    open fun getProductionTooltip(city: City, withIcon: Boolean = false) : String = ""
    override fun getStockpiledResourceRequirements(state: GameContext) = Counter.ZERO

    companion object {
        val science = PerpetualStatConversion(Stat.Science)
        val gold = PerpetualStatConversion(Stat.Gold)
        val culture = PerpetualStatConversion(Stat.Culture)
        val faith = PerpetualStatConversion(Stat.Faith)
        val food = PerpetualStatConversion(Stat.Food)
        val idle = object : PerpetualConstruction("Nothing", "The city will not produce anything.") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean = true
        }

        val perpetualConstructionsMap: Map<String, PerpetualConstruction>
                = mapOf(science.name to science, gold.name to gold, culture.name to culture, faith.name to faith, food.name to food, idle.name to idle)

        /** @return whether [name] represents a PerpetualConstruction - note "" is translated to Nothing in the queue so `isNamePerpetual("")==true` */
        fun isNamePerpetual(name: String) = name.isEmpty() || name in perpetualConstructionsMap
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            throw Exception("Impossible!")

    override fun getResourceRequirementsPerTurn(state: GameContext?) = Counter.ZERO

    override fun requiredResources(state: GameContext): Set<String> = emptySet()
}

open class PerpetualStatConversion(val stat: Stat) :
    PerpetualConstruction(stat.name, "Convert production to [${stat.name}] at a rate of [rate] to 1") {

    override fun getProductionTooltip(city: City, withIcon: Boolean) : String
            = "\r\n${(city.cityStats.currentCityStats.production / getConversionRate(city)).roundToInt()}${if (withIcon) stat.character else ""}/${Fonts.turn}"
    @Readonly
    fun getConversionRate(city: City) : Int = (1/city.cityStats.getStatConversionRate(stat)).roundToInt()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        val city = cityConstructions.city
        if (stat == Stat.Faith && !city.civ.gameInfo.isReligionEnabled())
            return false

        val context = city.state
        @Suppress("DEPRECATION") // forEachMatchingUnique doesn't allow a `return true` exit
        return city.civ.getMatchingUniques(UniqueType.EnablesStatProduction, context)
            .any { it.params[0] == stat.name }
    }
}
