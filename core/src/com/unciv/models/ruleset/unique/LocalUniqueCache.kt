package com.unciv.models.ruleset.unique

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

/** Used to cache results of getMatchingUniques
 * Must only be used when we're sure the matching uniques will not change in the meantime */
class LocalUniqueCache(val cache: Boolean = true) {
    // This stores sequences *that iterate directly on a list* - that is, pre-resolved
    @Cache private val keyToUniques = HashMap<String, Sequence<Unique>>()

    @Readonly
    fun forCityGetMatchingUniques(
        city: City,
        uniqueType: UniqueType,
        gameContext: GameContext = city.state
    ): Sequence<Unique> {
        // City uniques are a combination of *global civ* uniques plus *city relevant* uniques (see City.getMatchingUniques())
        // We can cache the civ uniques separately, so if we have several cities using the same cache,
        //   we can cache the list of *civ uniques* to reuse between cities.

        val citySpecificUniques = get(
            "city-${city.id}-${uniqueType.name}",
            city.getLocalMatchingUniques(uniqueType, GameContext.IgnoreMultiplicationForCaching)
        ).filter { it.conditionalsApply(gameContext) }
            .flatMap { it.getMultiplied(gameContext) }

        val civUniques = forCivGetMatchingUniques(city.civ, uniqueType, gameContext)

        return citySpecificUniques + civUniques
    }

    @Readonly
    fun forCivGetMatchingUniques(
        civ: Civilization,
        uniqueType: UniqueType,
        gameContext: GameContext = civ.state
    ): Sequence<Unique> {
        val sequence = civ.getMatchingUniques(uniqueType, GameContext.IgnoreMultiplicationForCaching)
        // The uniques CACHED are ALL civ uniques, regardless of conditional matching.
        // The uniques RETURNED are uniques AFTER conditional matching.
        // This allows reuse of the cached values, between runs with different conditionals -
        //   for example, iterate on all tiles and get StatPercentForObject uniques relevant for each tile,
        //   each tile will have different conditional state, but they will all reuse the same list of uniques for the civ
        return get(
            "civ-${civ.civName}-${uniqueType.name}",
            sequence
        ).filter { it.conditionalsApply(gameContext) }
            .flatMap { it.getMultiplied(gameContext) }
    }

    /** Get cached results as a sequence */
    @Readonly
    private fun get(key: String, sequence: Sequence<Unique>): Sequence<Unique> {
        if (!cache) return sequence
        val valueInMap = keyToUniques[key]
        if (valueInMap != null) return valueInMap
        // Iterate the sequence, save actual results as a list, as return a sequence to that
        val results = sequence.toList().asSequence()
        keyToUniques[key] = results
        return results
    }
}
