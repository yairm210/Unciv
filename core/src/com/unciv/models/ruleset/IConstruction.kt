package com.unciv.models.ruleset

import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.components.fonts.Fonts
import kotlin.math.pow
import kotlin.math.roundToInt

interface IConstruction : INamed {
    fun isBuildable(cityConstructions: CityConstructions): Boolean
    fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean
    /** Gets *per turn* resource requirements - does not include immediate costs for stockpiled resources.
     * Uses [stateForConditionals] to determine which civ or city this is built for*/
    fun getResourceRequirementsPerTurn(stateForConditionals: StateForConditionals? = null): Counter<String>
    fun requiresResource(resource: String, stateForConditionals: StateForConditionals? = null): Boolean
    /** We can't call this getMatchingUniques because then it would conflict with IHasUniques */
    fun getMatchingUniquesNotConflicting(uniqueType: UniqueType) = sequenceOf<Unique>()
}

interface INonPerpetualConstruction : IConstruction, INamed, IHasUniques {
    var cost: Int
    val hurryCostModifier: Int
    // Future development should not increase the role of requiredTech, and should reduce it when possible.
    // https://yairm210.github.io/Unciv/Developers/Translations%2C-mods%2C-and-modding-freedom-in-Open-Source#filters
    var requiredTech: String?

    override fun legacyRequiredTechs(): Sequence<String> = if (requiredTech == null) sequenceOf() else sequenceOf(requiredTech!!)

    fun getProductionCost(civInfo: Civilization, city: City?): Int
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

    fun getBaseGoldCost(civInfo: Civilization, city: City?): Double {
        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        return (30.0 * getProductionCost(civInfo, city)).pow(0.75) * hurryCostModifier.toPercent()
    }

