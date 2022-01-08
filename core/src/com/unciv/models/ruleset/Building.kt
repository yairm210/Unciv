package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.logic.city.*
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.*
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.toPercent
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.pow


class Building : RulesetStatsObject(), INonPerpetualConstruction {

    var requiredTech: String? = null

    var cost: Int = 0
    var maintenance = 0
    private var percentStatBonus: Stats? = null
    private var specialistSlots: Counter<String>? = null
    fun newSpecialists(): Counter<String> {
        if (specialistSlots == null) return Counter()
        // Could have old specialist values of "gold", "science" etc - change them to the new specialist names
        val counter = Counter<String>()
        for ((entry, amount) in specialistSlots!!) {
            val equivalentStat = Stat.values().firstOrNull { it.name.lowercase() == entry }

            if (equivalentStat != null)
                counter[Specialist.specialistNameByStat(equivalentStat)] = amount
            else counter[entry] = amount
        }
        return counter
    }

    var greatPersonPoints = Counter<String>()

    /** Extra cost percentage when purchasing */
    override var hurryCostModifier = 0
    var isWonder = false
    var isNationalWonder = false
    fun isAnyWonder() = isWonder || isNationalWonder
    var requiredBuilding: String? = null
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
    fun getShortDescription(ruleset: Ruleset): String { // should fit in one line
        val infoList = mutableListOf<String>()
        getStats(null).toString().also { if (it.isNotEmpty()) infoList += it }
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
    private fun getUniquesStringsWithoutDisablers(filterUniques: ((Unique) -> Boolean)? = null) = getUniquesStrings(filterUniques=filterUniques)
        .filterNot {
            it.startsWith("Hidden ") && it.endsWith(" disabled") ||
            it == UniqueType.Unbuildable.text ||
            it == Constants.hideFromCivilopediaUnique
        }

    /** used in CityScreen (CityInfoTable and ConstructionInfoTable) */
    fun getDescription(cityInfo: CityInfo?, ruleset: Ruleset): String {
        val stats = getStats(cityInfo)
        val lines = ArrayList<String>()
        val isFree = if (cityInfo == null) false
            else (name in cityInfo.civInfo.civConstructions.getFreeBuildings(cityInfo.id))
        if (uniqueTo != null) lines += if (replaces == null) "Unique to [$uniqueTo]"
            else "Unique to [$uniqueTo], replaces [$replaces]"
        val missingUnique = getMatchingUniques(UniqueType.RequiresBuildingInAllCities).firstOrNull()
        // Inefficient in theory. In practice, buildings seem to have only a small handful of uniques.
        val missingCities = if (cityInfo != null && missingUnique != null)
            // TODO: Unify with rejection reasons?
            cityInfo.civInfo.cities.filterNot {
                it.isPuppet
                || it.cityConstructions.containsBuildingOrEquivalent(missingUnique.params[0])
            }
            else listOf<CityInfo>()
        if (isWonder) lines += "Wonder"
        if (isNationalWonder) lines += "National Wonder"
        if (!isFree) {
            for ((resource, amount) in getResourceRequirements()) {
                lines += if (amount == 1) "Consumes 1 [$resource]" // For now, to keep the existing translations
                else "Consumes [$amount] [$resource]"
            }
        }
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques != "") lines += replacementTextForUniques
            else lines += getUniquesStringsWithoutDisablers(
                filterUniques=if (missingCities.isEmpty()) null
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
        if (missingCities.isNotEmpty()) {
            // Could be red. But IMO that should be done by enabling GDX's ColorMarkupLanguage globally instead of adding a separate label.
            lines += "\n" + 
                "[${cityInfo?.civInfo?.getEquivalentBuilding(missingUnique!!.params[0])}] required:".tr() + 
                " " + missingCities.joinToString(", ") { "{${it.name}}" }
            // Can't nest square bracket placeholders inside curlies, and don't see any way to define wildcard placeholders. So run translation explicitly on base text.
        }
        return lines.joinToString("\n") { it.tr() }.trim()
    }

    fun getStats(city: CityInfo?): Stats {
        // Calls the clone function of the NamedStats this class is derived from, not a clone function of this class
        val stats = cloneStats()
        if (city == null) return stats
        val civInfo = city.civInfo

        for (unique in city.getMatchingUniques(UniqueType.StatsFromObject)) {
            if (!matchesFilter(unique.params[1])) continue
            stats.add(unique.stats)
        }

        for (unique in city.getMatchingUniques("[] from every [] in cities where this religion has at least [] followers"))
            if (unique.params[2].toInt() <= city.religion.getFollowersOfMajorityReligion() && matchesFilter(unique.params[1]))
                stats.add(unique.stats)

        @Suppress("RemoveRedundantQualifierName")  // make it clearer Building inherits Stats
        for (unique in uniqueObjects)
            if (unique.placeholderText == "[] with []" && civInfo.hasResource(unique.params[1])
                    && Stats.isStats(unique.params[0]))
                stats.add(unique.stats)

        if (!isWonder)
            for (unique in city.getMatchingUniques("[] from all [] buildings")) {
                if (matchesFilter(unique.params[1]))
                    stats.add(unique.stats)
            }
        else
            for (unique in city.getMatchingUniques("[] from every Wonder"))
                stats.add(unique.stats)
        return stats
    }

    fun getStatPercentageBonuses(cityInfo: CityInfo?): Stats {
        val stats = percentStatBonus?.clone() ?: Stats()
        val civInfo = cityInfo?.civInfo ?: return stats  // initial stats

        for (unique in civInfo.getMatchingUniques("+[]% [] from every []")) {
            if (matchesFilter(unique.params[2]))
                stats.add(Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
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

        if (requiredTech != null || requiredBuilding != null || requiredBuildingInAllCities != null)
            textList += FormattedLine()
        if (requiredTech != null)
            textList += FormattedLine("Required tech: [$requiredTech]",
                link="Technology/$requiredTech")
        if (requiredBuilding != null)
            textList += FormattedLine("Requires [$requiredBuilding] to be built in the city",
                link="Building/$requiredBuilding")
        if (requiredBuildingInAllCities != null)
            textList += FormattedLine("Requires [$requiredBuildingInAllCities] to be built in all cities",
                link="Building/$requiredBuildingInAllCities")

        val resourceRequirements = getResourceRequirements()
        if (resourceRequirements.isNotEmpty()) {
            textList += FormattedLine()
            for ((resource, amount) in resourceRequirements) {
                textList += FormattedLine(
                    // the 1 variant should deprecate some time
                    if (amount == 1) "Consumes 1 [$resource]" else "Consumes [$amount] [$resource]",
                    link="Resources/$resource", color="#F42" )
            }
        }

        val stats = cloneStats()
        val percentStats = getStatPercentageBonuses(null)
        val specialists = newSpecialists()
        if (uniques.isNotEmpty() || !stats.isEmpty() || !percentStats.isEmpty() || this.greatPersonPoints.isNotEmpty() || specialists.isNotEmpty())
            textList += FormattedLine()

        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques.isNotEmpty())
                textList += FormattedLine(replacementTextForUniques)
            else
                uniqueObjects.forEach {
                    if (!it.hasFlag(UniqueFlag.HiddenToUsers))
                        textList += FormattedLine(it)
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

        for((greatPersonName, value) in greatPersonPoints) {
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
                    || building.uniqueObjects.any { unique -> unique.params.any { it ==name } })
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
            productionCost += civInfo.cities.count() * unique.params[0].toInt()

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

        productionCost *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
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
        var cost = getBaseBuyCost(cityInfo, stat)?.toDouble()
        if (cost == null) return null

        for (unique in cityInfo.getMatchingUniques("[] cost of purchasing items in cities []%"))
            if (stat.name == unique.params[0])
                cost *= unique.params[1].toPercent()

        for (unique in cityInfo.getMatchingUniques("[] cost of purchasing [] buildings []%")) {
            if (stat.name == unique.params[0] && matchesFilter(unique.params[1]))
                cost *= unique.params[2].toPercent()
        }

        return (cost / 10f).toInt() * 10
    }

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        if (cityConstructions.isBeingConstructedOrEnqueued(name))
            return false
        for (unique in getMatchingUniques(UniqueType.MaxNumberBuildable)){
            if (cityConstructions.cityInfo.civInfo.cities.count{it.cityConstructions.containsBuildingOrEquivalent(name)}>=unique.params[0].toInt())
                return false
        }

        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.none { !it.shouldShow }
            || (
                canBePurchasedWithAnyStat(cityConstructions.cityInfo)
                && rejectionReasons.all { it == RejectionReason.Unbuildable }
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
            when (unique.placeholderText) {
                // Deprecated since 3.16.11, replace with "Not displayed [...] construction without []"
                    UniqueType.NotDisplayedUnlessOtherBuildingBuilt.placeholderText ->
                        if (!cityConstructions.containsBuildingOrEquivalent(unique.params[0]))
                            rejectionReasons.add(RejectionReason.ShouldNotBeDisplayed)
                //

                UniqueType.NotDisplayedWithout.placeholderText ->
                    if (unique.params[0] in ruleSet.tileResources && !civInfo.hasResource(unique.params[0])
                        || unique.params[0] in ruleSet.buildings && !cityConstructions.containsBuildingOrEquivalent(unique.params[0])
                        || unique.params[0] in ruleSet.technologies && !civInfo.tech.isResearched(unique.params[0])
                        || unique.params[0] in ruleSet.policies && !civInfo.policies.isAdopted(unique.params[0])
                    )
                        rejectionReasons.add(RejectionReason.ShouldNotBeDisplayed)

                "Enables nuclear weapon" -> if (!cityConstructions.cityInfo.civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled)
                        rejectionReasons.add(RejectionReason.DisabledBySetting)

                UniqueType.MustBeOn.placeholderText ->
                    if (!cityCenter.matchesTerrainFilter(unique.params[0], civInfo))
                        rejectionReasons.add(RejectionReason.MustBeOnTile.apply { errorMessage = unique.text })

                UniqueType.MustNotBeOn.placeholderText ->
                    if (cityCenter.matchesTerrainFilter(unique.params[0], civInfo))
                        rejectionReasons.add(RejectionReason.MustNotBeOnTile.apply { errorMessage = unique.text })

                UniqueType.MustBeNextTo.placeholderText ->
                    if (// Fresh water is special, in that rivers are not tiles themselves but also fit the filter.
                        !(unique.params[0] == "Fresh water" && cityCenter.isAdjacentToRiver())
                        && cityCenter.getTilesInDistance(1).none { it.matchesFilter(unique.params[0], civInfo) }
                    )
                        rejectionReasons.add(RejectionReason.MustBeNextToTile.apply { errorMessage = unique.text })

                UniqueType.MustNotBeNextTo.placeholderText ->
                    if (cityCenter.getTilesInDistance(1).any { it.matchesFilter(unique.params[0], civInfo) })
                        rejectionReasons.add(RejectionReason.MustNotBeNextToTile.apply { errorMessage = unique.text })

                "Must have an owned [] within [] tiles" ->
                    if (cityCenter.getTilesInDistance(unique.params[1].toInt())
                        .none { it.matchesFilter(unique.params[0], civInfo) && it.getOwner() == cityConstructions.cityInfo.civInfo }
                    )
                        rejectionReasons.add(RejectionReason.MustOwnTile.apply { errorMessage = unique.text })

                // Deprecated since 3.16.11
                    "Can only be built in annexed cities" ->
                        if (
                            cityConstructions.cityInfo.isPuppet
                            || cityConstructions.cityInfo.civInfo.civName == cityConstructions.cityInfo.foundingCiv
                        )
                            rejectionReasons.add(RejectionReason.CanOnlyBeBuiltInSpecificCities.apply { errorMessage = unique.text })
                //

                "Can only be built []" ->
                    if (!cityConstructions.cityInfo.matchesFilter(unique.params[0]))
                        rejectionReasons.add(RejectionReason.CanOnlyBeBuiltInSpecificCities.apply { errorMessage = unique.text })

                "Obsolete with []" ->
                    if (civInfo.tech.isResearched(unique.params[0]))
                        rejectionReasons.add(RejectionReason.Obsoleted.apply { errorMessage = unique.text })

                UniqueType.HiddenWithoutReligion.text ->
                    if (!civInfo.gameInfo.isReligionEnabled())
                        rejectionReasons.add(RejectionReason.DisabledBySetting)

                UniqueType.MaxNumberBuildable.placeholderText ->
                    if (civInfo.cities.count {
                                it.cityConstructions.containsBuildingOrEquivalent(name) ||
                                        it.cityConstructions.isBeingConstructedOrEnqueued(name)
                            }
                            >= unique.params[0].toInt()) {
                        rejectionReasons.add(RejectionReason.MaxNumberBuildable)
                }
            }
        }

        if (uniqueTo != null && uniqueTo != civInfo.civName)
            rejectionReasons.add(RejectionReason.UniqueToOtherNation.apply { errorMessage = "Unique to $uniqueTo"})

        if (civInfo.gameInfo.ruleSet.buildings.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            rejectionReasons.add(RejectionReason.ReplacedByOurUnique)

        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!))
            rejectionReasons.add(RejectionReason.RequiresTech.apply { "$requiredTech not researched!"})

        for (unique in uniqueObjects) {
            if (unique.placeholderText != "Unlocked with []" && unique.placeholderText != "Requires []") continue
            val filter = unique.params[0]
            when {
                ruleSet.technologies.contains(filter) ->
                    if (!civInfo.tech.isResearched(filter))
                        rejectionReasons.add(RejectionReason.RequiresTech.apply { errorMessage = unique.text })
                ruleSet.policies.contains(filter) ->
                    if (!civInfo.policies.isAdopted(filter))
                        rejectionReasons.add(RejectionReason.RequiresPolicy.apply { errorMessage = unique.text })
                ruleSet.eras.contains(filter) ->
                    if (civInfo.getEraNumber() < ruleSet.eras[filter]!!.eraNumber)
                        rejectionReasons.add(RejectionReason.UnlockedWithEra.apply { errorMessage = unique.text })
                ruleSet.buildings.contains(filter) ->
                    if (civInfo.cities.none { it.cityConstructions.containsBuildingOrEquivalent(filter) })
                        rejectionReasons.add(RejectionReason.RequiresBuildingInSomeCity.apply { errorMessage = unique.text })
            }
        }

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

            if (requiredBuildingInAllCities != null && civInfo.gameInfo.ruleSet.buildings[requiredBuildingInAllCities!!] == null) {
                rejectionReasons.add(RejectionReason.InvalidRequiredBuilding)
            } else {
                if (requiredBuildingInAllCities != null
                    && civInfo.cities.any {
                        !it.isPuppet && !it.cityConstructions
                        .containsBuildingOrEquivalent(requiredBuildingInAllCities!!)
                    }
                ) {
                    rejectionReasons.add(RejectionReason.RequiresBuildingInAllCities
                        .apply { errorMessage = "Requires a [${civInfo.getEquivalentBuilding(requiredBuildingInAllCities!!)}] in all cities"})
                }

                if (civInfo.cities.any { it != cityConstructions.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                    rejectionReasons.add(RejectionReason.NationalWonderBeingBuiltElsewhere)

                if (civInfo.isCityState())
                    rejectionReasons.add(RejectionReason.CityStateNationalWonder)
            }
        }

        if (hasUnique(UniqueType.SpaceshipPart)) {
            if (!civInfo.hasUnique("Enables construction of Spaceship parts"))
                rejectionReasons.add(
                    RejectionReason.RequiresBuildingInSomeCity.apply { errorMessage = "Apollo project not built!" }
                )

            if (civInfo.victoryManager.unconstructedSpaceshipParts()[name] == 0)
                rejectionReasons.add(RejectionReason.ReachedBuildCap)
        }

        for (unique in uniqueObjects) when (unique.type) {
            UniqueType.RequiresAnotherBuilding -> {
                val filter = unique.params[0]
                if (civInfo.gameInfo.ruleSet.buildings.containsKey(filter) && !cityConstructions.containsBuildingOrEquivalent(filter))
                    rejectionReasons.add(
                        // replace with civ-specific building for user
                        RejectionReason.RequiresBuildingInThisCity.apply { errorMessage = "Requires a [${civInfo.getEquivalentBuilding(filter)}] in this city" }
                    )
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
                        RejectionReason.RequiresBuildingInAllCities.apply {
                            errorMessage = "Requires a [${civInfo.getEquivalentBuilding(unique.params[0])}] in all cities"
                        }
                    )
                }
            }

            UniqueType.HiddenBeforeAmountPolicies -> {
                if (cityConstructions.cityInfo.civInfo.getCompletedPolicyBranchesCount() < unique.params[0].toInt())
                    rejectionReasons.add(RejectionReason.MorePolicyBranches.apply { errorMessage = unique.text })
            }
            UniqueType.HiddenWithoutVictoryType -> {
                if (!civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.valueOf(unique.params[0])))
                    rejectionReasons.add(RejectionReason.HiddenWithoutVictory.apply { errorMessage = unique.text })
            }
        }

