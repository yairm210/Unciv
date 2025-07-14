package com.unciv.models.ruleset.unique

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.stats.INamed
import com.unciv.ui.components.extensions.toPercent

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques : INamed {
    var uniques: ArrayList<String> // Can not be a hashset as that would remove doubles

    // Every implementation should override these with the same `by lazy (::thingsProvider)`
    // AND every implementation should annotate these with `@delegate:Transient`
    val uniqueObjects: List<Unique>
    val uniqueMap: UniqueMap

    fun uniqueObjectsProvider(): List<Unique> {
        return uniqueObjectsProvider(uniques)
    }
    fun uniqueMapProvider(): UniqueMap {
        return uniqueMapProvider(uniqueObjects)
    }
    fun uniqueObjectsProvider(uniques: List<String>): List<Unique> {
        if (uniques.isEmpty()) return emptyList()
        return uniques.map { Unique(it, getUniqueTarget(), name) }
    }
    fun uniqueMapProvider(uniqueObjects: List<Unique>): UniqueMap {
        val newUniqueMap = UniqueMap()
        if (uniqueObjects.isNotEmpty())
            newUniqueMap.addUniques(uniqueObjects)
        return newUniqueMap
    }

    /** Technically not currently needed, since the unique target can be retrieved from every unique in the uniqueObjects,
     * But making this a function is relevant for future "unify Unciv object" plans ;)
     * */
    fun getUniqueTarget(): UniqueTarget

    fun getMatchingUniques(uniqueType: UniqueType, state: GameContext = GameContext.EmptyState) =
        uniqueMap.getMatchingUniques(uniqueType, state)

    fun getMatchingUniques(uniqueTag: String, state: GameContext = GameContext.EmptyState) =
        uniqueMap.getMatchingUniques(uniqueTag, state)

    fun hasUnique(uniqueType: UniqueType, state: GameContext? = null) =
        uniqueMap.hasMatchingUnique(uniqueType, state ?: GameContext.EmptyState)

    fun hasUnique(uniqueTag: String, state: GameContext? = null) =
        uniqueMap.hasMatchingUnique(uniqueTag, state ?: GameContext.EmptyState)

    fun hasTagUnique(tagUnique: String) =
        uniqueMap.hasTagUnique(tagUnique)

    fun availabilityUniques(): Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals) + getMatchingUniques(UniqueType.CanOnlyBeBuiltWhen, GameContext.IgnoreConditionals)

    fun techsRequiredByUniques(): Sequence<String> {
        return availabilityUniques()
                // Currently an OnlyAvailableWhen can have multiple conditionals, implicitly a conjunction.
                // Therefore, if any of its several conditionals is a ConditionalTech, then that tech is required.
                .flatMap { it.modifiers }
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

    fun getWeightForAiDecision(gameContext: GameContext): Float {
        var weight = 1f
        for (unique in getMatchingUniques(UniqueType.AiChoiceWeight, gameContext))
            weight *= unique.params[0].toPercent()
        return weight
    }

    /**
     *  Is this ruleset object unavailable as determined by settings chosen at game start?
     *
     *  - **Not** checked: HiddenFromCivilopedia - That is a Mod choice and less a user choice and these objects should otherwise work.
     *  - Default implementation checks disabling by Religion, Espionage or Victory types.
     *  - Overrides need to deal with e.g. Era-specific wonder disabling, no-nukes, ruin rewards by difficulty, and so on!
     */
    fun isUnavailableBySettings(gameInfo: GameInfo): Boolean {
        val gameBasedConditionals = setOf(
            UniqueType.ConditionalVictoryDisabled,
            UniqueType.ConditionalVictoryEnabled,
            UniqueType.ConditionalSpeed,
            UniqueType.ConditionalDifficulty,
            UniqueType.ConditionalReligionEnabled,
            UniqueType.ConditionalReligionDisabled,
            UniqueType.ConditionalEspionageEnabled,
            UniqueType.ConditionalEspionageDisabled,
        )
        val gameContext = GameContext(gameInfo = gameInfo)

        if (getMatchingUniques(UniqueType.Unavailable, GameContext.IgnoreConditionals)
                .any { unique ->
                    unique.modifiers.any {
                        it.type in gameBasedConditionals
                                && Conditionals.conditionalApplies(null, it, gameContext) } })
            return true

        if (getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                .any { unique ->
                    unique.modifiers.any {
                        it.type in gameBasedConditionals
                                && !Conditionals.conditionalApplies(null, it, gameContext) } })
            return true

        return false
    }

    /**
     *  Is this ruleset object hidden from Civilopedia?
     *
     *  - Obviously, the [UniqueType.HiddenFromCivilopedia] test is done here (and nowhere else - exception TranslationFileWriter for Tutorials).
     *  - Includes the [isUnavailableBySettings] test if [gameInfo] is known, otherwise existence of Religion/Espionage is guessed from [ruleset],
     *    and all victory types are assumed enabled.
     *  - Note: RulesetObject-type specific overrides should not be necessary.
     *  @param gameInfo Defaults to [UncivGame.getGameInfoOrNull]. Civilopedia must also be able to run from MainMenu without a game loaded.
     *                  In that case only this parameter can be `null`. So if you know it - provide!
     *  @param ruleset  Required if [gameInfo] is null, otherwise optional (but with both null this will simply return `false`).
     */
    fun isHiddenFromCivilopedia(
        gameInfo: GameInfo?,
        ruleset: Ruleset? = null
    ): Boolean {
        if (hasUnique(UniqueType.HiddenFromCivilopedia)) return true
        if (gameInfo != null && isUnavailableBySettings(gameInfo)) return true
        if (gameInfo == null && ruleset != null) {
            /* No game is loaded, but we know the Ruleset. This happens when opening Civilopedia from MainMenuScreen right after launch.
             * We can assume: If the Ruleset has Religion/Espionage, the user would enable it for a hypothetical game, so we shouldn't hide anything.
             * But we can't assume how mods may use the possible combinations of positive/negative modifiers
             *     e.g. a mod switches between two versions of the same object depending on user choice
             *     better do a check based on the best guess for the availability of these features.
             */
            if (shouldBeHiddenIfNoGameLoaded(
                    ruleset.beliefs.isNotEmpty(),
                    UniqueType.ConditionalReligionEnabled,
                    UniqueType.ConditionalReligionDisabled
                )) return true
            if (shouldBeHiddenIfNoGameLoaded(
                    ruleset.nations.values.any { it.spyNames.isNotEmpty() },
                    UniqueType.ConditionalEspionageEnabled,
                    UniqueType.ConditionalEspionageDisabled
                )) return true
        }
        return false
    }
    /** Overload of [isHiddenFromCivilopedia] for use in actually game-agnostic parts of Civilopedia */
    fun isHiddenFromCivilopedia(ruleset: Ruleset) = isHiddenFromCivilopedia(UncivGame.getGameInfoOrNull(), ruleset)

    /** Common for Religion/Espionage: Hidden check when no game is loaded
     *  @param hasFeature Best guess from the Ruleset whether the feature is available
     *  @param enabler The modifier testing feature is on: `ConditionalReligionEnabled` or `ConditionalEspionageEnabled`
     *  @param disabler The modifier testing feature is off: `ConditionalReligionDisabled` or `ConditionalEspionageDisabled`
     */
    private fun shouldBeHiddenIfNoGameLoaded(hasFeature: Boolean, enabler: UniqueType, disabler: UniqueType): Boolean {
        for (unique in getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)) {
            if (unique.hasModifier(enabler)) return !hasFeature
            if (unique.hasModifier(disabler)) return hasFeature
        }
        return false
    }
}
