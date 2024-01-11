package com.unciv.logic.city

import com.unciv.logic.city.managers.CityPopulationManager
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.models.Counter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toStringSigned

/** Manages calculating Great Person Points per City for nextTurn. See public constructor(city) below for details. */
class GreatPersonPointsBreakdown private constructor(
    private val ruleset: Ruleset,
    private val onlyAllGPPBoni: Boolean
) : Iterable<GreatPersonPointsBreakdown.Entry> {

    /** Represents any source of Great Person Point or GPP percentage boni */
    data class Entry(
        /** Simple label for the source of these points */
        val source: String,
        /** If true, [counter] represents a percentage bonus */
        val isPercentage: Boolean,
        /** In case we want to show the breakdown with decorations and/or Civilopedia linking */
        val pediaLink: String?,
        /** For display only - affects all GP */
        val isAllGPP: Boolean = false,
        /** Reference to the points, **do not mutate** */
        val counter: Counter<String> = Counter()
    ) {
        /** Player-readable representation that will work with tr() */
        override fun toString() = "{$source}: " + when {
            isAllGPP -> (counter.values.firstOrNull() ?: 0).toStringSigned() + (if (isPercentage) "%" else "")
            isPercentage -> counter.entries.joinToString { it.value.toStringSigned() + "% {${it.key}}" }
            else -> counter.entries.joinToString { it.value.toStringSigned() + " {${it.key}}" }
        }
    }

    companion object {
        // Using fixed-point(n.3) math in sum() to avoid surprises by rounding while still leveraging the Counter class
        const val fixedPointFactor = 1000
    }

    private val data = arrayListOf<Entry>()
    val size get() = data.size
    /** Collects all GPP names that have base points */
    val allNames = mutableSetOf<String>()

    override fun iterator(): Iterator<Entry> = data.iterator()

    /** Manages calculating Great Person Points per City for nextTurn.
     *
     *  Keeps flat points and percentage boni as separate items.
     *
     *  See [sum] to calculate the aggregate, and [iterator] to list as breakdown
     *
     *  @param onlyAllGPPBoni An optimization for use by [City.getGreatPersonPercentageBonus] (which in turn is only used by autoAssignPopulation). Skips some lookups, but in turn only the [allGppPercentageBonus] method gives a meaningful result.
     */
    constructor(city: City, onlyAllGPPBoni: Boolean = false) : this(city.getRuleset(), onlyAllGPPBoni) {
        if (onlyAllGPPBoni) {
            allNames.add("All")
        } else {
            // Collect points from Specialists
            val specialists = Entry("Specialists", false, null) // "Tutorial/Great People" as link doesn't quite fit
            for ((specialistName, amount) in city.population.getNewSpecialists())
                if (ruleset.specialists.containsKey(specialistName)) { // To solve problems in total remake mods
                    val specialist = ruleset.specialists[specialistName]!!
                    specialists.counter.add(specialist.greatPersonPoints.times(amount))
                }
            data.add(specialists)
            allNames += specialists.counter.keys

            // Collect points from buildings - duplicates listed individually (should not happen in vanilla Unciv)
            for (building in city.cityConstructions.getBuiltBuildings()) {
                if (building.greatPersonPoints.isEmpty()) continue
                data.add(Entry(building.name, false, building.makeLink(), false, building.greatPersonPoints))
                allNames += building.greatPersonPoints.keys
            }
        }

        // Now add boni for GreatPersonPointPercentage
        for (unique in city.getMatchingUniques(UniqueType.GreatPersonPointPercentage)) {
            if (!city.matchesFilter(unique.params[1])) continue
            addAllGPPBonus(getUniqueSourceName(unique), guessPediaLink(unique), allNames, unique.params[0].toInt())
        }

        // Now add boni for GreatPersonBoostWithFriendship (Sweden UP)
        val civ = city.civ
        for (otherCiv in civ.getKnownCivs()) {
            if (!civ.getDiplomacyManager(otherCiv).hasFlag(DiplomacyFlags.DeclarationOfFriendship))
                continue
            val boostUniques = civ.getMatchingUniques(UniqueType.GreatPersonBoostWithFriendship) +
                otherCiv.getMatchingUniques(UniqueType.GreatPersonBoostWithFriendship)
            for (unique in boostUniques)
                addAllGPPBonus("Declaration of Friendship", null, allNames, unique.params[0].toInt())
        }

        if (!onlyAllGPPBoni) {
            // And last, the GPP-type-specific GreatPersonEarnedFaster Unique
            val stateForConditionals = StateForConditionals(civInfo = civ, city = city)
            for (unique in civ.getMatchingUniques(UniqueType.GreatPersonEarnedFaster, stateForConditionals)) {
                val gppName = unique.params[0]
                if (gppName !in allNames) continue
                val bonusEntry = Entry(getUniqueSourceName(unique), true, guessPediaLink(unique))
                bonusEntry.counter.add(gppName, unique.params[1].toInt())
                data.add(bonusEntry)
            }
        }
    }

    private fun addAllGPPBonus(source: String, pediaLink: String?, keys: Set<String>, bonus: Int) {
        val counter = Counter<String>()
        for (key in keys) counter[key] = bonus
        data.add(Entry(source, true, pediaLink, true, counter))
    }

    private fun getUniqueSourceName(unique: Unique) = unique.sourceObjectName ?: "Bonus"

    private fun guessPediaLink(unique: Unique): String? {
        if (unique.sourceObjectName == null) return null
        return unique.sourceObjectType!!.name + "/" + unique.sourceObjectName
    }

    /** Aggregate over sources, applying percentage boni using fixed-point math to avoid rounding surprises */
    fun sum(): Counter<String> {
        // Accumulate, doing boni separately - to ensure they operate additively not multiplicatively
        val result = Counter<String>()
        val boni = Counter<String>()
        for (entry in this) {
            if (entry.isPercentage) {
                boni.add(entry.counter)
            } else {
                result.add(entry.counter * fixedPointFactor)
            }
        }

        // Apply boni
        for (key in result.keys.filter { it in boni }) {
            result.add(key, result[key] * boni[key] / 100)
        }

        // Round fixed-point to integers, toSet() because a result of 0 will remove the entry (-99% bonus in a certain Mod)
        for (key in result.keys.toSet())
            result[key] = (result[key] * 10 + 5) / fixedPointFactor / 10

        // Remove all "gpp" values that are not valid units
        for (key in result.keys.toSet())
            if (key !in ruleset.units)
                result.remove(key)

        return result
    }

    /** aggregate all boni for [CityPopulationManager.autoAssignPopulation] */
    fun allGppPercentageBonus() = if (onlyAllGPPBoni) sumOf { it.counter["All"] }
        else throw (UnsupportedOperationException("allGppPercentageBonus only works on GreatPersonPointsBreakdown instances created with onlyAllGPPBoni==true"))
}
