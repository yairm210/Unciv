package com.unciv.models.ruleset

import com.unciv.logic.MultiFilter
import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
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

    override fun getUniqueTarget() = if (isAnyWonder()) UniqueTarget.Wonder else UniqueTarget.Building

    override fun makeLink() = if (isAnyWonder()) "Wonder/$name" else "Building/$name"

    fun getShortDescription(multiline: Boolean = false, uniqueInclusionFilter: ((Unique) -> Boolean)? = null) = BuildingDescriptions.getShortDescription(this, multiline, uniqueInclusionFilter)
    fun getDescription(city: City, showAdditionalInfo: Boolean) = BuildingDescriptions.getDescription(this, city, showAdditionalInfo)
    override fun getCivilopediaTextLines(ruleset: Ruleset) = BuildingDescriptions.getCivilopediaTextLines(this, ruleset)

    fun getStats(city: City,
                 /* By default, do not cache - if we're getting stats for only one building this isn't efficient.
                 * Only use a cache if it was sent to us from outside, which means we can use the results for other buildings.  */
                 localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Stats {
        // Calls the clone function of the NamedStats this class is derived from, not a clone function of this class
        val stats = cloneStats()

        for (unique in localUniqueCache.forCityGetMatchingUniques(city, UniqueType.StatsFromObject)) {
            if (!matchesFilter(unique.params[1])) continue
            stats.add(unique.stats)
        }

        for (unique in getMatchingUniques(UniqueType.Stats, StateForConditionals(city.civ, city)))
            stats.add(unique.stats)

        if (!isWonder)
            for (unique in localUniqueCache.forCityGetMatchingUniques(city, UniqueType.StatsFromBuildings)) {
                if (matchesFilter(unique.params[1]))
                    stats.add(unique.stats)
            }
        return stats
    }

    fun getStatPercentageBonuses(city: City?, localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Stats {
        val stats = percentStatBonus?.clone() ?: Stats()
        val civInfo = city?.civ ?: return stats  // initial stats

        for (unique in localUniqueCache.forCivGetMatchingUniques(civInfo, UniqueType.StatPercentFromObject)) {
            if (matchesFilter(unique.params[2]))
                stats.add(Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }

        for (unique in localUniqueCache.forCivGetMatchingUniques(civInfo, UniqueType.AllStatsPercentFromObject)) {
            if (!matchesFilter(unique.params[1])) continue
            for (stat in Stat.values()) {
                stats.add(stat, unique.params[0].toFloat())
            }
        }

        return stats
    }

    override fun getProductionCost(civInfo: Civilization): Int {
        var productionCost = cost.toFloat()

        for (unique in uniqueObjects.filter { it.isOfType(UniqueType.CostIncreasesPerCity) })
            productionCost += civInfo.cities.size * unique.params[0].toInt()

        if (civInfo.isCityState())
            productionCost *= 1.5f
        if (civInfo.isHuman()) {
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
        if (stat == Stat.Gold && isAnyWonder()) return false
        if (city == null) return super.canBePurchasedWithStat(null, stat)

        val conditionalState = StateForConditionals(civInfo = city.civ, city = city)
        return (
            city.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && city.matchesFilter(it.params[3])
                }
            || city.getMatchingUniques(UniqueType.BuyBuildingsByProductionCost, conditionalState)
                .any { it.params[1] == stat.name && matchesFilter(it.params[0]) }
            || city.getMatchingUniques(UniqueType.BuyBuildingsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0])
                    && city.matchesFilter(it.params[2])
                }
            || city.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && city.matchesFilter(it.params[3])
                }
            || super.canBePurchasedWithStat(city, stat)
        )
    }

    override fun getBaseBuyCost(city: City, stat: Stat): Float? {
        val conditionalState = StateForConditionals(civInfo = city.civ, city = city)

        return sequence {
            val baseCost = super.getBaseBuyCost(city, stat)
            if (baseCost != null)
                yield(baseCost)
            yieldAll(city.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
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
                .filter { it.params[1] == stat.name && matchesFilter(it.params[0]) }
                .map { (getProductionCost(city.civ) * it.params[2].toInt()).toFloat() }
            )
            if (city.getMatchingUniques(UniqueType.BuyBuildingsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0])
                    && city.matchesFilter(it.params[2])
                }
            ) {
                yield(city.civ.getEra().baseUnitBuyCost * city.civ.gameInfo.speed.statCostModifiers[stat]!!)
            }
            yieldAll(city.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && city.matchesFilter(it.params[3])
                }.map { it.params[1].toInt() * city.civ.gameInfo.speed.statCostModifiers[stat]!! }
            )
        }.minOrNull()
    }

    override fun getStatBuyCost(city: City, stat: Stat): Int? {
        var cost = getBaseBuyCost(city, stat)?.toDouble() ?: return null

        for (unique in city.getMatchingUniques(UniqueType.BuyItemsDiscount))
            if (stat.name == unique.params[0])
                cost *= unique.params[1].toPercent()

        for (unique in city.getMatchingUniques(UniqueType.BuyBuildingsDiscount)) {
            if (stat.name == unique.params[0] && matchesFilter(unique.params[1]))
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

        if (rejectionReasons.any { it.type == RejectionReasonType.RequiresBuildingInSomeCities }
                && cityConstructions.city.civ.gameInfo.gameParameters.oneCityChallenge)
            return false // You will never be able to get more cities, this building is effectively disabled

        if (rejectionReasons.none { !it.shouldShow }) return true
        return canBePurchasedWithAnyStat(cityConstructions.city)
                && rejectionReasons.all { it.type == RejectionReasonType.Unbuildable }
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): Sequence<RejectionReason> = sequence {
        val cityCenter = cityConstructions.city.getCenterTile()
        val civ = cityConstructions.city.civ
        val ruleSet = civ.gameInfo.ruleset

        if (cityConstructions.isBuilt(name))
            yield(RejectionReasonType.AlreadyBuilt.toInstance())

        for (unique in uniqueObjects) {
            if (unique.type != UniqueType.OnlyAvailable &&
                !unique.conditionalsApply(StateForConditionals(civ, cityConstructions.city))) continue

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (unique.type) {
                // for buildings that are created as side effects of other things, and not directly built,
                // or for buildings that can only be bought
                UniqueType.Unbuildable ->
                    yield(RejectionReasonType.Unbuildable.toInstance())

                UniqueType.OnlyAvailable ->
                    if (!unique.conditionalsApply(civ, cityConstructions.city))
                        yield(RejectionReasonType.ShouldNotBeDisplayed.toInstance())

                UniqueType.RequiresPopulation ->
                    if (unique.params[0].toInt() > cityConstructions.city.population.population)
                        yield(RejectionReasonType.PopulationRequirement.toInstance(unique.text))

                UniqueType.EnablesNuclearWeapons -> if (!cityConstructions.city.civ.gameInfo.gameParameters.nuclearWeaponsEnabled)
                    yield(RejectionReasonType.DisabledBySetting.toInstance())

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
                        .none { it.matchesFilter(unique.params[0], civ) && it.getOwner() == cityConstructions.city.civ }
                    )
                        yield(RejectionReasonType.MustOwnTile.toInstance(unique.text))

                UniqueType.CanOnlyBeBuiltInCertainCities ->
                    if (!cityConstructions.city.matchesFilter(unique.params[0]))
                        yield(RejectionReasonType.CanOnlyBeBuiltInSpecificCities.toInstance(unique.text))

                UniqueType.ObsoleteWith ->
                    if (civ.tech.isResearched(unique.params[0]))
                        yield(RejectionReasonType.Obsoleted.toInstance(unique.text))

                UniqueType.HiddenWithoutReligion ->
                    if (!civ.gameInfo.isReligionEnabled())
                        yield(RejectionReasonType.DisabledBySetting.toInstance())

                UniqueType.MaxNumberBuildable ->
                    if (civ.civConstructions.countConstructedObjects(this@Building) >= unique.params[0].toInt())
                        yield(RejectionReasonType.MaxNumberBuildable.toInstance())

                // To be replaced with `Only available <after [Apollo Project] has been build>`
                UniqueType.SpaceshipPart -> {
                    if (!civ.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts))
                        yield(RejectionReasonType.RequiresBuildingInSomeCity.toInstance("Apollo project not built!"))
                }

                UniqueType.RequiresBuildingInSomeCities -> {
                    val buildingFilter = unique.params[0]
                    val numberOfCitiesRequired = unique.params[1].toInt()
                    val numberOfCitiesWithBuilding = civ.cities.count {
                        it.cityConstructions.containsBuildingOrEquivalent(buildingFilter)
                    }
                    if (numberOfCitiesWithBuilding < numberOfCitiesRequired) {
                        val equivalentBuildingFilter = if (ruleSet.buildings.containsKey(buildingFilter))
                            civ.getEquivalentBuilding(buildingFilter).name
                        else buildingFilter
                        yield(
                                // replace with civ-specific building for user
                                RejectionReasonType.RequiresBuildingInSomeCities.toInstance(
                                    unique.text.fillPlaceholders(equivalentBuildingFilter, numberOfCitiesRequired.toString()) +
                                            " ($numberOfCitiesWithBuilding/$numberOfCitiesRequired)"
                                ) )
                    }
                }

                UniqueType.RequiresBuildingInAllCities -> {
                    val filter = unique.params[0]
                    if (civ.gameInfo.ruleset.buildings.containsKey(filter)
                            && civ.cities.any {
                                !it.isPuppet && !it.cityConstructions.containsBuildingOrEquivalent(unique.params[0])
                            }
                    ) {
                        yield(
                                // replace with civ-specific building for user
                                RejectionReasonType.RequiresBuildingInAllCities.toInstance(
                                    "Requires a [${civ.getEquivalentBuilding(unique.params[0])}] in all cities"
                                )
                        )
                    }
                }

                UniqueType.HiddenBeforeAmountPolicies -> {
                    if (cityConstructions.city.civ.getCompletedPolicyBranchesCount() < unique.params[0].toInt())
                        yield(RejectionReasonType.MorePolicyBranches.toInstance(unique.text))
                }

                UniqueType.HiddenWithoutVictoryType -> {
                    if (!civ.gameInfo.gameParameters.victoryTypes.contains(unique.params[0]))
                        yield(RejectionReasonType.HiddenWithoutVictory.toInstance(unique.text))
                }

                else -> {}
            }
        }

        if (uniqueTo != null && uniqueTo != civ.civName)
            yield(RejectionReasonType.UniqueToOtherNation.toInstance("Unique to $uniqueTo"))

        if (civ.cache.uniqueBuildings.any { it.replaces == name })
            yield(RejectionReasonType.ReplacedByOurUnique.toInstance())

        for (requiredTech: String in requiredTechs())
            if (!civ.tech.isResearched(requiredTech))
                yield(RejectionReasonType.RequiresTech.toInstance("$requiredTech not researched!"))

        // Regular wonders
        if (isWonder) {
            if (civ.gameInfo.getCities().any { it.cityConstructions.isBuilt(name) })
                yield(RejectionReasonType.WonderAlreadyBuilt.toInstance())

            if (civ.cities.any { it != cityConstructions.city && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                yield(RejectionReasonType.WonderBeingBuiltElsewhere.toInstance())

            if (civ.isCityState())
                yield(RejectionReasonType.CityStateWonder.toInstance())

            val startingEra = civ.gameInfo.gameParameters.startingEra
            if (name in ruleSet.eras[startingEra]!!.startingObsoleteWonders)
                yield(RejectionReasonType.WonderDisabledEra.toInstance())
        }

        // National wonders
        if (isNationalWonder) {
            if (civ.cities.any { it.cityConstructions.isBuilt(name) })
                yield(RejectionReasonType.NationalWonderAlreadyBuilt.toInstance())

            if (civ.cities.any { it != cityConstructions.city && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                yield(RejectionReasonType.NationalWonderBeingBuiltElsewhere.toInstance())

            if (civ.isCityState())
                yield(RejectionReasonType.CityStateNationalWonder.toInstance())
        }

        if (requiredBuilding != null && !cityConstructions.containsBuildingOrEquivalent(requiredBuilding!!)) {
            yield(RejectionReasonType.RequiresBuildingInThisCity.toInstance("Requires a [${civ.getEquivalentBuilding(requiredBuilding!!)}] in this city"))
        }

        for ((resourceName, requiredAmount) in getResourceRequirementsPerTurn(
            StateForConditionals(cityConstructions.city.civ, cityConstructions.city))
        ) {
            val availableAmount = cityConstructions.city.getResourceAmount(resourceName)
            if (availableAmount < requiredAmount) {
                yield(RejectionReasonType.ConsumesResources.toInstance(resourceName.getNeedMoreAmountString(requiredAmount - availableAmount)))
            }
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

    /** Implements [UniqueParameterType.BuildingFilter] */
    fun matchesFilter(filter: String): Boolean {
        return MultiFilter.multiFilter(filter, ::matchesSingleFilter)
    }

    fun matchesSingleFilter(filter: String): Boolean {
        return when (filter) {
            "All" -> true
            name -> true
            "Building", "Buildings" -> !isAnyWonder()
            "Wonder", "Wonders" -> isAnyWonder()
            "National Wonder" -> isNationalWonder
            "World Wonder" -> isWonder
            replaces -> true
            else -> {
                if (uniques.contains(filter)) return true
                val stat = Stat.safeValueOf(filter)
                if (stat != null && isStatRelated(stat)) return true
                return false
            }
        }
    }

    fun isStatRelated(stat: Stat): Boolean {
        if (get(stat) > 0) return true
        if (getStatPercentageBonuses(null)[stat] > 0) return true
        if (getMatchingUniques(UniqueType.Stats).any { it.stats[stat] > 0 }) return true
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
    fun getImprovementToCreate(ruleset: Ruleset): TileImprovement? {
        if (!hasCreateOneImprovementUnique()) return null
        if (_getImprovementToCreate == null) {
            val improvementUnique = getMatchingUniques(UniqueType.CreatesOneImprovement)
                .firstOrNull() ?: return null
            _getImprovementToCreate = ruleset.tileImprovements[improvementUnique.params[0]]
        }
        return _getImprovementToCreate
    }

    fun isSellable() = !isAnyWonder() && !hasUnique(UniqueType.Unsellable)

    override fun getResourceRequirementsPerTurn(stateForConditionals: StateForConditionals?): Counter<String> {
        val resourceRequirements = Counter<String>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals))
            resourceRequirements[unique.params[1]] += unique.params[0].toInt()
        return resourceRequirements
    }

    override fun requiresResource(resource: String, stateForConditionals: StateForConditionals?): Boolean {
        if (getResourceRequirementsPerTurn(stateForConditionals).contains(resource)) return true
        for (unique in getMatchingUniques(UniqueType.CostsResources, stateForConditionals)) {
            if (unique.params[1] == resource) return true
        }
        return false
    }
}
