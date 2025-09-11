package com.unciv.logic.city

import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.models.Counter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/** Manages calculating Great Person Points per City for nextTurn. See public constructor(city) below for details. */
class GreatPersonPointsBreakdown private constructor(private val ruleset: Ruleset) {
    // ruleset kept as class field for reuse in sum(), for the "Remove all gpp values that are not valid units" step.
    // I am unsure why that existed before this class was written, what the UX for invalid mods should be.
    // Refactoring through allNames so that ruleset is only needed in init should be easy if all UI should behave as if any invalid definition did not exist at all.
    // As is, the "lost" points can still display.

    /** Return type component of the [Companion.getPercentagesApplyingToAllGP] helper */
    private class AllGPPercentageEntry(
        val source: String,
        val pediaLink: String?,
        val bonus: Int
    )

    /** Represents any source of Great Person Points or GPP percentage bonuses */
    class Entry (
        /** Simple label for the source of these points */
        val source: String,
        /** In case we want to show the breakdown with decorations and/or Civilopedia linking */
        val pediaLink: String? = null,
        /** For display only - this entry affects all Great Persons and can be displayed as simple percentage without listing all GP keys */
        val isAllGP: Boolean = false,
        /** Reference to the points, **do not mutate** */
        // To lift the mutability restriction, clone building.greatPersonPoints below - all others are already owned here
        val counter: Counter<String> = Counter()
    )

    companion object {
        // Using fixed-point(n.3) math in sum() to avoid surprises by rounding while still leveraging the Counter class
        const val fixedPointFactor = 1000


        @Pure private fun getUniqueSourceName(unique: Unique) = unique.sourceObjectName ?: "Bonus"

        @Pure
        private fun guessPediaLink(unique: Unique): String? {
            if (unique.sourceObjectName == null) return null
            return unique.sourceObjectType!!.name + "/" + unique.sourceObjectName
        }

        /** List all percentage bonuses that apply to all GPP
         *
         *  This is used internally from the public constructor to include them in the brakdown,
         *  and exposed to autoAssignPopulation via [getGreatPersonPercentageBonus]
         */
        @Readonly
        private fun getPercentagesApplyingToAllGP(city: City) = sequence {
            // Now add boni for GreatPersonPointPercentage
            for (unique in city.getMatchingUniques(UniqueType.GreatPersonPointPercentage)) {
                if (!city.matchesFilter(unique.params[1])) continue
                yield(AllGPPercentageEntry(getUniqueSourceName(unique), guessPediaLink(unique), unique.params[0].toInt()))
            }

            // Now add boni for GreatPersonBoostWithFriendship (Sweden UP)
            val civ = city.civ
            for (otherCiv in civ.getKnownCivs()) {
                if (!civ.getDiplomacyManager(otherCiv)!!.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
                    continue
                val boostUniques = civ.getMatchingUniques(UniqueType.GreatPersonBoostWithFriendship) +
                    otherCiv.getMatchingUniques(UniqueType.GreatPersonBoostWithFriendship)
                for (unique in boostUniques)
                    yield(AllGPPercentageEntry("Declaration of Friendship", null, unique.params[0].toInt()))
            }
        }

        /** Aggregate all percentage bonuses that apply to all GPP
         *
         *  For use by [City.getGreatPersonPercentageBonus] (which in turn is only used by autoAssignPopulation)
         */
        @Readonly
        fun getGreatPersonPercentageBonus(city: City) = getPercentagesApplyingToAllGP(city).sumOf { it.bonus }
    }

    /** Collects all GPP names that have base points */
    val allNames = mutableSetOf<String>()

    val basePoints = ArrayList<Entry>()
    val percentBonuses = ArrayList<Entry>()

    /** Manages calculating Great Person Points per City for nextTurn.
     *
     *  Keeps flat points and percentage boni as separate items.
     *
     *  See [sum] to calculate the aggregate, use [basePoints] and [percentBonuses] to list as breakdown
     */
    constructor(city: City) : this(city.getRuleset()) {
        // Collect points from Specialists
        val specialists = Entry("Specialists") // "Tutorial/Great People" as link doesn't quite fit
        for ((specialistName, amount) in city.population.getNewSpecialists())
            if (ruleset.specialists.containsKey(specialistName)) { // To solve problems in total remake mods
                val specialist = ruleset.specialists[specialistName]!!
                specialists.counter.add(specialist.greatPersonPoints.times(amount))
            }
        basePoints.add(specialists)
        allNames += specialists.counter.keys

        // Collect points from buildings - duplicates listed individually (should not happen in vanilla Unciv)
        for (building in city.cityConstructions.getBuiltBuildings()) {
            if (building.greatPersonPoints.isEmpty()) continue
            basePoints.add(Entry(building.name, building.makeLink(), counter = building.greatPersonPoints))
            allNames += building.greatPersonPoints.keys
        }

        // Translate bonuses applying to all GP equally
        for (item in getPercentagesApplyingToAllGP(city)) {
            val bonusEntry = Entry(item.source, item.pediaLink, isAllGP = true)
            for (name in allNames)
                bonusEntry.counter.add(name, item.bonus)
            percentBonuses.add(bonusEntry)
        }

        // And last, the GPP-type-specific GreatPersonEarnedFaster Unique
        val stateForConditionals = city.state
        for (unique in city.civ.getMatchingUniques(UniqueType.GreatPersonEarnedFaster, stateForConditionals)) {
            val gppName = unique.params[0]
            if (gppName !in allNames) continue // No sense applying a percentage without base points
            val bonusEntry = Entry(getUniqueSourceName(unique), guessPediaLink(unique))
            bonusEntry.counter.add(gppName, unique.params[1].toInt())
            percentBonuses.add(bonusEntry)
        }
    }

    /** Aggregate over sources, applying percentage boni using fixed-point math to avoid rounding surprises */
    @Readonly
    fun sum(): Counter<String> {
        // Accumulate base points as fake "fixed-point"
        val result = Counter<String>()
        for (entry in basePoints)
            result.add(entry.counter * fixedPointFactor)

        // Accumulate percentage bonuses additively not multiplicatively
        val bonuses = Counter<String>()
        for (entry in percentBonuses) {
            bonuses.add(entry.counter)
        }

        // Apply percent bonuses
        for (key in result.keys.filter { it in bonuses }) {
            result.add(key, result[key] * bonuses[key] / 100)
        }

        // Round fixed-point to integers, toSet() because a result of 0 will remove the entry (-99% bonus in a certain Mod)
        for (key in result.keys.toSet())
            result[key] = (result[key] + fixedPointFactor / 2) / fixedPointFactor

        // Remove all "gpp" values that are not valid units
        for (key in result.keys.toSet())
            if (key !in ruleset.units)
                result.remove(key)

        return result
    }
}
