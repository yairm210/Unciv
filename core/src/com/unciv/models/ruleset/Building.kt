package com.unciv.models.ruleset

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
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getConsumesAmountString
import com.unciv.ui.components.extensions.getNeedMoreAmountString
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine


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
    private var requiredResource: String? = null

    /** City can only be built if one of these resources is nearby - it must be improved! */
    var requiredNearbyImprovedResources: List<String>? = null
    var cityStrength = 0
    var cityHealth = 0
    var replaces: String? = null
    var uniqueTo: String? = null
    var quote: String = ""
    override fun getUniqueTarget() = if (isAnyWonder()) UniqueTarget.Wonder else UniqueTarget.Building
    private var replacementTextForUniques = ""

    /** Used for AlertType.WonderBuilt, and as sub-text in Nation and Tech descriptions */
    fun getShortDescription(multiline:Boolean = false): String {
        val infoList = mutableListOf<String>()
        this.clone().toString().also { if (it.isNotEmpty()) infoList += it }
        for ((key, value) in getStatPercentageBonuses(null))
            infoList += "+${value.toInt()}% ${key.name.tr()}"

        if (requiredNearbyImprovedResources != null)
            infoList += "Requires worked [" + requiredNearbyImprovedResources!!.joinToString("/") { it.tr() } + "] near city"
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques != "") infoList += replacementTextForUniques
            else infoList += getUniquesStringsWithoutDisablers()
        }
        if (cityStrength != 0) infoList += "{City strength} +$cityStrength"
        if (cityHealth != 0) infoList += "{City health} +$cityHealth"
        val separator = if (multiline) "\n" else "; "
        return infoList.joinToString(separator) { it.tr() }
    }

    /**
     * @param filterUniques If provided, include only uniques for which this function returns true.
     */
    private fun getUniquesStrings(filterUniques: ((Unique) -> Boolean)? = null) = sequence {
        val tileBonusHashmap = HashMap<String, ArrayList<String>>()
        for (unique in uniqueObjects) if (filterUniques == null || filterUniques(unique)) when {
            unique.isOfType(UniqueType.StatsFromTiles) && unique.params[2] == "in this city" -> {
                val stats = unique.params[0]
                if (!tileBonusHashmap.containsKey(stats)) tileBonusHashmap[stats] = ArrayList()
                tileBonusHashmap[stats]!!.add(unique.params[1])
            }
            else -> yield(unique.text)
        }
        for ((key, value) in tileBonusHashmap)
            yield( "[stats] from [tileFilter] tiles in this city"
                .fillPlaceholders( key,
                    // A single tileFilter will be properly translated later due to being within []
                    // advantage to not translate prematurely: FormatLine.formatUnique will recognize it
                    if (value.size == 1) value[0] else value.joinToString { it.tr() }
                ))
    }
    /**
     * @param filterUniques If provided, include only uniques for which this function returns true.
     */
    private fun getUniquesStringsWithoutDisablers(filterUniques: ((Unique) -> Boolean)? = null) = getUniquesStrings {
            !it.isHiddenToUsers()
            && filterUniques?.invoke(it) ?: true
        }

    /** used in CityScreen (ConstructionInfoTable) */
    fun getDescription(city: City, showAdditionalInfo: Boolean): String {
        val stats = getStats(city)
        val translatedLines = ArrayList<String>() // Some translations require special handling
        val isFree = city.civ.civConstructions.hasFreeBuilding(city, this)
        if (uniqueTo != null) translatedLines += if (replaces == null) "Unique to [$uniqueTo]".tr()
            else "Unique to [$uniqueTo], replaces [$replaces]".tr()
        val missingUnique = getMatchingUniques(UniqueType.RequiresBuildingInAllCities).firstOrNull()
        if (isWonder) translatedLines += "Wonder".tr()
        if (isNationalWonder) translatedLines += "National Wonder".tr()
        if (!isFree) {
            for ((resourceName, amount) in getResourceRequirementsPerTurn(StateForConditionals(city.civ, city))) {
                val available = city.getResourceAmount(resourceName)
                val resource = city.getRuleset().tileResources[resourceName] ?: continue
                val consumesString = resourceName.getConsumesAmountString(amount, resource.isStockpiled())

                translatedLines += if (showAdditionalInfo) "$consumesString ({[$available] available})".tr()
                else consumesString.tr()
            }
        }

        // Inefficient in theory. In practice, buildings seem to have only a small handful of uniques.
        val missingCities = if (missingUnique != null)
        // TODO: Unify with rejection reasons?
            city.civ.cities.filterNot {
                it.isPuppet
                        || it.cityConstructions.containsBuildingOrEquivalent(missingUnique.params[0])
            }
        else listOf()
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques != "") translatedLines += replacementTextForUniques.tr()
            else translatedLines += getUniquesStringsWithoutDisablers(
                filterUniques = if (missingCities.isEmpty()) null
                    else { unique -> !unique.isOfType(UniqueType.RequiresBuildingInAllCities) }
                    // Filter out the "Requires a [] in all cities" unique if any cities are still missing the required building, since in that case the list of cities will be appended at the end.
            ).map { it.tr() }
        }
        if (!stats.isEmpty())
            translatedLines += stats.toString()

        for ((stat, value) in getStatPercentageBonuses(city))
            if (value != 0f) translatedLines += "+${value.toInt()}% {${stat.name}}".tr()

        for ((greatPersonName, value) in greatPersonPoints)
            translatedLines += "+$value " + "[$greatPersonName] points".tr()

        for ((specialistName, amount) in newSpecialists())
            translatedLines += "+$amount " + "[$specialistName] slots".tr()

        if (requiredNearbyImprovedResources != null)
            translatedLines += "Requires worked [${requiredNearbyImprovedResources!!.joinToString("/") { it.tr() }}] near city".tr()

        if (cityStrength != 0) translatedLines += "{City strength} +$cityStrength".tr()
        if (cityHealth != 0) translatedLines += "{City health} +$cityHealth".tr()
        if (maintenance != 0 && !isFree) translatedLines += "{Maintenance cost}: $maintenance {Gold}".tr()
        if (showAdditionalInfo && missingCities.isNotEmpty()) {
            // Could be red. But IMO that should be done by enabling GDX's ColorMarkupLanguage globally instead of adding a separate label.
            translatedLines += "\n" +
                "[${city.civ.getEquivalentBuilding(missingUnique!!.params[0])}] required:".tr() +
                " " + missingCities.joinToString(", ") { it.name.tr(hideIcons = true) }
            // Can't nest square bracket placeholders inside curlies, and don't see any way to define wildcard placeholders. So run translation explicitly on base text.
        }
        return translatedLines.joinToString("\n").trim()
    }

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

    override fun makeLink() = if (isAnyWonder()) "Wonder/$name" else "Building/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        fun Float.formatSignedInt() = (if (this > 0f) "+" else "") + this.toInt().toString()

        val textList = ArrayList<FormattedLine>()

        if (isAnyWonder()) {
            textList += FormattedLine( if (isWonder) "Wonder" else "National Wonder", color="#CA4", header=3 )
        }

        if (uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
            if (replaces != null) {
                val replacesBuilding = ruleset.buildings[replaces]
                textList += FormattedLine("Replaces [$replaces]", link=replacesBuilding?.makeLink() ?: "", indent=1)
            }
        }

        if (cost > 0) {
            val stats = mutableListOf("$cost${Fonts.production}")
            if (canBePurchasedWithStat(null, Stat.Gold)) {
                stats += "${getCivilopediaGoldCost()}${Fonts.gold}"
            }
            textList += FormattedLine(stats.joinToString("/", "{Cost}: "))
        }

        if (requiredTech != null)
            textList += FormattedLine("Required tech: [$requiredTech]",
                link="Technology/$requiredTech")
        if (requiredBuilding != null)
            textList += FormattedLine("Requires [$requiredBuilding] to be built in the city",
                link="Building/$requiredBuilding")

        if (requiredResource != null) {
            textList += FormattedLine()
            val resource = ruleset.tileResources[requiredResource]
            textList += FormattedLine(
                requiredResource!!.getConsumesAmountString(1, resource!!.isStockpiled()),
                link="Resources/$requiredResource", color="#F42" )
        }

        val stats = cloneStats()
        val percentStats = getStatPercentageBonuses(null)
        val specialists = newSpecialists()
        if (uniques.isNotEmpty() || !stats.isEmpty() || !percentStats.isEmpty() || this.greatPersonPoints.isNotEmpty() || specialists.isNotEmpty())
            textList += FormattedLine()

        if (replacementTextForUniques.isNotEmpty()) {
            textList += FormattedLine(replacementTextForUniques)
        } else {
            uniquesToCivilopediaTextLines(textList, colorConsumesResources = true)
        }

        if (!stats.isEmpty()) {
            textList += FormattedLine(stats.toString())
        }

        if (!percentStats.isEmpty()) {
            for ((key, value) in percentStats) {
                if (value == 0f) continue
                textList += FormattedLine(value.formatSignedInt() + "% {$key}")
            }
        }

        for ((greatPersonName, value) in greatPersonPoints) {
            textList += FormattedLine(
                "+$value " + "[$greatPersonName] points".tr(),
                link = "Unit/$greatPersonName"
            )
        }

        if (specialists.isNotEmpty()) {
            for ((specialistName, amount) in specialists)
                textList += FormattedLine("+$amount " + "[$specialistName] slots".tr())
        }

        if (requiredNearbyImprovedResources != null) {
            textList += FormattedLine()
            textList += FormattedLine("Requires at least one of the following resources worked near the city:")
            requiredNearbyImprovedResources!!.forEach {
                textList += FormattedLine(it, indent = 1, link = "Resource/$it")
            }
        }

        if (cityStrength != 0 || cityHealth != 0 || maintenance != 0) textList += FormattedLine()
        if (cityStrength != 0) textList +=  FormattedLine("{City strength} +$cityStrength")
        if (cityHealth != 0) textList +=  FormattedLine("{City health} +$cityHealth")
        if (maintenance != 0) textList +=  FormattedLine("{Maintenance cost}: $maintenance {Gold}")

        val seeAlso = ArrayList<FormattedLine>()
        for (building in ruleset.buildings.values) {
            if (building.replaces == name
                    || building.uniqueObjects.any { unique -> unique.params.any { it == name } })
                seeAlso += FormattedLine(building.name, link=building.makeLink(), indent=1)
        }
        seeAlso += Belief.getCivilopediaTextMatching(name, ruleset, false)
        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
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
        for (unique in getMatchingUniques(UniqueType.MaxNumberBuildable)){
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
            if (unique.type != UniqueType.OnlyAvailableWhen &&
                !unique.conditionalsApply(StateForConditionals(civ, cityConstructions.city))) continue

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (unique.type) {
                // for buildings that are created as side effects of other things, and not directly built,
                // or for buildings that can only be bought
                UniqueType.Unbuildable ->
                    yield(RejectionReasonType.Unbuildable.toInstance())

                UniqueType.OnlyAvailableWhen ->
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
                    if (!cityCenter.isAdjacentTo(unique.params[0]))
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

        if (requiredTech != null && !civ.tech.isResearched(requiredTech!!))
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
