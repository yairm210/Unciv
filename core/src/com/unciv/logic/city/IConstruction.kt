package com.unciv.logic.city

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.toPercent
import kotlin.math.pow
import kotlin.math.roundToInt

interface IConstruction : INamed {
    fun isBuildable(cityConstructions: CityConstructions): Boolean
    fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean
    fun getResourceRequirements(): HashMap<String,Int>
    fun requiresResource(resource: String): Boolean
}

interface INonPerpetualConstruction : IConstruction, INamed, IHasUniques {
    var cost: Int
    val hurryCostModifier: Int
    var requiredTech: String?

    fun getProductionCost(civInfo: CivilizationInfo): Int
    fun getStatBuyCost(cityInfo: CityInfo, stat: Stat): Int?
    fun getRejectionReasons(cityConstructions: CityConstructions): RejectionReasons
    fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat? = null): Boolean  // Yes I'm hilarious.

    /** Only checks if it has the unique to be bought with this stat, not whether it is purchasable at all */
    fun canBePurchasedWithStat(cityInfo: CityInfo?, stat: Stat): Boolean {
        if (stat == Stat.Production || stat == Stat.Happiness) return false
        if (hasUnique(UniqueType.CannotBePurchased)) return false
        if (stat == Stat.Gold) return !hasUnique(UniqueType.Unbuildable)
        // Can be purchased with [Stat] [cityFilter]
        if (cityInfo != null && getMatchingUniques(UniqueType.CanBePurchasedWithStat)
            .any { it.params[0] == stat.name && cityInfo.matchesFilter(it.params[1]) }
        ) return true
        // Can be purchased for [amount] [Stat] [cityFilter]
        if (cityInfo != null && getMatchingUniques(UniqueType.CanBePurchasedForAmountStat)
            .any { it.params[1] == stat.name && cityInfo.matchesFilter(it.params[2]) }
        ) return true
        return false
    }

    /** Checks if the construction should be purchasable, not whether it can be bought with a stat at all */
    fun isPurchasable(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.all { it.rejectionReason == RejectionReason.Unbuildable }
    }

    fun canBePurchasedWithAnyStat(cityInfo: CityInfo): Boolean {
        return Stat.values().any { canBePurchasedWithStat(cityInfo, it) }
    }

    fun getBaseGoldCost(civInfo: CivilizationInfo): Double {
        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        return (30.0 * getProductionCost(civInfo)).pow(0.75) * hurryCostModifier.toPercent()
    }

    fun getBaseBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        if (stat == Stat.Gold) return getBaseGoldCost(cityInfo.civInfo).toInt()

        val conditionalState = StateForConditionals(civInfo = cityInfo.civInfo, cityInfo = cityInfo)

        // Can be purchased for [amount] [Stat] [cityFilter]
        val lowestCostUnique = getMatchingUniques(UniqueType.CanBePurchasedForAmountStat, conditionalState)
            .filter { it.params[1] == stat.name && cityInfo.matchesFilter(it.params[2]) }
            .minByOrNull { it.params[0].toInt() }
        if (lowestCostUnique != null) return lowestCostUnique.params[0].toInt()

        // Can be purchased with [Stat] [cityFilter]
        if (getMatchingUniques(UniqueType.CanBePurchasedWithStat, conditionalState)
            .any { it.params[0] == stat.name && cityInfo.matchesFilter(it.params[1]) }
        ) return cityInfo.civInfo.getEra().baseUnitBuyCost
        return null
    }

    fun getCostForConstructionsIncreasingInPrice(baseCost: Int, increaseCost: Int, previouslyBought: Int): Int {
        return (baseCost + increaseCost / 2f * ( previouslyBought * previouslyBought + previouslyBought )).toInt()
    }
}




class RejectionReasons: HashSet<RejectionReasonInstance>() {

    fun add(rejectionReason: RejectionReason) = add(RejectionReasonInstance(rejectionReason))

    fun contains(rejectionReason: RejectionReason) = any { it.rejectionReason == rejectionReason }

