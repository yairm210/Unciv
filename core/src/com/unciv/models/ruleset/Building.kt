package com.unciv.models.ruleset

import com.unciv.logic.GameInfo
import com.unciv.logic.MultiFilter
import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.*
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.extensions.getNeedMoreAmountString
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.objectdescriptions.BuildingDescriptions


class Building : RulesetStatsObject(), INonPerpetualConstruction {

    override var requiredTech: String? = null
    override var cost: Int = -1

    var maintenance = 0
    private var percentStatBonus: Stats? = null
    var specialistSlots: Counter<String> = Counter()
    fun newSpecialists(): Counter<String>  = specialistSlots

    var greatPersonPoints = Counter<String>()

    /** Extra cost percentage when purchasing */
    override var hurryCostModifier = 0
    var isWonder = false
    var isNationalWonder = false
    fun isAnyWonder() = isWonder || isNationalWonder
    var requiredBuilding: String? = null

    /** A strategic resource that will be consumed by this building */
    var requiredResource: String? = null

    /** This Building can only be built if one of these resources is nearby - it must be improved! */
    var requiredNearbyImprovedResources: List<String>? = null
    var cityStrength = 0
    var cityHealth = 0
    var replaces: String? = null
    var uniqueTo: String? = null
    var quote: String = ""
    var replacementTextForUniques = ""

    lateinit var ruleset: Ruleset

    override fun getUniqueTarget() = if (isAnyWonder()) UniqueTarget.Wonder else UniqueTarget.Building

    override fun makeLink() = if (isAnyWonder()) "Wonder/$name" else "Building/$name"

    fun getShortDescription(multiline: Boolean = false, uniqueInclusionFilter: ((Unique) -> Boolean)? = null) = BuildingDescriptions.getShortDescription(this, multiline, uniqueInclusionFilter)
    fun getDescription(city: City, showAdditionalInfo: Boolean) = BuildingDescriptions.getDescription(this, city, showAdditionalInfo)
    override fun getCivilopediaTextLines(ruleset: Ruleset) = BuildingDescriptions.getCivilopediaTextLines(this, ruleset)

    override fun isUnavailableBySettings(gameInfo: GameInfo): Boolean {
        if (super<INonPerpetualConstruction>.isUnavailableBySettings(gameInfo)) return true
        if (!gameInfo.gameParameters.nuclearWeaponsEnabled && hasUnique(UniqueType.EnablesNuclearWeapons)) return true
        return isHiddenByStartingEra(gameInfo)
    }
    private fun isHiddenByStartingEra(gameInfo: GameInfo): Boolean {
        if (!isWonder) return false
        // do not rely on this.ruleset or unit tests break
        val startingEra = gameInfo.ruleset.eras[gameInfo.gameParameters.startingEra] ?: return false
        return name in startingEra.startingObsoleteWonders
    }

