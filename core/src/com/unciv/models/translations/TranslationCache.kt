package com.unciv.models.translations

import com.unciv.UncivGame
import com.unciv.models.metadata.LocaleCode
import com.unciv.utils.hashOf
import java.text.DecimalFormat

/**
 *  Caches [active mods][activeMods] and repeatedly used values that are only dependent on [language]:
 *  - [conditionalsAfterUnique], [shouldCapitalize], [spaceEquivalent], [sortPriorities], [numberRegex], [numberFormatter]
 *
 *  Invalidation and update is automatic for [active mods][activeMods], and [manual][invalidateLanguage] for [language].
 */
object TranslationCache {
    private var cachedHash = Int.MIN_VALUE

    /** @see Translations.placeConditionalsAfterUnique */
    internal var conditionalsAfterUnique = true
        private set

    /** @see Translations.shouldCapitalize */
    internal var shouldCapitalize = true
        private set

    /** @see Translations.getSpaceEquivalent */
    internal var spaceEquivalent = " "
        private set

    /** @see Translations.getConditionalOrder */
    internal var sortPriorities: Map<String, Int> = mapOf()
        private set

    /** @see LocaleCode.getNumberRegexForLanguage */
    internal var numberRegex = Regex("""\d+""")
        private set

    /** @see LocaleCode.getNumberFormatFromLanguage */
    internal var numberFormatter = DecimalFormat()
        private set

    /** Mods active in the current game, or set by MainMenuScreen via [translationActiveMods][Translations.translationActiveMods] */
    internal var activeMods: HashSet<String> = hashSetOf()
        get() {
            val hash = getCurrentHash()
            if (hash != cachedHash) {
                cachedHash = hash
                field = getCurrentSet()
            }
            return field
        }
        private set

    /** Returns the language from settings and maintains dependent cached values */
    internal var language: String = ""
        get() {
            val setting = UncivGame.Current.settings.language
            if (setting != field) {
                updateLanguage(setting)
                field = setting
            }
            return field
        }
        private set

    /** Call this when updating translation tables ([UncivGame.translations]) */
    fun invalidateLanguage() {
        language = ""
    }

    private fun getCurrentHash(): Int {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null) {
            hashOf(gameInfo.gameParameters.mods.hashCode(), gameInfo.gameParameters.baseRuleset.hashCode())
        } else {
            UncivGame.Current.translations.translationActiveMods.hashCode()
        }
    }

    private fun getCurrentSet(): LinkedHashSet<String> {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null) {
            val par = gameInfo.gameParameters
            // This is equivalent to (par.mods + par.baseRuleset) without the cast down to `Set`
            LinkedHashSet<String>(par.mods.size + 1).apply {
                addAll(par.mods)
                add(par.baseRuleset)
            }
        } else {
            UncivGame.Current.translations.translationActiveMods
        }
    }

    private fun updateLanguage(language: String) {
        val conditionalOrdering: String
        with (UncivGame.Current.translations) {
            conditionalOrdering = getConditionalOrder(language)
            conditionalsAfterUnique = placeConditionalsAfterUnique(language)
            shouldCapitalize = shouldCapitalize(language)
            spaceEquivalent = getSpaceEquivalent(language)
        }

        // Somewhere, we asked the translators to reorder all possible conditionals in a way that
        // makes sense in their language. We get this ordering, and map their placeholderText to their ordinal.
        sortPriorities = pointyBraceRegex
            .findAll(conditionalOrdering)
            .map { it.groupValues[1].getPlaceholderText() }
            .withIndex()
            .associate { (index, key) -> key to index }

        numberRegex = LocaleCode.getNumberRegexForLanguage(language)
        numberFormatter = LocaleCode.getNumberFormatFromLanguage(language)
    }
}