    fun isOKIgnoringRequirements(
        ignoreTechPolicyEraWonderRequirements: Boolean = false,
        ignoreResources: Boolean = false
    ): Boolean {
        if (!ignoreTechPolicyEraWonderRequirements && !ignoreResources) return isEmpty()
        if (!ignoreTechPolicyEraWonderRequirements)
            return all { it.rejectionReason == RejectionReason.ConsumesResources }
        if (!ignoreResources)
            return all { it.rejectionReason in techPolicyEraWonderRequirements }
        return all {
            it.rejectionReason == RejectionReason.ConsumesResources ||
            it.rejectionReason in techPolicyEraWonderRequirements
        }
    }

    fun hasAReasonToBeRemovedFromQueue(): Boolean {
        return any { it.rejectionReason in reasonsToDefinitivelyRemoveFromQueue }
    }

    fun getMostImportantRejectionReason(): String? {
        for (rejectionReason in orderOfErrorMessages) {
            val rejectionReasonInstance = firstOrNull { it.rejectionReason == rejectionReason }
            if (rejectionReasonInstance != null) return rejectionReasonInstance.errorMessage
        }
        return null
    }

    // Used for constant variables in the functions above
    companion object {
        private val techPolicyEraWonderRequirements = hashSetOf(
            RejectionReason.Obsoleted,
            RejectionReason.RequiresTech,
            RejectionReason.RequiresPolicy,
            RejectionReason.MorePolicyBranches,
            RejectionReason.RequiresBuildingInSomeCity,
        )
        private val reasonsToDefinitivelyRemoveFromQueue = hashSetOf(
            RejectionReason.Obsoleted,
            RejectionReason.WonderAlreadyBuilt,
            RejectionReason.NationalWonderAlreadyBuilt,
            RejectionReason.CannotBeBuiltWith,
            RejectionReason.MaxNumberBuildable,
        )
        private val orderOfErrorMessages = listOf(
            RejectionReason.WonderBeingBuiltElsewhere,
            RejectionReason.NationalWonderBeingBuiltElsewhere,
            RejectionReason.RequiresBuildingInAllCities,
            RejectionReason.RequiresBuildingInThisCity,
            RejectionReason.RequiresBuildingInSomeCity,
            RejectionReason.PopulationRequirement,
            RejectionReason.ConsumesResources,
            RejectionReason.CanOnlyBePurchased,
            RejectionReason.MaxNumberBuildable,
            RejectionReason.NoPlaceToPutUnit,
        )
    }
}


enum class RejectionReason(val shouldShow: Boolean, val errorMessage: String) {
    AlreadyBuilt(false, "Building already built in this city"),
    Unbuildable(false, "Unbuildable"),
    CanOnlyBePurchased(true, "Can only be purchased"),
    ShouldNotBeDisplayed(false, "Should not be displayed"),

    DisabledBySetting(false, "Disabled by setting"),
    HiddenWithoutVictory(false, "Hidden because a victory type has been disabled"),

    MustBeOnTile(false, "Must be on a specific tile"),
    MustNotBeOnTile(false, "Must not be on a specific tile"),
    MustBeNextToTile(false, "Must be next to a specific tile"),
    MustNotBeNextToTile(false, "Must not be next to a specific tile"),
    MustOwnTile(false, "Must own a specific tile close by"),
    WaterUnitsInCoastalCities(false, "May only built water units in coastal cities"),
    CanOnlyBeBuiltInSpecificCities(false, "Can only be built in specific cities"),
    MaxNumberBuildable(false, "Maximum number have been built or are being constructed"),

    UniqueToOtherNation(false, "Unique to another nation"),
    ReplacedByOurUnique(false, "Our unique replaces this"),
    CannotBeBuilt(false, "Cannot be built by this nation"),

    Obsoleted(false, "Obsolete"),
    RequiresTech(false, "Required tech not researched"),
    RequiresPolicy(false, "Requires a specific policy!"),
    UnlockedWithEra(false, "Unlocked when reaching a specific era"),
    MorePolicyBranches(false, "Hidden until more policy branches are fully adopted"),

