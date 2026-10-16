package com.unciv.models.translations

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.models.metadata.LocaleCode
import com.unciv.utils.hashOf
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.regex.Pattern
import yairm210.purity.annotations.Readonly
import kotlin.concurrent.Volatile

/**
 *  Caches [active mods][activeMods] and repeatedly used values that are only dependent on [language] - call [getLangData] to get a [LanguageCache] instance
 *  with `conditionalsAfterUnique`, `shouldCapitalize`, `spaceEquivalent`, `sortPriorities`, `numberRegex`, `format`, `parse`.
 *
 *  Invalidation and update is automatic for [active mods][activeMods], and [manual][invalidateLanguage] for [language].
 */
object TranslationCache {
    @Volatile
    private var modsCache: ActiveModsCache? = null

    /** Mods active in the current game, or set by MainMenuScreen via [translationActiveMods][Translations.translationActiveMods] */
    internal val activeMods: HashSet<String>
        get() {
            val cache = modsCache
            if (cache?.isValid() == true) return cache.activeMods
            val newCache = ActiveModsCache.create()
            modsCache = newCache
            return newCache.activeMods
        }

    @Volatile
    private var langData: LanguageCache? = null

    /** Returns the language from settings and maintains dependent cached values */
    internal val language get() = getLangData().language

    @Readonly
    @Suppress("purity") // Can update cache, but callers need to be marked readonly
    internal fun getLangData(): LanguageCache {
        val setting = UncivGame.Current.settings.language
        val data = langData
        if (data?.language == setting) return data
        val newData = LanguageCache.of(setting)
        langData = newData
        return newData
    }

    /** Call this when updating translation tables ([UncivGame.translations]) */
    fun invalidateLanguage() {
        langData = null
    }

    /** @return a [DecimalFormat] for the current [language], cloned to make your copy mutable
     *  @see LanguageCache.reformat
     */
    fun getNumberFormatter() = getLangData().getMutableFormatter()

    /** Uncached, bypassing the cached language, for unit tests */
    @Readonly
    internal fun getNumberFormatForLanguage(language: String): DecimalFormat {
        val locale = LocaleCode.getLocale(language)
        return getNumberFormat(locale)
    }

    @Readonly
    private fun getNumberFormat(locale: Locale): DecimalFormat {
        return NumberFormat.getInstance(locale) as? DecimalFormat
        // This should never happen, at least not in stock Java 21:
            ?: getFallbackDecimalFormat(locale)
    }

