package com.unciv.models.ruleset

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.city.RejectionReason
import com.unciv.logic.city.RejectionReasons
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.getConsumesAmountString
import com.unciv.ui.utils.extensions.getNeedMoreAmountString
import com.unciv.ui.utils.extensions.toPercent
import kotlin.math.pow


class Building : RulesetStatsObject(), INonPerpetualConstruction {

    override var requiredTech: String? = null
    override var cost: Int = 0

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
    @Deprecated("As of 3.18.15 - replace with RequiresBuildingInAllCities unique")
    var requiredBuildingInAllCities: String? = null

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
    fun getShortDescription(): String { // should fit in one line
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
        return infoList.joinToString("; ") { it.tr() }
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
            unique.isOfType(UniqueType.ConsumesResources) -> Unit    // skip these,
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
            !it.hasFlag(UniqueFlag.HiddenToUsers)
            && filterUniques?.invoke(it) ?: true
        }

    /** used in CityScreen (CityInfoTable and ConstructionInfoTable) */
    fun getDescription(cityInfo: CityInfo, showAdditionalInfo: Boolean): String {
        val stats = getStats(cityInfo)
        val lines = ArrayList<String>()
        val isFree = name in cityInfo.civInfo.civConstructions.getFreeBuildings(cityInfo.id)
        if (uniqueTo != null) lines += if (replaces == null) "Unique to [$uniqueTo]"
            else "Unique to [$uniqueTo], replaces [$replaces]"
        val missingUnique = getMatchingUniques(UniqueType.RequiresBuildingInAllCities).firstOrNull()
        if (isWonder) lines += "Wonder"
        if (isNationalWonder) lines += "National Wonder"
        if (!isFree) {
            val availableResources = if (!showAdditionalInfo) emptyMap()
                else cityInfo.civInfo.getCivResources().associate { it.resource.name to it.amount }
            for ((resource, amount) in getResourceRequirements()) {
                val available = availableResources[resource] ?: 0
                lines += if (showAdditionalInfo)
                        "{${resource.getConsumesAmountString(amount)}} ({[$available] available})"
                    else resource.getConsumesAmountString(amount)
            }
        }

        // Inefficient in theory. In practice, buildings seem to have only a small handful of uniques.
        val missingCities = if (missingUnique != null)
        // TODO: Unify with rejection reasons?
            cityInfo.civInfo.cities.filterNot {
                it.isPuppet
                        || it.cityConstructions.containsBuildingOrEquivalent(missingUnique.params[0])
            }
        else listOf()
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques != "") lines += replacementTextForUniques
            else lines += getUniquesStringsWithoutDisablers(
                filterUniques = if (missingCities.isEmpty()) null
                    else { unique -> !unique.isOfType(UniqueType.RequiresBuildingInAllCities) }
                    // Filter out the "Requires a [] in all cities" unique if any cities are still missing the required building, since in that case the list of cities will be appended at the end.
            )
        }
        if (!stats.isEmpty())
            lines += stats.toString()

        for ((stat, value) in getStatPercentageBonuses(cityInfo))
            if (value != 0f) lines += "+${value.toInt()}% {${stat.name}}"

        for ((greatPersonName, value) in greatPersonPoints)
            lines += "+$value " + "[$greatPersonName] points".tr()

        for ((specialistName, amount) in newSpecialists())
            lines += "+$amount " + "[$specialistName] slots".tr()

        if (requiredNearbyImprovedResources != null)
            lines += "Requires worked [" + requiredNearbyImprovedResources!!.joinToString("/") { it.tr() } + "] near city"

        if (cityStrength != 0) lines += "{City strength} +$cityStrength"
        if (cityHealth != 0) lines += "{City health} +$cityHealth"
        if (maintenance != 0 && !isFree) lines += "{Maintenance cost}: $maintenance {Gold}"
        if (showAdditionalInfo && missingCities.isNotEmpty()) {
            // Could be red. But IMO that should be done by enabling GDX's ColorMarkupLanguage globally instead of adding a separate label.
            lines += "\n" +
                "[${cityInfo.civInfo.getEquivalentBuilding(missingUnique!!.params[0])}] required:".tr() +
                " " + missingCities.joinToString(", ") { "{${it.name}}" }
            // Can't nest square bracket placeholders inside curlies, and don't see any way to define wildcard placeholders. So run translation explicitly on base text.
        }
        return lines.joinToString("\n") { it.tr() }.trim()
    }

    fun getStats(city: CityInfo,
                 /* By default, do not cache - if we're getting stats for only one building this isn't efficient.
                 * Only use a cache if it was sent to us from outside, which means we can use the results for other buildings.  */
                 localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Stats {
        // Calls the clone function of the NamedStats this class is derived from, not a clone function of this class
        val stats = cloneStats()

        for (unique in localUniqueCache.get("StatsFromObject", city.getMatchingUniques(UniqueType.StatsFromObject))) {
            if (!matchesFilter(unique.params[1])) continue
            stats.add(unique.stats)
        }

        if (!isWonder)
            for (unique in localUniqueCache.get("StatsFromBuildings", city.getMatchingUniques(UniqueType.StatsFromBuildings))) {
                if (matchesFilter(unique.params[1]))
                    stats.add(unique.stats)
            }
        return stats
    }

    fun getStatPercentageBonuses(cityInfo: CityInfo?, localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Stats {
        val stats = percentStatBonus?.clone() ?: Stats()
        val civInfo = cityInfo?.civInfo ?: return stats  // initial stats

        for (unique in localUniqueCache.get("StatPercentFromObject", civInfo.getMatchingUniques(UniqueType.StatPercentFromObject))) {
            if (matchesFilter(unique.params[2]))
                stats.add(Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }

        for (unique in localUniqueCache.get("AllStatsPercentFromObject", civInfo.getMatchingUniques(UniqueType.AllStatsPercentFromObject))) {
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
                // We need what INonPerpetualConstruction.getBaseGoldCost calculates but without any game- or civ-specific modifiers
                val buyCost = (30.0 * cost.toFloat().pow(0.75f) * hurryCostModifier.toPercent()).toInt() / 10 * 10
                stats += "$buyCost${Fonts.gold}"
            }
            textList += FormattedLine(stats.joinToString(", ", "{Cost}: "))
        }

        if (requiredTech != null)
            textList += FormattedLine("Required tech: [$requiredTech]",
                link="Technology/$requiredTech")
        if (requiredBuilding != null)
            textList += FormattedLine("Requires [$requiredBuilding] to be built in the city",
                link="Building/$requiredBuilding")

        val resourceRequirements = getResourceRequirements()
        if (resourceRequirements.isNotEmpty()) {
            textList += FormattedLine()
            for ((resource, amount) in resourceRequirements) {
                textList += FormattedLine(
                    // the 1 variant should deprecate some time
                    resource.getConsumesAmountString(amount),
                    link="Resources/$resource", color="#F42" )
            }
        }

        val stats = cloneStats()
        val percentStats = getStatPercentageBonuses(null)
        val specialists = newSpecialists()
        if (uniques.isNotEmpty() || !stats.isEmpty() || !percentStats.isEmpty() || this.greatPersonPoints.isNotEmpty() || specialists.isNotEmpty())
            textList += FormattedLine()

        if (replacementTextForUniques.isNotEmpty()) {
            textList += FormattedLine(replacementTextForUniques)
        } else if (uniques.isNotEmpty()) {
            for (unique in uniqueObjects) {
                if (unique.hasFlag(UniqueFlag.HiddenToUsers)) continue
                if (unique.type == UniqueType.ConsumesResources) continue  // already shown from getResourceRequirements
                textList += FormattedLine(unique)
            }
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

    override fun getProductionCost(civInfo: CivilizationInfo): Int {
        var productionCost = cost.toFloat()

        for (unique in uniqueObjects.filter { it.isOfType(UniqueType.CostIncreasesPerCity) })
            productionCost += civInfo.cities.size * unique.params[0].toInt()

        if (civInfo.isCityState())
            productionCost *= 1.5f
        if (civInfo.isPlayerCivilization()) {
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


    override fun canBePurchasedWithStat(cityInfo: CityInfo?, stat: Stat): Boolean {
        if (stat == Stat.Gold && isAnyWonder()) return false
        if (cityInfo == null) return super.canBePurchasedWithStat(cityInfo, stat)

        val conditionalState = StateForConditionals(civInfo = cityInfo.civInfo, cityInfo = cityInfo)
        return (
            cityInfo.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }
            || cityInfo.getMatchingUniques(UniqueType.BuyBuildingsByProductionCost, conditionalState)
                .any { it.params[1] == stat.name && matchesFilter(it.params[0]) }
            || cityInfo.getMatchingUniques(UniqueType.BuyBuildingsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[2])
                }
            || cityInfo.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }
            || return super.canBePurchasedWithStat(cityInfo, stat)
        )
    }

    override fun getBaseBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        if (stat == Stat.Gold) return getBaseGoldCost(cityInfo.civInfo).toInt()
        val conditionalState = StateForConditionals(civInfo = cityInfo.civInfo, cityInfo = cityInfo)

        return sequence {
            val baseCost = super.getBaseBuyCost(cityInfo, stat)
            if (baseCost != null)
                yield(baseCost)
            yieldAll(cityInfo.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }.map {
                    getCostForConstructionsIncreasingInPrice(
                        it.params[1].toInt(),
                        it.params[4].toInt(),
                        cityInfo.civInfo.civConstructions.boughtItemsWithIncreasingPrice[name] ?: 0
                    )
                }
            )
            yieldAll(cityInfo.getMatchingUniques(UniqueType.BuyBuildingsByProductionCost, conditionalState)
                .filter { it.params[1] == stat.name && matchesFilter(it.params[0]) }
                .map { getProductionCost(cityInfo.civInfo) * it.params[2].toInt() }
            )
            if (cityInfo.getMatchingUniques(UniqueType.BuyBuildingsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[2])
                }
            ) {
                yield(cityInfo.civInfo.getEra().baseUnitBuyCost)
            }
            yieldAll(cityInfo.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }.map { it.params[1].toInt() }
            )
        }.minOrNull()
    }

    override fun getStatBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        var cost = getBaseBuyCost(cityInfo, stat)?.toDouble() ?: return null

        for (unique in cityInfo.getMatchingUniques(UniqueType.BuyItemsDiscount))
            if (stat.name == unique.params[0])
                cost *= unique.params[1].toPercent()

        for (unique in cityInfo.getMatchingUniques(UniqueType.BuyBuildingsDiscount)) {
            if (stat.name == unique.params[0] && matchesFilter(unique.params[1]))
                cost *= unique.params[2].toPercent()
        }

        return (cost / 10f).toInt() * 10
    }

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        if (cityConstructions.isBeingConstructedOrEnqueued(name))
            return false
        for (unique in getMatchingUniques(UniqueType.MaxNumberBuildable)){
            if (cityConstructions.cityInfo.civInfo.civConstructions.countConstructedObjects(this) >= unique.params[0].toInt())
                return false
        }

        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.none { !it.shouldShow }
            || (
                canBePurchasedWithAnyStat(cityConstructions.cityInfo)
                && rejectionReasons.all { it.rejectionReason == RejectionReason.Unbuildable }
            )
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): RejectionReasons {
        val rejectionReasons = RejectionReasons()
        val cityCenter = cityConstructions.cityInfo.getCenterTile()
        val civInfo = cityConstructions.cityInfo.civInfo
        val ruleSet = civInfo.gameInfo.ruleSet

        if (cityConstructions.isBuilt(name))
            rejectionReasons.add(RejectionReason.AlreadyBuilt)
        // for buildings that are created as side effects of other things, and not directly built,
        // or for buildings that can only be bought
        if (hasUnique(UniqueType.Unbuildable))
            rejectionReasons.add(RejectionReason.Unbuildable)

        for (unique in uniqueObjects) {
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (unique.type) {
                UniqueType.OnlyAvailableWhen->
                    if (!unique.conditionalsApply(civInfo, cityConstructions.cityInfo))
                        rejectionReasons.add(RejectionReason.ShouldNotBeDisplayed)

                UniqueType.EnablesNuclearWeapons -> if (!cityConstructions.cityInfo.civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled)
                        rejectionReasons.add(RejectionReason.DisabledBySetting)

                UniqueType.MustBeOn ->
                    if (!cityCenter.matchesTerrainFilter(unique.params[0], civInfo))
                        rejectionReasons.add(RejectionReason.MustBeOnTile.toInstance(unique.text))

                UniqueType.MustNotBeOn ->
                    if (cityCenter.matchesTerrainFilter(unique.params[0], civInfo))
                        rejectionReasons.add(RejectionReason.MustNotBeOnTile.toInstance(unique.text))

                UniqueType.MustBeNextTo ->
                    if (!cityCenter.isAdjacentTo(unique.params[0]))
                        rejectionReasons.add(RejectionReason.MustBeNextToTile.toInstance(unique.text))

                UniqueType.MustNotBeNextTo ->
                    if (cityCenter.getTilesInDistance(1).any { it.matchesFilter(unique.params[0], civInfo) })
                        rejectionReasons.add(RejectionReason.MustNotBeNextToTile.toInstance(unique.text))

                UniqueType.MustHaveOwnedWithinTiles ->
                    if (cityCenter.getTilesInDistance(unique.params[1].toInt())
                        .none { it.matchesFilter(unique.params[0], civInfo) && it.getOwner() == cityConstructions.cityInfo.civInfo }
                    )
                        rejectionReasons.add(RejectionReason.MustOwnTile.toInstance(unique.text))

                UniqueType.CanOnlyBeBuiltInCertainCities ->
                    if (!cityConstructions.cityInfo.matchesFilter(unique.params[0]))
                        rejectionReasons.add(RejectionReason.CanOnlyBeBuiltInSpecificCities.toInstance(unique.text))

                UniqueType.ObsoleteWith ->
                    if (civInfo.tech.isResearched(unique.params[0]))
                        rejectionReasons.add(RejectionReason.Obsoleted.toInstance(unique.text))

                UniqueType.HiddenWithoutReligion ->
                    if (!civInfo.gameInfo.isReligionEnabled())
                        rejectionReasons.add(RejectionReason.DisabledBySetting)

                UniqueType.MaxNumberBuildable ->
                    if (civInfo.civConstructions.countConstructedObjects(this) >= unique.params[0].toInt())
                        rejectionReasons.add(RejectionReason.MaxNumberBuildable)

                // To be replaced with `Only available <after [Apollo Project] has been build>`
                UniqueType.SpaceshipPart -> {
                    if (!civInfo.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts))
                        rejectionReasons.add(RejectionReason.RequiresBuildingInSomeCity.toInstance("Apollo project not built!"))
                }

                UniqueType.RequiresBuildingInSomeCities -> {
                    val buildingName = unique.params[0]
                    val numberOfCitiesRequired = unique.params[1].toInt()
                    val numberOfCitiesWithBuilding = civInfo.cities.count {
                        it.cityConstructions.containsBuildingOrEquivalent(buildingName)
                    }
                    if (numberOfCitiesWithBuilding < numberOfCitiesRequired) {
                        val equivalentBuildingName = civInfo.getEquivalentBuilding(buildingName).name
                        rejectionReasons.add(
                                // replace with civ-specific building for user
                                RejectionReason.RequiresBuildingInAllCities.toInstance(
                                    unique.text.fillPlaceholders(equivalentBuildingName, numberOfCitiesRequired.toString()) +
                                            " ($numberOfCitiesWithBuilding/$numberOfCitiesRequired)"
                                ) )
                    }
                }

                UniqueType.RequiresBuildingInAllCities -> {
                    val filter = unique.params[0]
                    if (civInfo.gameInfo.ruleSet.buildings.containsKey(filter)
                            && civInfo.cities.any {
                                !it.isPuppet && !it.cityConstructions.containsBuildingOrEquivalent(unique.params[0])
                            }
                    ) {
                        rejectionReasons.add(
                                // replace with civ-specific building for user
                                RejectionReason.RequiresBuildingInAllCities.toInstance(
                                    "Requires a [${civInfo.getEquivalentBuilding(unique.params[0])}] in all cities"
                                )
                        )
                    }
                }

                UniqueType.HiddenBeforeAmountPolicies -> {
                    if (cityConstructions.cityInfo.civInfo.getCompletedPolicyBranchesCount() < unique.params[0].toInt())
                        rejectionReasons.add(RejectionReason.MorePolicyBranches.toInstance(unique.text))
                }

                UniqueType.HiddenWithoutVictoryType -> {
                    if (!civInfo.gameInfo.gameParameters.victoryTypes.contains(unique.params[0]))
                        rejectionReasons.add(RejectionReason.HiddenWithoutVictory.toInstance(unique.text))
                }

                else -> {}
            }
        }

        if (uniqueTo != null && uniqueTo != civInfo.civName)
            rejectionReasons.add(RejectionReason.UniqueToOtherNation.toInstance("Unique to $uniqueTo"))

        if (civInfo.gameInfo.ruleSet.buildings.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            rejectionReasons.add(RejectionReason.ReplacedByOurUnique)

        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!))
            rejectionReasons.add(RejectionReason.RequiresTech.toInstance("$requiredTech not researched!"))

        // Regular wonders
        if (isWonder) {
            if (civInfo.gameInfo.getCities().any { it.cityConstructions.isBuilt(name) })
                rejectionReasons.add(RejectionReason.WonderAlreadyBuilt)

            if (civInfo.cities.any { it != cityConstructions.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                rejectionReasons.add(RejectionReason.WonderBeingBuiltElsewhere)

            if (civInfo.isCityState())
                rejectionReasons.add(RejectionReason.CityStateWonder)

            val startingEra = civInfo.gameInfo.gameParameters.startingEra
            if (name in ruleSet.eras[startingEra]!!.startingObsoleteWonders)
                rejectionReasons.add(RejectionReason.WonderDisabledEra)
        }

        // National wonders
        if (isNationalWonder) {
            if (civInfo.cities.any { it.cityConstructions.isBuilt(name) })
                rejectionReasons.add(RejectionReason.NationalWonderAlreadyBuilt)

            if (civInfo.cities.any { it != cityConstructions.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                rejectionReasons.add(RejectionReason.NationalWonderBeingBuiltElsewhere)

            if (civInfo.isCityState())
                rejectionReasons.add(RejectionReason.CityStateNationalWonder)
        }

        if (requiredBuilding != null && !cityConstructions.containsBuildingOrEquivalent(requiredBuilding!!)) {
            rejectionReasons.add(RejectionReason.RequiresBuildingInThisCity.toInstance("Requires a [${civInfo.getEquivalentBuilding(requiredBuilding!!)}] in this city"))
        }

        for ((resource, requiredAmount) in getResourceRequirements()) {
            val availableAmount = civInfo.getCivResourcesByName()[resource]!!
            if (availableAmount < requiredAmount) {
                rejectionReasons.add(RejectionReason.ConsumesResources.toInstance(resource.getNeedMoreAmountString(requiredAmount - availableAmount)))
            }
        }

        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = cityConstructions.cityInfo.getWorkableTiles()
                .any {
                    it.resource != null
                    && requiredNearbyImprovedResources!!.contains(it.resource!!)
                    && it.getOwner() == civInfo
                    && ((it.improvement != null && it.tileResource.isImprovedBy(it.improvement!!)) || it.isCityCenter()
                       || (it.getTileImprovement()?.isGreatImprovement() == true && it.tileResource.resourceType == ResourceType.Strategic)
                    )
                }
            if (!containsResourceWithImprovement)
                rejectionReasons.add(RejectionReason.RequiresNearbyResource.toInstance("Nearby $requiredNearbyImprovedResources required"))
        }

        return rejectionReasons
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReasons(cityConstructions).isEmpty()

    override fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat?): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo

        if (civInfo.gameInfo.spaceResources.contains(name)) {
            civInfo.victoryManager.currentsSpaceshipParts.add(name, 1)
            return true
        }

        if (cityHealth > 0) {
            // city built a building that increases health so add a portion of this added health that is
            // proportional to the city's current health
            cityConstructions.cityInfo.health += (cityHealth.toFloat() * cityConstructions.cityInfo.health.toFloat() / cityConstructions.cityInfo.getMaxHealth().toFloat()).toInt()
        }

        cityConstructions.addBuilding(name)

        /** Support for [UniqueType.CreatesOneImprovement] */
        cityConstructions.applyCreateOneImprovement(this)

        // "Provides a free [buildingName] [cityFilter]"
        cityConstructions.addFreeBuildings()

        for (unique in uniqueObjects)
            UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo, cityConstructions.cityInfo)

        if ("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)" in uniques)
            civInfo.updateHasActiveGreatWall()

        // Korean unique - apparently gives the same as the research agreement
        if (science > 0 && civInfo.hasUnique(UniqueType.TechBoostWhenScientificBuildingsBuiltInCapital))
            civInfo.tech.addScience(civInfo.tech.scienceOfLast8Turns.sum() / 8)

        cityConstructions.cityInfo.cityStats.update() // new building, new stats
        civInfo.updateDetailedCivResources() // this building/unit could be a resource-requiring one
        civInfo.transients().updateCitiesConnectedToCapital(false) // could be a connecting building, like a harbor

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

    override fun getResourceRequirements(): HashMap<String, Int> = resourceRequirementsInternal

    private val resourceRequirementsInternal: HashMap<String, Int> by lazy {
        val resourceRequirements = HashMap<String, Int>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in uniqueObjects)
            if (unique.isOfType(UniqueType.ConsumesResources))
                resourceRequirements[unique.params[1]] = unique.params[0].toInt()
        resourceRequirements
    }

    override fun requiresResource(resource: String): Boolean {
        if (requiredResource == resource) return true
        for (unique in getMatchingUniques(UniqueType.ConsumesResources)) {
            if (unique.params[1] == resource) return true
        }
        return false
    }
}