    RequiresNearbyResource(false, "Requires a certain resource being exploited nearby"),
    CannotBeBuiltWith(false, "Cannot be built at the same time as another building already built"),

    RequiresBuildingInThisCity(true, "Requires a specific building in this city!"),
    RequiresBuildingInAllCities(true, "Requires a specific building in all cities!"),
    RequiresBuildingInSomeCity(true, "Requires a specific building anywhere in your empire!"),

    WonderAlreadyBuilt(false, "Wonder already built"),
    NationalWonderAlreadyBuilt(false, "National Wonder already built"),
    WonderBeingBuiltElsewhere(true, "Wonder is being built elsewhere"),
    NationalWonderBeingBuiltElsewhere(true, "National Wonder is being built elsewhere"),
    CityStateWonder(false, "No Wonders for city-states"),
    CityStateNationalWonder(false, "No National Wonders for city-states"),
    WonderDisabledEra(false, "This Wonder is disabled when starting in this era"),

    ConsumesResources(true, "Consumes resources which you are lacking"),

    PopulationRequirement(true, "Requires more population"),

    NoSettlerForOneCityPlayers(false, "No settlers for city-states or one-city challengers"),
    NoPlaceToPutUnit(true, "No space to place this unit");

    fun toInstance(errorMessage: String = this.errorMessage,
        shouldShow: Boolean = this.shouldShow): RejectionReasonInstance {
        return RejectionReasonInstance(this, errorMessage, shouldShow)
    }
}

data class RejectionReasonInstance(val rejectionReason:RejectionReason,
                                   val errorMessage: String = rejectionReason.errorMessage,
                                   val shouldShow: Boolean = rejectionReason.shouldShow)


open class PerpetualConstruction(override var name: String, val description: String) : IConstruction {

    override fun shouldBeDisplayed(cityConstructions: CityConstructions) = isBuildable(cityConstructions)
    open fun getProductionTooltip(cityInfo: CityInfo) : String = ""

    companion object {
        val science = PerpetualStatConversion(Stat.Science)
        val gold = PerpetualStatConversion(Stat.Gold)
        val culture = PerpetualStatConversion(Stat.Culture)
        val faith = PerpetualStatConversion(Stat.Faith)
        val idle = object : PerpetualConstruction("Nothing", "The city will not produce anything.") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean = true
        }

        val perpetualConstructionsMap: Map<String, PerpetualConstruction>
                = mapOf(science.name to science, gold.name to gold, culture.name to culture, faith.name to faith, idle.name to idle)
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            throw Exception("Impossible!")

    override fun getResourceRequirements(): HashMap<String, Int> = hashMapOf()

    override fun requiresResource(resource: String) = false

}

open class PerpetualStatConversion(val stat: Stat) :
    PerpetualConstruction(stat.name, "Convert production to [${stat.name}] at a rate of [rate] to 1") {

    override fun getProductionTooltip(cityInfo: CityInfo) : String
            = "\r\n${(cityInfo.cityStats.currentCityStats.production / getConversionRate(cityInfo)).roundToInt()}/${Fonts.turn}"
    fun getConversionRate(cityInfo: CityInfo) : Int = (1/cityInfo.cityStats.getStatConversionRate(stat)).roundToInt()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        val hasProductionUnique = cityConstructions.cityInfo.civInfo.getMatchingUniques(UniqueType.EnablesCivWideStatProduction).any { it.params[0] == stat.name }
        return when (stat) {
            Stat.Science -> hasProductionUnique
                    || cityConstructions.cityInfo.civInfo.hasUnique(UniqueType.EnablesScienceProduction) // backwards compatibility
            Stat.Gold -> hasProductionUnique
                    || cityConstructions.cityInfo.civInfo.hasUnique(UniqueType.EnablesGoldProduction) // backwards compatibility
            Stat.Culture -> hasProductionUnique
            Stat.Faith -> cityConstructions.cityInfo.civInfo.gameInfo.isReligionEnabled() && hasProductionUnique
            else -> false
        }
    }
}
