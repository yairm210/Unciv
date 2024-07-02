package com.unciv.models.ruleset.unique

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.stats.INamed

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques : INamed {
    var uniques: ArrayList<String> // Can not be a hashset as that would remove doubles

    // Every implementation should override these with the same `by lazy (::thingsProvider)`
    // AND every implementation should annotate these with `@delegate:Transient`
    val uniqueObjects: List<Unique>
    val uniqueMap: Map<String, List<Unique>>

    fun uniqueObjectsProvider(): List<Unique> {
        if (uniques.isEmpty()) return emptyList()
        return uniques.map { Unique(it, getUniqueTarget(), name) }
    }
    fun uniqueMapProvider(): UniqueMap {
        val newUniqueMap = UniqueMap()
        if (uniques.isNotEmpty())
            newUniqueMap.addUniques(uniqueObjects)
        return newUniqueMap
    }

    /** Technically not currently needed, since the unique target can be retrieved from every unique in the uniqueObjects,
     * But making this a function is relevant for future "unify Unciv object" plans ;)
     * */
    fun getUniqueTarget(): UniqueTarget

    fun getMatchingUniques(uniqueTemplate: String, stateForConditionals: StateForConditionals? = null): Sequence<Unique> {
        val matchingUniques = uniqueMap[uniqueTemplate]
            ?: return emptySequence()

        val actualStateForConditionals = stateForConditionals ?: StateForConditionals()
        val uniques = matchingUniques.asSequence().filter { it.conditionalsApply(actualStateForConditionals) }
        return uniques.flatMap { it.getMultiplied(actualStateForConditionals) }
    }

    fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueType.placeholderText, stateForConditionals)

    fun hasUnique(uniqueTemplate: String, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueTemplate, stateForConditionals).any()

    fun hasUnique(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueType.placeholderText, stateForConditionals).any()

    fun availabilityUniques(): Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals) + getMatchingUniques(UniqueType.CanOnlyBeBuiltWhen, StateForConditionals.IgnoreConditionals)

    fun techsRequiredByUniques(): Sequence<String> {
        return availabilityUniques()
                // Currently an OnlyAvailableWhen can have multiple conditionals, implicitly a conjunction.
                // Therefore, if any of its several conditionals is a ConditionalTech, then that tech is required.
                .flatMap { it.conditionals }
                .filter{ it.type == UniqueType.ConditionalTech }
                .map { it.params[0] }
    }

    fun legacyRequiredTechs(): Sequence<String> = emptySequence()

    fun requiredTechs(): Sequence<String> = legacyRequiredTechs() + techsRequiredByUniques()

    fun requiredTechnologies(ruleset: Ruleset): Sequence<Technology?> =
        requiredTechs().map { ruleset.technologies[it] }

    fun era(ruleset: Ruleset): Era? =
            requiredTechnologies(ruleset).map { it?.era() }.map { ruleset.eras[it] }.maxByOrNull { it?.eraNumber ?: 0 }
            // This will return null only if requiredTechnologies() is empty or all required techs have no eraNumber

    fun techColumn(ruleset: Ruleset): TechColumn? =
            requiredTechnologies(ruleset).map { it?.column }.filterNotNull().maxByOrNull { it.columnNumber }
            // This will return null only if *all* required techs have null TechColumn.

    fun availableInEra(ruleset: Ruleset, requestedEra: String): Boolean {
        val eraAvailable: Era = era(ruleset)
            ?: return true // No technologies are required, so available in the starting era.
        // This is not very efficient, because era() inspects the eraNumbers and then returns the whole object.
        // We could take a max of the eraNumbers directly.
        // But it's unlikely to make any significant difference.
        // Currently this is only used in CityStateFunctions.kt.
        return eraAvailable.eraNumber <= ruleset.eras[requestedEra]!!.eraNumber
    }

    /**
     *  Is this ruleset object unavailable as determined by settings chosen at game start?
     *
     *  - **Not** checked: HiddenFromCivilopedia - That is a Mod choice and less a user choice and these objects should otherwise work.
     *  - Default implementation checks disabling by Religion, Espionage or Victory types.
     *  - Overrides need to deal with e.g. Era-specific wonder disabling, no-nukes, ruin rewards by difficulty, and so on!
     */
    fun isHiddenBySettings(gameInfo: GameInfo): Boolean {
        if (!gameInfo.isReligionEnabled() && hasUnique(UniqueType.HiddenWithoutReligion)) return true
        if (!gameInfo.isEspionageEnabled() && hasUnique(UniqueType.HiddenWithoutEspionage)) return true
        if (getMatchingUniques(UniqueType.HiddenWithoutVictoryType).any { it.params[0] !in gameInfo.gameParameters.victoryTypes }) return true
        return false
    }

    /**
     *  Is this ruleset object hidden from Civilopedia?
     *
     *  - Obviously, the [UniqueType.HiddenFromCivilopedia] test is done here (and nowhere else - exception TranslationFileWriter for Tutorials).
     *  - Includes the [isHiddenBySettings] test if [gameInfo] is known, otherwise existence of Religion is guessed from [ruleset], and all victory types are assumed enabled.
     *  - Note: RulesetObject-type specific overrides should not be necessary unless (TODO) we implement support for conditionals on the 'Hidden*' Uniques.
     *  @param gameInfo Defaults to [UncivGame.getGameInfoOrNull]. Civilopedia must also be able to run from MainMenu without a game loaded. In that case only this parameter can be `null`. So if you know it - provide!
     *  @param ruleset required if [gameInfo] is null, otherwise optional.
     *  @throws NullPointerException if both parameters are null
     */
    fun isHiddenFromCivilopedia(
        gameInfo: GameInfo?,
        ruleset: Ruleset? = null
    ): Boolean {
        if (hasUnique(UniqueType.HiddenFromCivilopedia)) return true
        if (gameInfo != null && isHiddenBySettings(gameInfo)) return true
        if (gameInfo == null && hasUnique(UniqueType.HiddenWithoutReligion) && ruleset!!.beliefs.isEmpty()) return true
        return false
    }
    /** Overload of [isHiddenFromCivilopedia] for use in actually game-agnostic parts of Civilopedia */
    fun isHiddenFromCivilopedia(ruleset: Ruleset) = isHiddenFromCivilopedia(UncivGame.getGameInfoOrNull(), ruleset)
}
