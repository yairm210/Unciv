package com.unciv.models.ruleset

import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stat.Companion.statsUsableToBuy
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.components.fonts.Fonts
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.math.pow
import kotlin.math.roundToInt

interface IConstruction : INamed {
    fun isBuildable(cityConstructions: CityConstructions): Boolean
    fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean
    /** We can't call this getMatchingUniques because then it would conflict with IHasUniques */
    fun getMatchingUniquesNotConflicting(uniqueType: UniqueType, gameContext: GameContext) = sequenceOf<Unique>()
    
    /** Gets *per turn* resource requirements - does not include immediate costs for stockpiled resources.
     * Uses [state] to determine which civ or city this is built for*/
    @Readonly fun getResourceRequirementsPerTurn(state: GameContext? = null): Counter<String>
    fun requiredResources(state: GameContext = GameContext.EmptyState): Set<String>
    fun getStockpiledResourceRequirements(state: GameContext): Counter<String>
}

interface INonPerpetualConstruction : IConstruction, INamed, IHasUniques {
    var cost: Int
    val hurryCostModifier: Int
    // Future development should not increase the role of requiredTech, and should reduce it when possible.
    // https://yairm210.github.io/Unciv/Developers/Translations%2C-mods%2C-and-modding-freedom-in-Open-Source#filters
    var requiredTech: String?

    override fun legacyRequiredTechs(): Sequence<String> = if (requiredTech == null) emptySequence() else sequenceOf(requiredTech!!)

    fun getProductionCost(civInfo: Civilization, city: City?): Int
    fun getStatBuyCost(city: City, stat: Stat): Int?
    fun getRejectionReasons(cityConstructions: CityConstructions): Sequence<RejectionReason>

    /** Only checks if it has the unique to be bought with this stat, not whether it is purchasable at all */
    fun canBePurchasedWithStat(city: City?, stat: Stat): Boolean {
        return canBePurchasedWithStatReasons(city, stat).purchasable
    }

    /** Only checks if it has the unique to be bought with this stat, not whether it is purchasable at all */
    fun canBePurchasedWithStatReasons(city: City?, stat: Stat): PurchaseReason {
        val gameContext = city?.state ?: GameContext.EmptyState
        if (stat == Stat.Production || stat == Stat.Happiness) return PurchaseReason.Invalid
        if (hasUnique(UniqueType.CannotBePurchased, gameContext)) return PurchaseReason.Unpurchasable
        // Can be purchased with [Stat] [cityFilter]
        if (getMatchingUniques(UniqueType.CanBePurchasedWithStat, GameContext.IgnoreConditionals)
            .any {
                it.params[0] == stat.name &&
                    (city == null || (it.conditionalsApply(gameContext) && city.matchesFilter(it.params[1])))
            }
        ) return PurchaseReason.UniqueAllowed
        // Can be purchased for [amount] [Stat] [cityFilter]
        if (getMatchingUniques(UniqueType.CanBePurchasedForAmountStat, GameContext.IgnoreConditionals)
            .any {
                it.params[1] == stat.name &&
                    (city == null || (it.conditionalsApply(gameContext) && city.matchesFilter(it.params[2])))
            }
        ) return PurchaseReason.UniqueAllowed
        if (stat == Stat.Gold && !hasUnique(UniqueType.Unbuildable, gameContext)) return PurchaseReason.Allowed
        return PurchaseReason.NotAllowed
    }

    /** Checks if the construction should be purchasable, not whether it can be bought with a stat at all */
    fun isPurchasable(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.all { it.type == RejectionReasonType.Unbuildable }
    }