    @Readonly
    private fun getFallbackDecimalFormat(locale: Locale) =
        DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(locale))


    private class ActiveModsCache(
        val activeMods: HashSet<String>,
        private val cachedHash: Int
    ) {
        fun isValid() = cachedHash == getCurrentHash()

        companion object {
            fun create(): ActiveModsCache {
                val gameInfo = UncivGame.Current.gameInfo
                val fallbackMods = UncivGame.Current.translations.translationActiveMods
                val hash = getCurrentHash(gameInfo, fallbackMods)
                val mods = getCurrentSet(gameInfo, fallbackMods)
                return ActiveModsCache(mods, hash)
            }

            private fun getCurrentHash(
                gameInfo: GameInfo? = UncivGame.Current.gameInfo,
                fallbackMods: HashSet<String> = UncivGame.Current.translations.translationActiveMods
            ): Int {
                return if (gameInfo != null) {
                    hashOf(gameInfo.gameParameters.mods.hashCode(), gameInfo.gameParameters.baseRuleset.hashCode())
                } else {
                    fallbackMods.hashCode()
                }
            }

            private fun getCurrentSet(gameInfo: GameInfo?, fallbackMods: HashSet<String>): HashSet<String> {
                if (gameInfo == null) return fallbackMods
                val par = gameInfo.gameParameters
                // This is equivalent to (par.mods + par.baseRuleset) without the cast down to `Set`
                return LinkedHashSet<String>(par.mods.size + 1).apply {
                    addAll(par.mods)
                    add(par.baseRuleset)
                }
            }
        }
    }

    internal class LanguageCache(
        /** The language this cache was created for */
        val language: String,
        /** A Java [Locale] matching the current [language]
         *  @see LocaleCode.getLocale */
        val locale: Locale,
        /** @see Translations.placeConditionalsAfterUnique */
        val conditionalsAfterUnique: Boolean,
        /** @see Translations.shouldCapitalize */
        val shouldCapitalize: Boolean,
        /** @see Translations.getSpaceEquivalent */
        val spaceEquivalent: String,
        /** @see Translations.getConditionalOrder */
        val sortPriorities: Map<String, Int>,
        /** A Regex able to find numbers that were formatted in the current language _or_ plain positive ASCII digit numbers */
        val numberRegex: Regex,
        /** Cloned in [getMutableFormatter] */
        private val formatterPrototype: DecimalFormat
    ) {
        /** Format [value] - uses a thread-local version of [getNumberFormatter] without cloning every call */
        @Readonly
        fun format(value: Number): String = getMutableFormatter().format(value)

        /** Parse [text] to a Number, updating [pos] - uses a thread-local version of [getNumberFormatter] without cloning every call */
        @Readonly
        fun parse(text: String, pos: ParsePosition): Number? = getMutableFormatter().parse(text, pos)

        /** Reformat a number given as plain ASCII digits or in this language's format; null if it doesn't parse completely */
        @Readonly
        fun reformat(numericString: String): String? {
            val formatter = getMutableFormatter()
            val pos = ParsePosition(0)
            val number = formatter.parse(numericString, pos) ?: return null
            return if (pos.index == numericString.length) formatter.format(number) else null
        }

        /** Get a fresh clone of this language's DecimalFormat, as DecimalFormat is not thread-safe */
        @Readonly
        fun getMutableFormatter() = formatterPrototype.clone() as DecimalFormat

        companion object {
            fun of(language: String): LanguageCache {
                val locale = LocaleCode.getLocale(language)

                val tr = UncivGame.Current.translations
                val conditionalOrdering = tr.getConditionalOrder(language)
                val conditionalsAfterUnique = tr.placeConditionalsAfterUnique(language)
                val shouldCapitalize = tr.shouldCapitalize(language)
                val spaceEquivalent = tr.getSpaceEquivalent(language)

                // Somewhere, we asked the translators to reorder all possible conditionals in a way that
                // makes sense in their language. We get this ordering, and map their placeholderText to their ordinal.
                val sortPriorities = buildMap {
                    conditionalOrdering.getModifierTexts().forEachIndexed { index, text ->
                        put(text.getPlaceholderText(), index)
                    }
                }

                val formatter = getNumberFormat(locale)
                val numberRegex = getNumberRegex(formatter)
                return LanguageCache(language, locale, conditionalsAfterUnique, shouldCapitalize, spaceEquivalent, sortPriorities, numberRegex, formatter)
            }

            private fun StringBuilder.quoted(str: String) = append(Pattern.quote(str))
            private fun StringBuilder.optional(str: String) = when {
                str.isEmpty() -> Unit
                str.length == 1 -> {
                    quoted(str)
                    append('?')
                }
                else -> {
                    append("(?:")
                    quoted(str)
                    append(")?")
                }
            }

            private fun getNumberRegex(formatter: DecimalFormat): Regex {
                val symbols = formatter.decimalFormatSymbols
                val zero = symbols.zeroDigit
                val digit =
                    if (zero == '0') "[0-9]" else "[0-9$zero-${zero + 9}]"   // ASCII plus the locale's own digits, NOT \p{Nd}, because that would re-format e.g. "２"
                val pattern = buildString {
                    // Since we might include a "-", a "\b" won't do:
                    // First, a look-ahead for a (unicode) digit or negativePrefix mitigates the extra cost of a look-behind.
                    append("(?=$digit")
                    if (formatter.negativePrefix.isNotEmpty()) {
                        append('|')
                        quoted(formatter.negativePrefix)
                    }
                    append(')')
                    // Now, do a look-behind for "not a (ascii) letter or digit"
                    append("(?<![A-Za-z0-9_])")
                    // Main number pattern
                    optional(formatter.negativePrefix)
                    append("$digit+")
                    if (formatter.isGroupingUsed) {
                        append("(?:")
                        quoted(symbols.groupingSeparator.toString())
                        append("$digit{")
                        append(formatter.groupingSize)
                        append("})*")
                    }
                    /* Activate the following to allow post-processing numbers with fractions (would change number of decimal places, therefore disabled)
                    append("(?:\\")
                    append(symbols.decimalSeparator)
                    append("$digit+")
                    append(")?")
                    */
                    optional(formatter.negativeSuffix)
                    // Ensure not followed by (ascii) letter or digit, more portable than "\b"
                    append("""(?![A-Za-z0-9_])""")
                }
                return Regex(pattern)
            }
        }
    }
}
