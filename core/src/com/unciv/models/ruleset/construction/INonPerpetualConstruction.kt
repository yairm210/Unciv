package com.unciv.models.ruleset.construction

import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.toPercent
import kotlin.math.pow

interface INonPerpetualConstruction : IConstruction, INamed, IHasUniques {
    var cost: Int
    val hurryCostModifier: Int
    // Future development should not increase the role of requiredTech, and should reduce it when possible.
    // https://yairm210.github.io/Unciv/Developers/Translations%2C-mods%2C-and-modding-freedom-in-Open-Source#filters
    @Deprecated("The functionality provided by the requiredTech field is provided by the OnlyAvailableWhen unique.")
    var requiredTech: String?

    override fun legacyRequiredTechs(): Sequence<String> = if (requiredTech == null) sequenceOf() else sequenceOf(requiredTech!!)

    fun getProductionCost(civInfo: Civilization): Int
    fun getStatBuyCost(city: City, stat: Stat): Int?
    fun getRejectionReasons(cityConstructions: CityConstructions): Sequence<RejectionReason>

    /** Returns whether was successful - can fail for units if we can't place them */
    fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat? = null): Boolean  // Yes I'm hilarious.

    /** Only checks if it has the unique to be bought with this stat, not whether it is purchasable at all */
    fun canBePurchasedWithStat(city: City?, stat: Stat): Boolean {
        val stateForConditionals = StateForConditionals(city?.civ, city)
        if (stat == Stat.Production || stat == Stat.Happiness) return false
        if (hasUnique(UniqueType.CannotBePurchased, stateForConditionals)) return false
        // Can be purchased with [Stat] [cityFilter]
        if (city != null && getMatchingUniques(UniqueType.CanBePurchasedWithStat, stateForConditionals)
            .any { it.params[0] == stat.name && city.matchesFilter(it.params[1]) }
        ) return true
        // Can be purchased for [amount] [Stat] [cityFilter]
        if (city != null && getMatchingUniques(UniqueType.CanBePurchasedForAmountStat, stateForConditionals)
            .any { it.params[1] == stat.name && city.matchesFilter(it.params[2]) }
        ) return true
        if (stat == Stat.Gold) return !hasUnique(UniqueType.Unbuildable, stateForConditionals)
        return false
    }

    /** Checks if the construction should be purchasable, not whether it can be bought with a stat at all */
    fun isPurchasable(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.all { it.type == RejectionReasonType.Unbuildable }
    }

    fun canBePurchasedWithAnyStat(city: City): Boolean {
        return Stat.values().any { canBePurchasedWithStat(city, it) }
    }

    fun getCivilopediaGoldCost(): Int {
        // Same as getBaseGoldCost, but without game-specific modifiers
        return ((30.0 * cost.toFloat()).pow(0.75) * hurryCostModifier.toPercent() / 10).toInt() * 10
    }

    fun getBaseGoldCost(civInfo: Civilization): Double {
        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        return (30.0 * getProductionCost(civInfo)).pow(0.75) * hurryCostModifier.toPercent()
    }

    fun getBaseBuyCost(city: City, stat: Stat): Float? {
        val conditionalState = StateForConditionals(civInfo = city.civ, city = city)

        // Can be purchased for [amount] [Stat] [cityFilter]
        val lowestCostUnique = getMatchingUniques(UniqueType.CanBePurchasedForAmountStat, conditionalState)
            .filter { it.params[1] == stat.name && city.matchesFilter(it.params[2]) }
            .minByOrNull { it.params[0].toInt() }
        if (lowestCostUnique != null) return lowestCostUnique.params[0].toInt() * city.civ.gameInfo.speed.statCostModifiers[stat]!!

        if (stat == Stat.Gold) return getBaseGoldCost(city.civ).toFloat()

        // Can be purchased with [Stat] [cityFilter]
        if (getMatchingUniques(UniqueType.CanBePurchasedWithStat, conditionalState)
            .any { it.params[0] == stat.name && city.matchesFilter(it.params[1]) }
        ) return city.civ.getEra().baseUnitBuyCost * city.civ.gameInfo.speed.statCostModifiers[stat]!!
        return null
    }

    fun getCostForConstructionsIncreasingInPrice(baseCost: Int, increaseCost: Int, previouslyBought: Int): Int {
        return (baseCost + increaseCost / 2f * ( previouslyBought * previouslyBought + previouslyBought )).toInt()
    }

    override fun getMatchingUniquesNotConflicting(uniqueType: UniqueType): Sequence<Unique> =
            getMatchingUniques(uniqueType)
}