    fun getStats(city: City,
                 /* By default, do not cache - if we're getting stats for only one building this isn't efficient.
                 * Only use a cache if it was sent to us from outside, which means we can use the results for other buildings.  */
                 localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Stats {
        // Calls the clone function of the NamedStats this class is derived from, not a clone function of this class
        val stats = cloneStats()
        
        val conditionalState = city.state

        for (unique in localUniqueCache.forCityGetMatchingUniques(city, UniqueType.StatsFromObject)) {
            if (!matchesFilter(unique.params[1], conditionalState)) continue
            stats.add(unique.stats)
        }

        for (unique in getMatchingUniques(UniqueType.Stats, conditionalState))
            stats.add(unique.stats)

        if (!isWonder)
            for (unique in localUniqueCache.forCityGetMatchingUniques(city, UniqueType.StatsFromBuildings)) {
                if (matchesFilter(unique.params[1], conditionalState))
                    stats.add(unique.stats)
            }
        return stats
    }

    fun getStatPercentageBonuses(city: City?, localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Stats {
        val stats = percentStatBonus?.clone() ?: Stats()
        val civInfo = city?.civ ?: return stats  // initial stats
        
        val conditionalState = city.state

        for (unique in localUniqueCache.forCivGetMatchingUniques(civInfo, UniqueType.StatPercentFromObject)) {
            if (matchesFilter(unique.params[2], conditionalState))
                stats.add(Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }

        for (unique in localUniqueCache.forCivGetMatchingUniques(civInfo, UniqueType.AllStatsPercentFromObject)) {
            if (!matchesFilter(unique.params[1], conditionalState)) continue
            for (stat in Stat.entries) {
                stats.add(stat, unique.params[0].toFloat())
            }
        }

        return stats
    }

    override fun getProductionCost(civInfo: Civilization, city: City?): Int {
        var productionCost = cost.toFloat()
        val stateForConditionals = city?.state ?: civInfo.state

        for (unique in getMatchingUniques(UniqueType.CostIncreasesWhenBuilt, stateForConditionals))
            productionCost += civInfo.civConstructions.builtItemsWithIncreasingCost[name] * unique.params[0].toInt()

        for (unique in getMatchingUniques(UniqueType.CostIncreasesPerCity, stateForConditionals))
            productionCost += civInfo.cities.size * unique.params[0].toInt()

        for (unique in getMatchingUniques(UniqueType.CostPercentageChange, stateForConditionals))
            productionCost *= unique.params[0].toPercent()

        if (civInfo.isCityState)
            productionCost *= 1.5f
        else if (civInfo.isHuman()) {
            if (!isWonder)
                productionCost *= civInfo.getDifficulty().buildingCostModifier
        } else {
            productionCost *= if (isWonder)
                civInfo.gameInfo.getDifficulty().aiWonderCostModifier
            else
                civInfo.gameInfo.getDifficulty().aiBuildingCostModifier
        }

        productionCost *= civInfo.gameInfo.speed.productionCostModifier

        return productionCost.toInt()
    }


    override fun canBePurchasedWithStat(city: City?, stat: Stat): Boolean {
        val purchaseReason = canBePurchasedWithStatReasons(null, stat)
        if (purchaseReason != PurchaseReason.UniqueAllowed && stat == Stat.Gold && isAnyWonder()) return false
        if (city == null) return purchaseReason.purchasable

        val conditionalState = city.state
        return (
            city.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0], conditionalState)
                    && city.matchesFilter(it.params[3])
                }
            || city.getMatchingUniques(UniqueType.BuyBuildingsByProductionCost, conditionalState)
                .any { it.params[1] == stat.name && matchesFilter(it.params[0], conditionalState) }
            || city.getMatchingUniques(UniqueType.BuyBuildingsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0], conditionalState)
                    && city.matchesFilter(it.params[2])
                }
            || city.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0], conditionalState)
                    && city.matchesFilter(it.params[3])
                }
            || super.canBePurchasedWithStat(city, stat)
        )
    }

    override fun getBaseBuyCost(city: City, stat: Stat): Float? {
        val conditionalState = city.state

        return sequence {
            val baseCost = super.getBaseBuyCost(city, stat)
            if (baseCost != null)
                yield(baseCost)
            yieldAll(city.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0], conditionalState)
                    && city.matchesFilter(it.params[3])
                }.map {
                    getCostForConstructionsIncreasingInPrice(
                        it.params[1].toInt(),
                        it.params[4].toInt(),
                        city.civ.civConstructions.boughtItemsWithIncreasingPrice[name]
                    ) * city.civ.gameInfo.speed.statCostModifiers[stat]!!
                }
            )
            yieldAll(city.getMatchingUniques(UniqueType.BuyBuildingsByProductionCost, conditionalState)
                .filter { it.params[1] == stat.name && matchesFilter(it.params[0], conditionalState) }
                .map { (getProductionCost(city.civ, city) * it.params[2].toInt()).toFloat() }
            )
            if (city.getMatchingUniques(UniqueType.BuyBuildingsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0], conditionalState)
                    && city.matchesFilter(it.params[2])
                }
            ) {
                yield(city.civ.getEra().baseUnitBuyCost * city.civ.gameInfo.speed.statCostModifiers[stat]!!)
            }
            yieldAll(city.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0], conditionalState)
                    && city.matchesFilter(it.params[3])
                }.map { it.params[1].toInt() * city.civ.gameInfo.speed.statCostModifiers[stat]!! }
            )
        }.minOrNull()
    }

    override fun getStatBuyCost(city: City, stat: Stat): Int? {
        var cost = getBaseBuyCost(city, stat)?.toDouble() ?: return null
        val conditionalState = city.state

        for (unique in city.getMatchingUniques(UniqueType.BuyItemsDiscount))
            if (stat.name == unique.params[0])
                cost *= unique.params[1].toPercent()

        for (unique in city.getMatchingUniques(UniqueType.BuyBuildingsDiscount)) {
            if (stat.name == unique.params[0] && matchesFilter(unique.params[1], conditionalState))
                cost *= unique.params[2].toPercent()
        }

        return (cost / 10f).toInt() * 10
    }

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        if (cityConstructions.isBeingConstructedOrEnqueued(name))
            return false
        for (unique in getMatchingUniques(UniqueType.MaxNumberBuildable)) {
            if (cityConstructions.city.civ.civConstructions.countConstructedObjects(this) >= unique.params[0].toInt())
                return false
        }

        val rejectionReasons = getRejectionReasons(cityConstructions)

        if (hasUnique(UniqueType.ShowsWhenUnbuilable, cityConstructions.city.state) &&
            rejectionReasons.none { it.isNeverVisible() })
            return true

        if (rejectionReasons.any { it.type == RejectionReasonType.RequiresBuildingInSomeCities }
                && cityConstructions.city.civ.gameInfo.gameParameters.oneCityChallenge)
            return false // You will never be able to get more cities, this building is effectively disabled

        if (rejectionReasons.none { !it.shouldShow }) return true
        return canBePurchasedWithAnyStat(cityConstructions.city)
                && rejectionReasons.all { it.type == RejectionReasonType.Unbuildable }
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): Sequence<RejectionReason> = sequence {
        val city = cityConstructions.city
        val cityCenter = city.getCenterTile()
        val civ = city.civ
        val stateForConditionals = city.state

        if (cityConstructions.isBuilt(name))
            yield(RejectionReasonType.AlreadyBuilt.toInstance())

        if (isUnavailableBySettings(civ.gameInfo)) {
            // Repeat the starting era test isHiddenBySettings already did to change the RejectionReasonType
            if (isHiddenByStartingEra(civ.gameInfo))
                yield(RejectionReasonType.WonderDisabledEra.toInstance())
            else
                yield(RejectionReasonType.DisabledBySetting.toInstance())
        }

        for (unique in uniqueObjects) {
            // skip uniques that don't have conditionals apply
            // EXCEPT for [UniqueType.OnlyAvailable] and [UniqueType.CanOnlyBeBuiltInCertainCities]
            // since they trigger (reject) only if conditionals ARE NOT met
            if (unique.type != UniqueType.OnlyAvailable && unique.type != UniqueType.CanOnlyBeBuiltWhen &&
                !unique.conditionalsApply(stateForConditionals)) continue

            when (unique.type) {
                // for buildings that are created as side effects of other things, and not directly built,
                // or for buildings that can only be bought
                UniqueType.Unbuildable ->
                    yield(RejectionReasonType.Unbuildable.toInstance())

                UniqueType.OnlyAvailable ->
                    yieldAll(notMetRejections(unique, cityConstructions))

                UniqueType.CanOnlyBeBuiltWhen ->
                    yieldAll(notMetRejections(unique, cityConstructions, true))

                UniqueType.Unavailable ->
                    yield(RejectionReasonType.ShouldNotBeDisplayed.toInstance())

                UniqueType.RequiresPopulation ->
                    if (unique.params[0].toInt() > city.population.population)
                        yield(RejectionReasonType.PopulationRequirement.toInstance(unique.text))

                UniqueType.MustBeOn ->
                    if (!cityCenter.matchesTerrainFilter(unique.params[0], civ))
                        yield(RejectionReasonType.MustBeOnTile.toInstance(unique.text))

                UniqueType.MustNotBeOn ->
                    if (cityCenter.matchesTerrainFilter(unique.params[0], civ))
                        yield(RejectionReasonType.MustNotBeOnTile.toInstance(unique.text))

                UniqueType.MustBeNextTo ->
                    if (!cityCenter.isAdjacentTo(unique.params[0], civ))
                        yield(RejectionReasonType.MustBeNextToTile.toInstance(unique.text))

                UniqueType.MustNotBeNextTo ->
                    if (cityCenter.getTilesInDistance(1).any { it.matchesFilter(unique.params[0], civ) })
                        yield(RejectionReasonType.MustNotBeNextToTile.toInstance(unique.text))

                UniqueType.MustHaveOwnedWithinTiles ->
                    if (cityCenter.getTilesInDistance(unique.params[1].toInt())
                        .none { it.matchesFilter(unique.params[0], civ) && it.getOwner() == civ }
                    )
                        yield(RejectionReasonType.MustOwnTile.toInstance(unique.text))

                UniqueType.ObsoleteWith ->
                    if (civ.tech.isResearched(unique.params[0]))
                        yield(RejectionReasonType.Obsoleted.toInstance(unique.text))

                UniqueType.MaxNumberBuildable ->
                    if (civ.civConstructions.countConstructedObjects(this@Building) >= unique.params[0].toInt())
                        yield(RejectionReasonType.MaxNumberBuildable.toInstance())

                // To be replaced with `Only available <after [Apollo Project] has been build>`
                UniqueType.SpaceshipPart -> {
                    if (!civ.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts))
                        yield(RejectionReasonType.RequiresBuildingInSomeCity.toInstance("Apollo project not built!"))
                }

                UniqueType.HiddenBeforeAmountPolicies -> {
                    if (cityConstructions.city.civ.getCompletedPolicyBranchesCount() < unique.params[0].toInt())
                        yield(RejectionReasonType.MorePolicyBranches.toInstance(unique.text))
                }

                else -> {}
            }
        }

        if (uniqueTo != null && !civ.matchesFilter(uniqueTo!!, stateForConditionals))
            yield(RejectionReasonType.UniqueToOtherNation.toInstance("Unique to $uniqueTo"))

        if (civ.cache.uniqueBuildings.any { it.replaces == name })
            yield(RejectionReasonType.ReplacedByOurUnique.toInstance())

        for (requiredTech: String in requiredTechs())
            if (!civ.tech.isResearched(requiredTech))
                yield(RejectionReasonType.RequiresTech.toInstance("$requiredTech not researched!"))

        // All Wonders
        if(isAnyWonder()) {
            if (civ.cities.any { it != cityConstructions.city && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                yield(RejectionReasonType.WonderBeingBuiltElsewhere.toInstance())

            if (civ.isCityState)
                yield(RejectionReasonType.CityStateWonder.toInstance())

            if (cityConstructions.city.isPuppet)
                yield(RejectionReasonType.PuppetWonder.toInstance())
        }

        // World Wonders
        if (isWonder) {
            if (civ.gameInfo.getCities().any { it.cityConstructions.isBuilt(name) })
                yield(RejectionReasonType.WonderAlreadyBuilt.toInstance())
        }

        // National Wonders
        if (isNationalWonder) {
            if (civ.cities.any { it.cityConstructions.isBuilt(name) })
                yield(RejectionReasonType.NationalWonderAlreadyBuilt.toInstance())
        }

        if (requiredBuilding != null && !cityConstructions.containsBuildingOrEquivalent(requiredBuilding!!)) {
            yield(RejectionReasonType.RequiresBuildingInThisCity.toInstance("Requires a [${civ.getEquivalentBuilding(requiredBuilding!!)}] in this city"))
        }

        for ((resourceName, requiredAmount) in getResourceRequirementsPerTurn(stateForConditionals)) {
            val availableAmount = cityConstructions.city.getAvailableResourceAmount(resourceName)
            if (availableAmount < requiredAmount) {
                yield(RejectionReasonType.ConsumesResources.toInstance(resourceName.getNeedMoreAmountString(requiredAmount - availableAmount)))
            }
        }
        
        // If we've already paid the unit costs, we don't need to pay it again
        if (cityConstructions.getWorkDone(name) == 0)
            for (unique in getMatchingUniques(UniqueType.CostsResources, stateForConditionals)) {
                val amount = unique.params[0].toInt()
                val resourceName = unique.params[1]
                val availableResources = cityConstructions.city.getAvailableResourceAmount(resourceName)
                if (availableResources < amount)
                    yield(RejectionReasonType.ConsumesResources.toInstance(resourceName.getNeedMoreAmountString(amount - availableResources)))
            }

        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = cityConstructions.city.getWorkableTiles()
                .any {
                    it.resource != null
                    && requiredNearbyImprovedResources!!.contains(it.resource!!)
                    && it.getOwner() == civ
                    && ((it.getUnpillagedImprovement() != null && it.tileResource.isImprovedBy(it.improvement!!)) || it.isCityCenter()
                       || (it.getUnpillagedTileImprovement()?.isGreatImprovement() == true && it.tileResource.resourceType == ResourceType.Strategic)
                    )
                }
            if (!containsResourceWithImprovement)
                yield(RejectionReasonType.RequiresNearbyResource.toInstance("Nearby $requiredNearbyImprovedResources required"))
        }
    }

    /**
     * Handles inverted conditional rejections and cumulative conditional reporting
     * See also [com.unciv.models.ruleset.unit.BaseUnit.notMetRejections]
     */
    private fun notMetRejections(unique: Unique, cityConstructions: CityConstructions, built: Boolean=false): Sequence<RejectionReason> = sequence {
        val civ = cityConstructions.city.civ
        for (conditional in unique.modifiers) {
            // We yield a rejection only when conditionals are NOT met
            if (Conditionals.conditionalApplies(unique, conditional, cityConstructions.city.state))
                continue
            when (conditional.type) {
                UniqueType.ConditionalBuildingBuiltAmount -> {
                    val building = civ.getEquivalentBuilding(conditional.params[0]).name
                    val amount = conditional.params[1].toInt()
                    val cityFilter = conditional.params[2]
                    val numberOfCities = civ.cities.count {
                        it.cityConstructions.containsBuildingOrEquivalent(building) && it.matchesFilter(cityFilter)
                    }
                    if (numberOfCities < amount)
                    {
                        yield(RejectionReasonType.RequiresBuildingInSomeCities.toInstance(
                            "Requires a [$building] in at least [$amount] of [${cityFilter}] cities" +
                                " ($numberOfCities/$numberOfCities)"))
                    }
                }
                UniqueType.ConditionalBuildingBuiltAll -> {
                    val building = civ.getEquivalentBuilding(conditional.params[0]).name
                    val cityFilter = conditional.params[1]
                    if (civ.cities.any { it.matchesFilter(cityFilter)
                            !it.isPuppet && !it.cityConstructions.containsBuildingOrEquivalent(building)
                    }) {
                        yield(RejectionReasonType.RequiresBuildingInAllCities.toInstance(
                            "Requires a [${building}] in all [${cityFilter}] cities"))
                    }
                }
                else -> {
                    if (built)
                        yield(RejectionReasonType.CanOnlyBeBuiltInSpecificCities.toInstance(unique.text))
                    else
                        yield(RejectionReasonType.ShouldNotBeDisplayed.toInstance())
                }
            }
        }
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReasons(cityConstructions).none()

    override fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat?): Boolean {
        val civInfo = cityConstructions.city.civ

        if (civInfo.gameInfo.spaceResources.contains(name)) {
            civInfo.victoryManager.currentsSpaceshipParts.add(name, 1)
            return true
        }

        cityConstructions.addBuilding(this)
        return true
    }


    private val cachedMatchesFilterResult = HashMap<String, Boolean>()

    /** Implements [UniqueParameterType.BuildingFilter] */
    fun matchesFilter(filter: String, state: StateForConditionals? = null): Boolean =
        MultiFilter.multiFilter(filter, {
            cachedMatchesFilterResult.getOrPut(it) { matchesSingleFilter(it) } ||
                state != null && hasUnique(it, state) ||
                state == null && hasTagUnique(it)
        })

    private fun matchesSingleFilter(filter: String): Boolean {
        // all cases are constants for performance
        return when (filter) {
            "all", "All" -> true
            "Building", "Buildings" -> !isAnyWonder()
            "Wonder", "Wonders" -> isAnyWonder()
            "National Wonder", "National" -> isNationalWonder
            "World Wonder", "World" -> isWonder
            else -> {
                if (filter == name) return true
                if (filter == replaces) return true
                if (::ruleset.isInitialized) // False when loading ruleset and checking buildingsToRemove
                    for (requiredTech: String in requiredTechs())
                        if (ruleset.technologies[requiredTech]?.matchesFilter(filter, multiFilter = false) == true) return true
                val stat = Stat.safeValueOf(filter)
                return (stat != null && isStatRelated(stat))
            }
        }
    }

    fun isStatRelated(stat: Stat, city: City? = null): Boolean {
        if (city != null) {
            if (getStats(city)[stat] > 0) return true
            if (getStatPercentageBonuses(city)[stat] > 0) return true
        }
        else {
            if (get(stat) > 0) return true
            if (getMatchingUniques(UniqueType.Stats).any { it.stats[stat] > 0 }) return true
            if (getStatPercentageBonuses(null)[stat] > 0) return true
        }
        if (getMatchingUniques(UniqueType.StatsFromTiles).any { it.stats[stat] > 0 }) return true
        if (getMatchingUniques(UniqueType.StatsPerPopulation).any { it.stats[stat] > 0 }) return true
        if (stat == Stat.Happiness && hasUnique(UniqueType.RemoveAnnexUnhappiness)) return true
        return false
    }

    private val _hasCreatesOneImprovementUnique by lazy {
        hasUnique(UniqueType.CreatesOneImprovement)
    }
    fun hasCreateOneImprovementUnique() = _hasCreatesOneImprovementUnique

    private var _getImprovementToCreate: TileImprovement? = null
    private fun getImprovementToCreate(ruleset: Ruleset): TileImprovement? {
        if (!hasCreateOneImprovementUnique()) return null
        if (_getImprovementToCreate == null) {
            val improvementUnique = getMatchingUniques(UniqueType.CreatesOneImprovement)
                .firstOrNull() ?: return null
            _getImprovementToCreate = ruleset.tileImprovements[improvementUnique.params[0]]
        }
        return _getImprovementToCreate
    }
    fun getImprovementToCreate(ruleset: Ruleset, civInfo: Civilization): TileImprovement? {
        val improvement = getImprovementToCreate(ruleset) ?: return null
        return civInfo.getEquivalentTileImprovement(improvement)
    }

    fun isSellable() = !isAnyWonder() && !hasUnique(UniqueType.Unsellable)

    override fun getResourceRequirementsPerTurn(state: StateForConditionals?): Counter<String> {
        val uniques = getMatchingUniques(UniqueType.ConsumesResources,
            state ?: StateForConditionals.EmptyState)
        if (uniques.none() && requiredResource == null) return Counter.ZERO
        
        val resourceRequirements = Counter<String>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in uniques)
            resourceRequirements[unique.params[1]] += unique.params[0].toInt()
        return resourceRequirements
    }
}