    fun getBaseBuyCost(city: City, stat: Stat): Float? {
        val conditionalState = StateForConditionals(civInfo = city.civ, city = city)

        // Can be purchased for [amount] [Stat] [cityFilter]
        val lowestCostUnique = getMatchingUniques(UniqueType.CanBePurchasedForAmountStat, conditionalState)
            .filter { it.params[1] == stat.name && city.matchesFilter(it.params[2]) }
            .minByOrNull { it.params[0].toInt() }
        if (lowestCostUnique != null) return lowestCostUnique.params[0].toInt() * city.civ.gameInfo.speed.statCostModifiers[stat]!!

        if (stat == Stat.Gold) return getBaseGoldCost(city.civ, city).toFloat()

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



class RejectionReason(val type: RejectionReasonType,
                      val errorMessage: String = type.errorMessage,
                      val shouldShow: Boolean = type.shouldShow) {

    fun techPolicyEraWonderRequirements(): Boolean = type in techPolicyEraWonderRequirements

    fun hasAReasonToBeRemovedFromQueue(): Boolean = type in reasonsToDefinitivelyRemoveFromQueue

    fun isImportantRejection(): Boolean = type in orderedImportantRejectionTypes

    fun isConstructionRejection(): Boolean = type in constructionRejectionReasonType

    /** Returns the index of [orderedImportantRejectionTypes] with the smallest index having the
     * highest precedence */
    fun getRejectionPrecedence(): Int {
        return orderedImportantRejectionTypes.indexOf(type)
    }

    // Used for constant variables in the functions above
    private val techPolicyEraWonderRequirements = hashSetOf(
        RejectionReasonType.Obsoleted,
        RejectionReasonType.RequiresTech,
        RejectionReasonType.RequiresPolicy,
        RejectionReasonType.MorePolicyBranches,
        RejectionReasonType.RequiresBuildingInSomeCity,
    )
    private val reasonsToDefinitivelyRemoveFromQueue = hashSetOf(
        RejectionReasonType.Obsoleted,
        RejectionReasonType.WonderAlreadyBuilt,
        RejectionReasonType.NationalWonderAlreadyBuilt,
        RejectionReasonType.CannotBeBuiltWith,
        RejectionReasonType.MaxNumberBuildable,
    )
    private val orderedImportantRejectionTypes = listOf(
        RejectionReasonType.ShouldNotBeDisplayed,
        RejectionReasonType.WonderBeingBuiltElsewhere,
        RejectionReasonType.RequiresBuildingInAllCities,
        RejectionReasonType.RequiresBuildingInThisCity,
        RejectionReasonType.RequiresBuildingInSomeCity,
        RejectionReasonType.RequiresBuildingInSomeCities,
        RejectionReasonType.CanOnlyBeBuiltInSpecificCities,
        RejectionReasonType.CannotBeBuiltUnhappiness,
        RejectionReasonType.PopulationRequirement,
        RejectionReasonType.ConsumesResources,
        RejectionReasonType.CanOnlyBePurchased,
        RejectionReasonType.MaxNumberBuildable,
        RejectionReasonType.NoPlaceToPutUnit,
    )
    // Exceptions. Used for units spawned/upgrade path, not built
    private val constructionRejectionReasonType = listOf(
        RejectionReasonType.Unbuildable,
        RejectionReasonType.CannotBeBuiltUnhappiness,
        RejectionReasonType.CannotBeBuilt,
        RejectionReasonType.CanOnlyBeBuiltInSpecificCities,
    )
}


enum class RejectionReasonType(val shouldShow: Boolean, val errorMessage: String) {
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
    CanOnlyBeBuiltInSpecificCities(false, "Build requirements not met in this city"),
    MaxNumberBuildable(false, "Maximum number have been built or are being constructed"),

    UniqueToOtherNation(false, "Unique to another nation"),
    ReplacedByOurUnique(false, "Our unique replaces this"),
    CannotBeBuilt(false, "Cannot be built by this nation"),
    CannotBeBuiltUnhappiness(true, "Unhappiness"),

    Obsoleted(false, "Obsolete"),
    RequiresTech(false, "Required tech not researched"),
    RequiresPolicy(false, "Requires a specific policy!"),
    UnlockedWithEra(false, "Unlocked when reaching a specific era"),
    MorePolicyBranches(false, "Hidden until more policy branches are fully adopted"),

    RequiresNearbyResource(false, "Requires a certain resource being exploited nearby"),
    CannotBeBuiltWith(false, "Cannot be built at the same time as another building already built"),

    RequiresBuildingInThisCity(true, "Requires a specific building in this city!"),
    RequiresBuildingInAllCities(true, "Requires a specific building in all cities!"),
    RequiresBuildingInSomeCities(true, "Requires a specific building in more cities!"),
    RequiresBuildingInSomeCity(true, "Requires a specific building anywhere in your empire!"),

    WonderAlreadyBuilt(false, "Wonder already built"),
    NationalWonderAlreadyBuilt(false, "National Wonder already built"),
    WonderBeingBuiltElsewhere(true, "Wonder is being built elsewhere"),
    CityStateWonder(false, "No Wonders for city-states"),
    PuppetWonder(false, "No Wonders for Puppets"),
    WonderDisabledEra(false, "This Wonder is disabled when starting in this era"),

    ConsumesResources(true, "Consumes resources which you are lacking"),

    PopulationRequirement(true, "Requires more population"),

    NoSettlerForOneCityPlayers(false, "No settlers for city-states or one-city challengers"),
    NoPlaceToPutUnit(true, "No space to place this unit");

    fun toInstance(errorMessage: String = this.errorMessage,
        shouldShow: Boolean = this.shouldShow): RejectionReason {
        return RejectionReason(this, errorMessage, shouldShow)
    }
}

open class PerpetualConstruction(override var name: String, val description: String) :
    IConstruction {

    override fun shouldBeDisplayed(cityConstructions: CityConstructions) = isBuildable(cityConstructions)
    open fun getProductionTooltip(city: City, withIcon: Boolean = false) : String = ""

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

        /** @return whether [name] represents a PerpetualConstruction - note "" is translated to Nothing in the queue so `isNamePerpetual("")==true` */
        fun isNamePerpetual(name: String) = name.isEmpty() || name in perpetualConstructionsMap
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            throw Exception("Impossible!")

    override fun getResourceRequirementsPerTurn(stateForConditionals: StateForConditionals?) = Counter.ZERO

    override fun requiresResource(resource: String, stateForConditionals: StateForConditionals?) = false

}

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
