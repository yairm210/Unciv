package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.toPercent
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class Building : NamedStats(), INonPerpetualConstruction, ICivilopediaText {

    var requiredTech: String? = null

    var cost: Int = 0
    var maintenance = 0
    private var percentStatBonus: Stats? = null
    var specialistSlots: Counter<String>? = null
    fun newSpecialists(): Counter<String> {
        if (specialistSlots == null) return Counter()
        // Could have old specialist values of "gold", "science" etc - change them to the new specialist names
        val counter = Counter<String>()
        for ((entry, amount) in specialistSlots!!) {
            val equivalentStat = Stat.values().firstOrNull { it.name.toLowerCase(Locale.ENGLISH) == entry }

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
    @Deprecated("As of 3.15.19, replace with 'Cannot be built with []' unique")
    private var cannotBeBuiltWith: String? = null
    var cityStrength = 0
    var cityHealth = 0
    var replaces: String? = null
    var uniqueTo: String? = null
    var quote: String = ""
    @Deprecated("As of 3.15.16 - replaced with 'Provides a free [buildingName] [cityFilter]'")
    var providesFreeBuilding: String? = null
    override var uniques = ArrayList<String>()
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    var replacementTextForUniques = ""

    override var civilopediaText = listOf<FormattedLine>()


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

    private fun getUniquesStrings() = sequence {
        val tileBonusHashmap = HashMap<String, ArrayList<String>>()
        for (unique in uniqueObjects) when {
            unique.placeholderText == "[] from [] tiles []" && unique.params[2] == "in this city" -> {
                val stats = unique.params[0]
                if (!tileBonusHashmap.containsKey(stats)) tileBonusHashmap[stats] = ArrayList()
                tileBonusHashmap[stats]!!.add(unique.params[1])
            }
            unique.placeholderText == "Consumes [] []" -> Unit    // skip these,
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
    private fun getUniquesStringsWithoutDisablers() = getUniquesStrings()
        .filterNot {
            it.startsWith("Hidden ") && it.endsWith(" disabled") ||
            it == "Unbuildable" ||
            it == Constants.hideFromCivilopediaUnique
        }

    /** used in CityScreen (CityInfoTable and ConstructionInfoTable) */
    fun getDescription(cityInfo: CityInfo?, ruleset: Ruleset): String {
        val stats = getStats(cityInfo)
        val lines = ArrayList<String>()
        if (uniqueTo != null) lines += if (replaces == null) "Unique to [$uniqueTo]"
            else "Unique to [$uniqueTo], replaces [$replaces]"
        if (isWonder) lines += "Wonder"
        if (isNationalWonder) lines += "National Wonder"
        for ((resource, amount) in getResourceRequirements()) {
            lines += if (amount == 1) "Consumes 1 [$resource]" // For now, to keep the existing translations
            else "Consumes [$amount] [$resource]"
        }
        if (providesFreeBuilding != null)
            lines += "Provides a free [$providesFreeBuilding] in the city"
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques != "") lines += replacementTextForUniques
            else lines += getUniquesStringsWithoutDisablers()
        }
        if (!stats.isEmpty())
            lines += stats.toString()

        for ((stat, value) in getStatPercentageBonuses(cityInfo))
            if (value != 0f) lines += "+${value.toInt()}% {${stat.name}}\n"

        for ((greatPersonName, value) in greatPersonPoints)
            lines += "+$value " + "[$greatPersonName] points".tr()

        for ((specialistName, amount) in newSpecialists())
            lines += "+$amount " + "[$specialistName] slots".tr()

        if (requiredNearbyImprovedResources != null)
            lines += "Requires worked [" + requiredNearbyImprovedResources!!.joinToString("/") { it.tr() } + "] near city"

        if (cityStrength != 0) lines += "{City strength} +$cityStrength"
        if (cityHealth != 0) lines += "{City health} +$cityHealth"
        if (maintenance != 0) lines += "{Maintenance cost}: $maintenance {Gold}"
        return lines.joinToString("\n") { it.tr() }.trim()
    }

    fun getStats(city: CityInfo?): Stats {
        val stats = this.clone()
        if (city == null) return stats
        val civInfo = city.civInfo

        for (unique in city.getMatchingUniques("[] from every []")) {
            if (!matchesFilter(unique.params[1])) continue
            stats.add(unique.stats)
        }

        for (unique in city.getMatchingUniques("[] from every [] in cities where this religion has at least [] followers"))
            if (unique.params[2].toInt() <= city.religion.getFollowersOfMajorityReligion() && matchesFilter(unique.params[1]))
                stats.add(unique.stats)

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

        val baseBuildingName = getBaseBuilding(civInfo.gameInfo.ruleSet).name

        for (unique in civInfo.getMatchingUniques("+[]% [] from every []")) {
            if (unique.params[2] == baseBuildingName)
                stats.add(Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }

        if (uniques.contains("+5% Production for every Trade Route with a City-State in the empire"))
            stats.production += 5 * civInfo.citiesConnectedToCapitalToMediums.count { it.key.civInfo.isCityState() }

        return stats
    }

    override fun canBePurchasedWithStat(cityInfo: CityInfo, stat: Stat, ignoreCityRequirements: Boolean): Boolean {
        if (stat == Stat.Gold && isAnyWonder()) return false
        // May buy [buildingFilter] buildings for [amount] [Stat] [cityFilter]
        if (!ignoreCityRequirements && cityInfo.getMatchingUniques("May buy [] buildings for [] [] []")
                .any { it.params[2] == stat.name && matchesFilter(it.params[0]) && cityInfo.matchesFilter(it.params[3]) }
        ) return true
        return super.canBePurchasedWithStat(cityInfo, stat, ignoreCityRequirements)
    }

    override fun getBaseBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        if (stat == Stat.Gold) return getBaseGoldCost(cityInfo.civInfo).toInt()

        val lowestCostFromUnique = 
            (
                // Can be purchased for [amount] [Stat] [cityFilter]
                getMatchingUniques("Can be purchased for [] [] []")
                    .filter { it.params[1] == stat.name && cityInfo.matchesFilter(it.params[2]) }
                    .map { it.params[0].toInt() }
                // May buy [buildingFilter] buildings for [amount] [Stat] [cityFilter]
                + cityInfo.getMatchingUniques("May buy [] buildings for [] [] []")
                    .filter { it.params[2] == stat.name && matchesFilter(it.params[0]) && cityInfo.matchesFilter(it.params[3])}
                    .map { it.params[1].toInt() }
            ).minOrNull()
        if (lowestCostFromUnique != null) return lowestCostFromUnique

        // Can be purchased with [Stat] [cityFilter]
        if (getMatchingUniques("Can be purchased with [] []")
                .any { it.params[0] == stat.name && cityInfo.matchesFilter(it.params[1])}
        ) return cityInfo.civInfo.gameInfo.ruleSet.eras[cityInfo.civInfo.getEra()]!!.baseUnitBuyCost
        return null
    }

    override fun getCivilopediaTextHeader() = FormattedLine(name, header=2, icon=makeLink())
    override fun makeLink() = if (isAnyWonder()) "Wonder/$name" else "Building/$name"
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

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
            if (canBePurchasedWithStat(CityInfo(), Stat.Gold, true)) {
                stats += "${getBaseGoldCost(UncivGame.Current.gameInfo.currentPlayerCiv).toInt() / 10 * 10}${Fonts.gold}"
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

        if (providesFreeBuilding != null) {
            textList += FormattedLine()
            textList += FormattedLine("Provides a free [$providesFreeBuilding] in the city",
                link="Building/$providesFreeBuilding")
        }

        val stats = this.clone()
        val percentStats = getStatPercentageBonuses(null)
        val specialists = newSpecialists()
        if (uniques.isNotEmpty() || !stats.isEmpty() || !percentStats.isEmpty() || this.greatPersonPoints.isNotEmpty() || specialists.isNotEmpty())
            textList += FormattedLine()

        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques.isNotEmpty())
                textList += FormattedLine(replacementTextForUniques)
            else
                for (unique in getUniquesStrings())
                    textList += FormattedLine(Unique(unique))
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
            if (building.replaces == name || building.providesFreeBuilding == name
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

        for (unique in uniqueObjects.filter { it.placeholderText == "Cost increases by [] per owned city" })
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

    override fun getStatBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        var cost = getBaseBuyCost(cityInfo, stat)?.toDouble()
        if (cost == null) return null

        // Deprecated since 3.15.15
            if (stat == Stat.Gold) {
                for (unique in cityInfo.getMatchingUniques("Cost of purchasing items in cities reduced by []%"))
                    cost *= 1 - (unique.params[0].toFloat() / 100)

                for (unique in cityInfo.getMatchingUniques("Cost of purchasing [] buildings reduced by []%")) {
                    if (matchesFilter(unique.params[0]))
                        cost *= 1 - (unique.params[1].toFloat() / 100)
                }
            }
        //

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
        val rejectionReason = getRejectionReason(cityConstructions)
        return rejectionReason == ""
                || rejectionReason.startsWith("Requires")
                || rejectionReason.startsWith("Consumes")
                || rejectionReason.endsWith("Wonder is being built elsewhere")
                || rejectionReason == "Can only be purchased"
    }

    override fun getRejectionReason(construction: CityConstructions): String {
        if (construction.isBuilt(name)) return "Already built"
        // for buildings that are created as side effects of other things, and not directly built
        // unless they can be bought with faith
        if (uniques.contains("Unbuildable")) {
            if (canBePurchasedWithAnyStat(construction.cityInfo))
                return "Can only be purchased"
            return "Unbuildable"
        }

        val cityCenter = construction.cityInfo.getCenterTile()
        val civInfo = construction.cityInfo.civInfo

        // This overrides the others
        if (uniqueObjects
            .any {
                it.placeholderText == "Not displayed as an available construction unless [] is built"
                && !construction.containsBuildingOrEquivalent(it.params[0])
            }
        ) return "Should not be displayed"

        for (unique in uniqueObjects.filter { it.placeholderText == "Not displayed as an available construction without []" }) {
            val filter = unique.params[0]
            if (filter in civInfo.gameInfo.ruleSet.tileResources && !construction.cityInfo.civInfo.hasResource(filter)
                    || filter in civInfo.gameInfo.ruleSet.buildings && !construction.containsBuildingOrEquivalent(filter))
                return "Should not be displayed"
        }

        for (unique in uniqueObjects) when (unique.placeholderText) {
            "Enables nuclear weapon" -> if(!construction.cityInfo.civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled) return "Disabled by setting"
            "Must be on []" -> if (!cityCenter.matchesTerrainFilter(unique.params[0], civInfo)) return unique.text
            "Must not be on []" -> if (cityCenter.matchesTerrainFilter(unique.params[0], civInfo)) return unique.text
            "Must be next to []" -> if (!(unique.params[0] == "Fresh water" && cityCenter.isAdjacentToRiver()) // Fresh water is special, in that rivers are not tiles themselves but also fit the filter.
                    && cityCenter.getTilesInDistance(1).none { it.matchesFilter(unique.params[0], civInfo) }) return unique.text
            "Must not be next to []" -> if (cityCenter.getTilesInDistance(1).any { it.matchesFilter(unique.params[0], civInfo) }) return unique.text
            "Must have an owned [] within [] tiles" -> if (cityCenter.getTilesInDistance(unique.params[1].toInt()).none {
                        it.matchesFilter(unique.params[0], civInfo) && it.getOwner() == construction.cityInfo.civInfo
                    }) return unique.text
            "Can only be built in annexed cities" -> if (construction.cityInfo.isPuppet
                    || construction.cityInfo.civInfo.civName == construction.cityInfo.foundingCiv) return unique.text
            "Obsolete with []" -> if (civInfo.tech.isResearched(unique.params[0])) return unique.text
            Constants.hiddenWithoutReligionUnique -> if (!civInfo.gameInfo.hasReligionEnabled()) return unique.text
        }

        if (uniqueTo != null && uniqueTo != civInfo.civName) return "Unique to $uniqueTo"
        if (civInfo.gameInfo.ruleSet.buildings.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            return "Our unique building replaces this"
        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) return "$requiredTech not researched"

        for (unique in uniqueObjects.filter { it.placeholderText == "Unlocked with []" })
            if (civInfo.tech.researchedTechnologies.none { it.era() == unique.params[0] || it.name == unique.params[0] }
                    && !civInfo.policies.isAdopted(unique.params[0]))
                return unique.text

        // Regular wonders
        if (isWonder) {
            if (civInfo.gameInfo.getCities().any { it.cityConstructions.isBuilt(name) })
                return "Wonder is already built"

            if (civInfo.cities.any { it != construction.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                return "Wonder is being built elsewhere"

            if (civInfo.isCityState())
                return "No world wonders for city-states"

            val ruleSet = civInfo.gameInfo.ruleSet
            val startingEra = civInfo.gameInfo.gameParameters.startingEra
            if (startingEra in ruleSet.eras && name in ruleSet.eras[startingEra]!!.startingObsoleteWonders)
                return "Wonder is disabled when starting in this era"
        }


        // National wonders
        if (isNationalWonder) {
            if (civInfo.cities.any { it.cityConstructions.isBuilt(name) })
                return "National Wonder is already built"
            if (requiredBuildingInAllCities != null && civInfo.gameInfo.ruleSet.buildings[requiredBuildingInAllCities!!] == null)
                return "Required building in all cities does not exist in the ruleset!"
            if (requiredBuildingInAllCities != null
                    && civInfo.cities.any {
                        !it.isPuppet && !it.cityConstructions
                                .containsBuildingOrEquivalent(requiredBuildingInAllCities!!)
                    })
                return "Requires a [${civInfo.getEquivalentBuilding(requiredBuildingInAllCities!!)}] in all cities"
            if (civInfo.cities.any { it != construction.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                return "National Wonder is being built elsewhere"
            if (civInfo.isCityState())
                return "No national wonders for city-states"
        }

        if ("Spaceship part" in uniques) {
            if (!civInfo.hasUnique("Enables construction of Spaceship parts")) return "Apollo project not built!"
            if (civInfo.victoryManager.unconstructedSpaceshipParts()[name] == 0) return "Don't need to build any more of these!"
        }

        for (unique in uniqueObjects) when (unique.placeholderText) {
            "Requires []" -> {
                val filter = unique.params[0]
                if (filter in civInfo.gameInfo.ruleSet.buildings) {
                    if (civInfo.cities.none { it.cityConstructions.containsBuildingOrEquivalent(filter) }) return unique.text // Wonder is not built
                } else if (!civInfo.policies.adoptedPolicies.contains(filter)) return "Policy is not adopted" // this reason should not be displayed
            }

            "Requires a [] in this city" -> {
                val filter = unique.params[0]
                if (civInfo.gameInfo.ruleSet.buildings.containsKey(filter)
                        && !construction.containsBuildingOrEquivalent(filter))
                    return "Requires a [${civInfo.getEquivalentBuilding(filter)}] in this city" // replace with civ-specific building for user
            }

            "Requires a [] in all cities" -> {
                val filter = unique.params[0]
                if (civInfo.gameInfo.ruleSet.buildings.containsKey(filter)
                        && civInfo.cities.any { !it.isPuppet && !it.cityConstructions.containsBuildingOrEquivalent(unique.params[0]) })
                    return "Requires a [${civInfo.getEquivalentBuilding(unique.params[0])}] in all cities"  // replace with civ-specific building for user
            }
            "Hidden until [] social policy branches have been completed" -> {
                if (construction.cityInfo.civInfo.getCompletedPolicyBranchesCount() < unique.params[0].toInt()) {
                    return "Should not be displayed"
                }
            }
            "Hidden when [] Victory is disabled" -> {
                if (!civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.valueOf(unique.params[0]))) {
                    return unique.text
                }
            }
            // Deprecated since 3.15.14
                "Hidden when cultural victory is disabled" -> {
                    if (!civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.Cultural)) {
                        return unique.text
                    }
                }
            //
        }

        if (requiredBuilding != null && !construction.containsBuildingOrEquivalent(requiredBuilding!!)) {
            if (!civInfo.gameInfo.ruleSet.buildings.containsKey(requiredBuilding!!))
                return "Requires a [${requiredBuilding}] in this city, which doesn't seem to exist in this ruleset!"
            return "Requires a [${civInfo.getEquivalentBuilding(requiredBuilding!!)}] in this city"
        }
        // cannotBeBuiltWith is Deprecated as of 3.15.19
        val cannotBeBuiltWith = uniqueObjects
            .firstOrNull { it.placeholderText == "Cannot be built with []" }
            ?.params?.get(0)
            ?: this.cannotBeBuiltWith
        if (cannotBeBuiltWith != null && construction.isBuilt(cannotBeBuiltWith))
            return "Cannot be built with [$cannotBeBuiltWith]"

        for ((resource, amount) in getResourceRequirements())
            if (civInfo.getCivResourcesByName()[resource]!! < amount) {
                return if (amount == 1) "Consumes 1 [$resource]" // Again, to preserve existing translations
                    else "Consumes [$amount] [$resource]"
            }

        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = construction.cityInfo.getWorkableTiles()
                    .any {
                        it.resource != null
                                && requiredNearbyImprovedResources!!.contains(it.resource!!)
                                && it.getOwner() == civInfo
                                && (it.getTileResource().improvement == it.improvement || it.getTileImprovement()?.isGreatImprovement() == true || it.isCityCenter())
                    }
            if (!containsResourceWithImprovement) return "Nearby $requiredNearbyImprovedResources required"
        }


        if (!civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.Scientific)
                && "Enables construction of Spaceship parts" in uniques)
            return "Can't construct spaceship parts if scientific victory is not enabled!"

        return ""
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReason(cityConstructions) == ""

    override fun postBuildEvent(cityConstructions: CityConstructions, wasBought: Boolean): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo

        if ("Spaceship part" in uniques) {
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
        var freeBuildingUniques = uniqueObjects.asSequence().filter { it.placeholderText=="Provides a free [] []" }
        if (providesFreeBuilding!=null) freeBuildingUniques += sequenceOf(Unique("Provides a free [$providesFreeBuilding] [in this city]"))

        for(unique in freeBuildingUniques) {
            val affectedCities =
                if (unique.params[1] == "in this city") sequenceOf(cityConstructions.cityInfo)
                else civInfo.cities.asSequence().filter { it.matchesFilter(unique.params[1]) }

            val freeBuildingName = civInfo.getEquivalentBuilding(unique.params[0]).name

            for (city in affectedCities) {
                if (cityConstructions.containsBuildingOrEquivalent(freeBuildingName)) continue
                cityConstructions.addBuilding(freeBuildingName)
            }
        }

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
            replaces -> true
            else -> {
                if (uniques.contains(filter)) return true
                if (isStats(filter) && isStatRelated(Stat.valueOf(filter))) return true
                return false
            }
        }
    }

    fun isStatRelated(stat: Stat): Boolean {
        if (get(stat) > 0) return true
        if (getStatPercentageBonuses(null)[stat] > 0) return true
        if (uniqueObjects.any { it.placeholderText == "[] per [] population []" && it.stats[stat] > 0 }) return true
        return false
    }

    fun getBaseBuilding(ruleset: Ruleset): Building {
        return if (replaces == null) this else ruleset.buildings[replaces!!]!!
    }

    fun getImprovement(ruleset: Ruleset): TileImprovement? {
        val improvementUnique = uniqueObjects
            .firstOrNull { it.placeholderText == "Creates a [] improvement on a specific tile" }
            ?: return null
        return ruleset.tileImprovements[improvementUnique.params[0]]
    }

    fun isSellable() = !isAnyWonder() && !uniques.contains("Unsellable")

    override fun getResourceRequirements(): HashMap<String, Int> {
        val resourceRequirements = HashMap<String, Int>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in uniqueObjects)
            if (unique.placeholderText == "Consumes [] []")
                resourceRequirements[unique.params[1]] = unique.params[0].toInt()
        return resourceRequirements
    }
}
