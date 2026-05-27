package com.unciv.models.ruleset.unique

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.expressions.Expressions
import yairm210.purity.annotations.Readonly

/**
 * Identifies a game calculation that can be overridden via [UniqueType.FunctionCalculation].
 *
 * [text] is the string used in the `[calculationType]` unique parameter.
 * [defaultExpression] documents the vanilla formula as a countable expression
 * using default [ModConstants][com.unciv.models.ModConstants] values.
 * When no [UniqueType.FunctionCalculation] unique is present, the game uses the standard
 * hardcoded formula (which respects ModConstants). When an override is present, the
 * countable expression fully replaces the base formula; modifiers such as
 * [UniqueType.LessPolicyCost] or speed/difficulty multipliers still apply on top
 * if the consuming code applies them after evaluating the expression.
 */
enum class Calculation(val text: String, val defaultExpression: String, val description: String) {
    /**
     * Faith cost to found a Pantheon, before the speed modifier is applied.
     * Counts civs that have already founded a Religion (not just a Pantheon).
     * Uses [ModConstants.pantheonBase] and [ModConstants.pantheonGrowth].
     */
    PantheonCost(
        "Pantheon Cost",
        "10 + [Civs with Religion] * 5",
        "Faith cost to found a Pantheon (before speed modifier)"
    ),

    /**
     * Maximum number of Religions that can be founded, before capping at the number
     * of Religion symbols defined in the ruleset.
     * Uses [ModConstants.religionLimitBase] and [ModConstants.religionLimitMultiplier].
     */
    MaxFoundableReligions(
        "Max Foundable Religions",
        "floor([[Major] Civilizations] * 0.5) + 1",
        "Maximum number of foundable Religions (before capping at available Religion symbols)"
    ),

    /**
     * Base culture cost to adopt a Policy, before city scaling, difficulty, and speed modifiers.
     * [UniqueType.LessPolicyCost] and [UniqueType.LessPolicyCostFromCities] still apply on top.
     */
    PolicyCostBase(
        "Policy Cost Base",
        "([Adopted Policies] * 6) ^ 1.7 + 25",
        "Base Culture cost to adopt a Policy (before city scaling, difficulty, and speed modifiers)"
    ),
    ;

    /**
     * Returns the countable expression to use for this calculation for the given [civ].
     * Priority: civ-level [UniqueType.FunctionCalculation] → ModOptions [UniqueType.FunctionCalculation] → [defaultExpression].
     */
    @Readonly
    fun getExpression(civ: Civilization): String {
        @Suppress("DEPRECATION")
        civ.getMatchingUniques(UniqueType.FunctionCalculation, civ.state)
            .firstOrNull { it.params[0] == text }
            ?.let { return it.params[1] }
        @Suppress("DEPRECATION")
        civ.gameInfo.ruleset.modOptions
            .getMatchingUniques(UniqueType.FunctionCalculation)
            .firstOrNull { it.params[0] == text }
            ?.let { return it.params[1] }
        return defaultExpression
    }

    /** Evaluates this calculation for the given [civ], returning 0 on failure. */
    @Readonly
    fun evaluate(civ: Civilization): Int =
        Expressions().eval(getExpression(civ), civ.state) ?: 0

    companion object {
        private val byText = entries.associateBy { it.text }
        fun getByText(text: String) = byText[text]
    }
}