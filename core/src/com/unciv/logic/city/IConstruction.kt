package com.unciv.logic.city

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.toPercent
import kotlin.math.pow
import kotlin.math.roundToInt

interface IConstruction : INamed {
    fun isBuildable(cityConstructions: CityConstructions): Boolean
    fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean
    fun getResourceRequirements(): HashMap<String,Int>
    fun requiresResource(resource: String): Boolean
}

interface INonPerpetualConstruction : IConstruction, INamed, IHasUniques {
    val hurryCostModifier: Int

    fun getProductionCost(civInfo: CivilizationInfo): Int
    fun getStatBuyCost(cityInfo: CityInfo, stat: Stat): Int?
    fun getRejectionReasons(cityConstructions: CityConstructions): RejectionReasons
    fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat? = null): Boolean  // Yes I'm hilarious.

    fun canBePurchasedWithStat(cityInfo: CityInfo?, stat: Stat): Boolean {
        if (stat in listOf(Stat.Production, Stat.Happiness)) return false
        if (hasUnique(UniqueType.CannotBePurchased)) return false
        if (stat == Stat.Gold) return !hasUnique(UniqueType.Unbuildable)
        // Can be purchased with [Stat] [cityFilter]
        if (getMatchingUniques(UniqueType.CanBePurchasedWithStat)
            .any { it.params[0] == stat.name && (cityInfo != null && cityInfo.matchesFilter(it.params[1])) }
        ) return true
        // Can be purchased for [amount] [Stat] [cityFilter]
        if (getMatchingUniques(UniqueType.CanBePurchasedForAmountStat)
            .any { it.params[1] == stat.name && (cityInfo != null && cityInfo.matchesFilter(it.params[2])) }
        ) return true
        return false
    }

    /** Checks if the construction should be purchasable, not whether it can be bought with a stat at all */
    fun isPurchasable(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.all { it == RejectionReason.Unbuildable }
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




class RejectionReasons: HashSet<RejectionReason>() {

    fun filterTechPolicyEraWonderRequirements(): HashSet<RejectionReason> {
        return filterNot { it in techPolicyEraWonderRequirements }.toHashSet()
    }

    fun hasAReasonToBeRemovedFromQueue(): Boolean {
        return any { it in reasonsToDefinitivelyRemoveFromQueue }
    }

    fun getMostImportantRejectionReason(): String? {
        return orderOfErrorMessages.firstOrNull { it in this }?.errorMessage
    }

    // Used for constant variables in the functions above
    companion object {
        private val techPolicyEraWonderRequirements = hashSetOf(
            RejectionReason.Obsoleted,
            RejectionReason.RequiresTech,
            RejectionReason.RequiresPolicy,
            RejectionReason.MorePolicyBranches,
            RejectionReason.RequiresBuildingInSomeCity
        )
        private val reasonsToDefinitivelyRemoveFromQueue = hashSetOf(
            RejectionReason.Obsoleted,
            RejectionReason.WonderAlreadyBuilt,
            RejectionReason.NationalWonderAlreadyBuilt,
            RejectionReason.CannotBeBuiltWith,
            RejectionReason.ReachedBuildCap
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
            RejectionReason.MaxNumberBuildable
        )
    }
} 


// TODO: Put a wrapper class around this containing the errorMessage, so that we don't
// change the value of a enum constant sometimes.
enum class RejectionReason(val shouldShow: Boolean, var errorMessage: String) {
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
    MaxNumberBuildable(true, "Maximum number being built"),

    UniqueToOtherNation(false, "Unique to another nation"),
    ReplacedByOurUnique(false, "Our unique replaces this"),
    CannotBeBuilt(false, "Cannot be built by this nation"),

    Obsoleted(false, "Obsolete"),
    RequiresTech(false, "Required tech not researched"),
    RequiresPolicy(false, "Requires a specific policy!"),
    UnlockedWithEra(false, "Unlocked when reaching a specific era"),
    MorePolicyBranches(false, "Hidden until more policy branches are fully adopted"),

    RequiresNearbyResource(false, "Requires a certain resource being exploited nearby"),
    InvalidRequiredBuilding(false, "Required building does not exist in ruleSet!"),
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

    ReachedBuildCap(false, "Don't need to build any more of these!"),

    ConsumesResources(true, "Consumes resources which you are lacking"),

    PopulationRequirement(true, "Requires more population"),

    NoSettlerForOneCityPlayers(false, "No settlers for city-states or one-city challengers"),
}


open class PerpetualConstruction(override var name: String, val description: String) : IConstruction {

    override fun shouldBeDisplayed(cityConstructions: CityConstructions) = isBuildable(cityConstructions)
    open fun getProductionTooltip(cityInfo: CityInfo) : String
            = "\r\n${(cityInfo.cityStats.currentCityStats.production / CONVERSION_RATE).roundToInt()}/${Fonts.turn}"
    open fun getConversionRate(cityInfo: CityInfo) : Int
            = CONVERSION_RATE

    companion object {
        const val CONVERSION_RATE: Int = 4
        val science = object : PerpetualConstruction("Science", "Convert production to science at a rate of [rate] to 1") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean {
                return cityConstructions.cityInfo.civInfo.hasUnique(UniqueType.EnablesScienceProduction)
            }
            override fun getProductionTooltip(cityInfo: CityInfo): String {
                return "\r\n${(cityInfo.cityStats.currentCityStats.production / getConversionRate(cityInfo)).roundToInt()}/${Fonts.turn}"
            }
            override fun getConversionRate(cityInfo: CityInfo) = (1/cityInfo.cityStats.getScienceConversionRate()).roundToInt()
        }
        val gold = object : PerpetualConstruction("Gold", "Convert production to gold at a rate of $CONVERSION_RATE to 1") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean {
                return cityConstructions.cityInfo.civInfo.hasUnique(UniqueType.EnablesGoldProduction)
            }
        }
        val idle = object : PerpetualConstruction("Nothing", "The city will not produce anything.") {
            override fun isBuildable(cityConstructions: CityConstructions): Boolean = true

            override fun getProductionTooltip(cityInfo: CityInfo): String = ""
        }

        val perpetualConstructionsMap: Map<String, PerpetualConstruction>
                = mapOf(science.name to science, gold.name to gold, idle.name to idle)
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            throw Exception("Impossible!")

    override fun getResourceRequirements(): HashMap<String, Int> = hashMapOf()

    override fun requiresResource(resource: String) = false

}