        if (requiredBuilding != null && !cityConstructions.containsBuildingOrEquivalent(requiredBuilding!!)) {
            if (!civInfo.gameInfo.ruleSet.buildings.containsKey(requiredBuilding!!)) {
                rejectionReasons.add(
                    RejectionReason.InvalidRequiredBuilding
                        .apply { errorMessage = "Requires a [${requiredBuilding}] in this city, which doesn't seem to exist in this ruleset!" }
                )
            } else {
                rejectionReasons.add(
                    RejectionReason.RequiresBuildingInThisCity.apply { errorMessage = "Requires a [${civInfo.getEquivalentBuilding(requiredBuilding!!)}] in this city"}
                )
            }
        }
        val cannotBeBuiltWithUnique = uniqueObjects
            .firstOrNull { it.isOfType(UniqueType.CannotBeBuiltWith) }
        if (cannotBeBuiltWithUnique != null && cityConstructions.isBuilt(cannotBeBuiltWithUnique.params[0]))
            rejectionReasons.add(RejectionReason.CannotBeBuiltWith.apply { errorMessage = cannotBeBuiltWithUnique.text })

        for ((resource, amount) in getResourceRequirements())
            if (civInfo.getCivResourcesByName()[resource]!! < amount) {
                rejectionReasons.add(RejectionReason.ConsumesResources.apply {
                    errorMessage = "Consumes [$amount] [$resource]"
                })
            }

        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = cityConstructions.cityInfo.getWorkableTiles()
                .any {
                    it.resource != null
                    && requiredNearbyImprovedResources!!.contains(it.resource!!)
                    && it.getOwner() == civInfo
                    && (it.tileResource.improvement == it.improvement || it.isCityCenter()
                       || (it.getTileImprovement()?.isGreatImprovement() == true && it.tileResource.resourceType == ResourceType.Strategic)
                    )
                }
            if (!containsResourceWithImprovement)
                rejectionReasons.add(RejectionReason.RequiresNearbyResource.apply { errorMessage = "Nearby $requiredNearbyImprovedResources required" })
        }

        return rejectionReasons
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReasons(cityConstructions).isEmpty()

    override fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat?): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo

        if (hasUnique(UniqueType.SpaceshipPart)) {
            civInfo.victoryManager.currentsSpaceshipParts.add(name, 1)
            return true
        }

        cityConstructions.addBuilding(name)

        val improvement = getImprovement(civInfo.gameInfo.ruleSet)
        if (improvement != null) {
            val tileWithImprovement = cityConstructions.cityInfo.getTiles().firstOrNull { it.improvementInProgress == improvement.name }
            if (tileWithImprovement != null) {
                tileWithImprovement.turnsToImprovement = 0
                tileWithImprovement.improvementInProgress = null
                tileWithImprovement.improvement = improvement.name
            }
        }

        // "Provides a free [buildingName] [cityFilter]"
        cityConstructions.addFreeBuildings()

        for (unique in uniqueObjects)
            UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo, cityConstructions.cityInfo)

        if ("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)" in uniques)
            civInfo.updateHasActiveGreatWall()

        // Korean unique - apparently gives the same as the research agreement
        if (science > 0 && civInfo.hasUnique("Receive a tech boost when scientific buildings/wonders are built in capital"))
            civInfo.tech.addScience(civInfo.tech.scienceOfLast8Turns.sum() / 8)

        cityConstructions.cityInfo.cityStats.update() // new building, new stats
        civInfo.updateDetailedCivResources() // this building/unit could be a resource-requiring one
        civInfo.transients().updateCitiesConnectedToCapital(false) // could be a connecting building, like a harbor

        return true
    }

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
                val stat = Stat.values().firstOrNull { it.name == filter }
                if (stat != null && isStatRelated(stat)) return true
                return false
            }
        }
    }

    fun isStatRelated(stat: Stat): Boolean {
        if (get(stat) > 0) return true
        if (getStatPercentageBonuses(null)[stat] > 0) return true
        if (uniqueObjects.any { it.isOfType(UniqueType.StatsPerPopulation) && it.stats[stat] > 0 }) return true
        if (uniqueObjects.any { it.isOfType(UniqueType.StatsFromTiles) && it.stats[stat] > 0 }) return true
        return false
    }

    fun getImprovement(ruleset: Ruleset): TileImprovement? {
        val improvementUnique = uniqueObjects
            .firstOrNull { it.placeholderText == "Creates a [] improvement on a specific tile" }
            ?: return null
        return ruleset.tileImprovements[improvementUnique.params[0]]
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
