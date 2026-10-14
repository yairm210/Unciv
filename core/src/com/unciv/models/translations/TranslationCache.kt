package com.unciv.models.translations

import com.unciv.UncivGame
import com.unciv.models.metadata.LocaleCode
import com.unciv.utils.hashOf
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern
import yairm210.purity.annotations.Readonly

/**
 *  Caches [active mods][activeMods] and repeatedly used values that are only dependent on [language]:
 *  - [conditionalsAfterUnique], [shouldCapitalize], [spaceEquivalent], [sortPriorities], [numberRegex], [numberFormatter]
 *
 *  Invalidation and update is automatic for [active mods][activeMods], and [manual][invalidateLanguage] for [language].
 */
object TranslationCache {
    private var cachedHash = Int.MIN_VALUE

    /** A Java [Locale] matching the current [language]
     *  @see LocaleCode.getLocale */
    var locale: Locale = Locale.getDefault()
        private set

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

    /** @return a [DecimalFormat] for the current [language], cloned to make your copy thread-safe */
    @Readonly
    fun getNumberFormatter() = numberFormatter.clone() as DecimalFormat
    private var numberFormatter = DecimalFormat()

    /** A Regex able to find numbers that were formatted in the current language _or_ plain positive ASCII digit numbers */
    internal var numberRegex = Regex("""\d+""")
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

    /** Checks the given [language] matches the cached one */
    fun check(language: String) {
        check(this.language == language) { "TranslationCache out of sync" }
    }

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
        locale = LocaleCode.getLocale(language)

        val conditionalOrdering: String
        with (UncivGame.Current.translations) {
            conditionalOrdering = getConditionalOrder(language)
            conditionalsAfterUnique = placeConditionalsAfterUnique(language)
            shouldCapitalize = shouldCapitalize(language)
            spaceEquivalent = getSpaceEquivalent(language)
        }

        // Somewhere, we asked the translators to reorder all possible conditionals in a way that
        // makes sense in their language. We get this ordering, and map their placeholderText to their ordinal.
        sortPriorities = buildMap {
            conditionalOrdering.getModifierTexts().forEachIndexed { index, text ->
                put(text.getPlaceholderText(), index)
            }
        }

        val formatter = getNumberFormat()
        numberRegex = getNumberRegex(formatter)
        numberFormatter = formatter
    }

    private fun getNumberFormat(): DecimalFormat {
        return NumberFormat.getInstance(locale) as? DecimalFormat
            // This should never happen, at least not in stock Java 21:
            ?: getFallbackDecimalFormat()
    }

    @Readonly
    private fun getFallbackDecimalFormat() =
        DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(locale))

    /** Uncached, bypassing the cached language, for unit tests */
    @Readonly
    internal fun getNumberFormatForLanguage(language: String): DecimalFormat {
        return NumberFormat.getInstance(LocaleCode.getLocale(language)) as? DecimalFormat
            ?: getFallbackDecimalFormat()
    }

    private fun StringBuilder.optional(str: String) = when {
        str.isEmpty() -> Unit
        str.length == 1 -> {
            append('\\')
            append(str)
            append('?')
        }
        else -> {
            append("(?:")
            append(Pattern.quote(str))
            append(")?")
        }
    }

    private fun getNumberRegex(formatter: DecimalFormat): Regex {
        val symbols = formatter.decimalFormatSymbols
        val pattern = buildString {
            append("\\b")
            optional(formatter.negativePrefix)
            append("\\p{Nd}+")
            if (formatter.isGroupingUsed) {
                append("(?:\\")
                append(symbols.groupingSeparator)
                append("\\p{Nd}{")
                append(formatter.groupingSize)
                append("})*")
            }
            /* Activate the following to allow post-processing numbers with fractions (would change number of decimal places, therefore disabled)
            append("(?:\\")
            append(symbols.decimalSeparator)
            append("\\p{Nd}+")
            append(")?")
            */
            optional(formatter.negativeSuffix)
            append("\\b")
        }
        return Regex(pattern)
    }
}