    fun canBePurchasedWithAnyStat(city: City): Boolean {
        return statsUsableToBuy.any { canBePurchasedWithStat(city, it) }
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
        val conditionalState = city.state

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

    @Readonly
    fun getCostForConstructionsIncreasingInPrice(baseCost: Int, increaseCost: Int, previouslyBought: Int): Int {
        return (baseCost + increaseCost / 2f * ( previouslyBought * previouslyBought + previouslyBought )).toInt()
    }

    @Readonly
    override fun getMatchingUniquesNotConflicting(uniqueType: UniqueType, gameContext: GameContext): Sequence<Unique> =
            getMatchingUniques(uniqueType, gameContext)

    @Readonly
    override fun requiredResources(state: GameContext): Set<String> {
        return getResourceRequirementsPerTurn(state).keys +
                getMatchingUniques(UniqueType.CostsResources, state).map { it.params[1] }
    }
    
    @Readonly
    override fun getStockpiledResourceRequirements(state: GameContext): Counter<String> {
        @LocalState val counter = Counter<String>()
        for (unique in getMatchingUniquesNotConflicting(UniqueType.CostsResources, state)){
            var amount = unique.params[0].toInt()
            if (unique.isModifiedByGameSpeed()) amount = (amount * state.gameInfo!!.speed.modifier).toInt()
            counter.add(unique.params[1], amount)
        }
        return counter
    }
}

enum class PurchaseReason(val purchasable: Boolean) {
    Allowed(true),
    Invalid(false),
    Unpurchasable(false),
    UniqueAllowed(true),
    Other(false),
    OtherAllowed(true),
    NotAllowed(false)
}


class RejectionReason(val type: RejectionReasonType,
                      val errorMessage: String = type.errorMessage,
                      val shouldShow: Boolean = type.shouldShow) {

    fun techPolicyEraWonderRequirements(): Boolean = type in techPolicyEraWonderRequirements

    fun hasAReasonToBeRemovedFromQueue(): Boolean = type in reasonsToDefinitivelyRemoveFromQueue

    fun isImportantRejection(): Boolean = type in orderedImportantRejectionTypes

    fun isConstructionRejection(): Boolean = type in constructionRejectionReasonType

    fun isNeverVisible(): Boolean = type in neverVisible

    /** Returns the index of [orderedImportantRejectionTypes] with the smallest index having the
     * highest precedence */
    fun getRejectionPrecedence(): Int {
        return orderedImportantRejectionTypes.indexOf(type)
    }

    companion object {
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
        private val neverVisible = listOf(
            RejectionReasonType.AlreadyBuilt,
            RejectionReasonType.WonderAlreadyBuilt,
            RejectionReasonType.NationalWonderAlreadyBuilt,
            RejectionReasonType.DisabledBySetting,
            RejectionReasonType.UniqueToOtherNation,
            RejectionReasonType.ReplacedByOurUnique,
            RejectionReasonType.Obsoleted,
            RejectionReasonType.WonderBeingBuiltElsewhere,
            RejectionReasonType.RequiresTech,
            RejectionReasonType.NoSettlerForOneCityPlayers,
            RejectionReasonType.WaterUnitsInCoastalCities,
        )
    }
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

    val defaultInstance by lazy { RejectionReason(this, errorMessage, shouldShow) }
    @Pure fun toInstance() = defaultInstance

    @Pure
    fun toInstance(errorMessage: String = this.errorMessage,
        shouldShow: Boolean = this.shouldShow): RejectionReason {
        return RejectionReason(this, errorMessage, shouldShow)
    }
}

open class PerpetualConstruction(override var name: String, val description: String) :
    IConstruction {

    override fun shouldBeDisplayed(cityConstructions: CityConstructions) = isBuildable(cityConstructions)
    open fun getProductionTooltip(city: City, withIcon: Boolean = false) : String = ""
    override fun getStockpiledResourceRequirements(state: GameContext) = Counter.ZERO

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

    override fun getResourceRequirementsPerTurn(state: GameContext?) = Counter.ZERO

    override fun requiredResources(state: GameContext): Set<String> = emptySet()
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

        val stateForConditionals = city.state
        return city.civ.getMatchingUniques(UniqueType.EnablesCivWideStatProduction, stateForConditionals)
            .any { it.params[0] == stat.name }
    }
}
