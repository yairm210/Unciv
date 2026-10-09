package com.unciv.models.translations

import com.unciv.UncivGame
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.fonts.FontRulesetIcons
import java.text.ParsePosition
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Immutable
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

// We don't need to allocate different memory for these every time we .tr() - or recompile them.
// Please note: The extra \] and \} are NOT removable, despite what Android Studio might recommend -
//   they are necessary for Android Java 6 phones to parse the regex properly!

// Expect a literal [ followed by a captured () group and a literal ].
// The group may contain any number of any character except ] - pattern [^]]
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
val squareBraceRegex = Regex("""\[([^]]*)\]""")

// Analogous as above: Expect a {} pair with any chars but } in between and capture that
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
@VisibleForTesting
val curlyBraceRegex = Regex("""\{([^}]*)\}""")

// Analogous as above: Expect a <> pair with any chars but > in between and capture that
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
internal val pointyBraceRegex = Regex("""\<([^>]*)\>""")


/**
 *  This function does the actual translation work,
 *      using an instance of [Translations] stored in UncivGame.Current
 *
 *  @receiver   The string to be translated, can take three forms:
 *                  plain - translated directly by hashset lookup
 *                  placeholders - contains at least one '[' - see below
 *                  sentences - contains at least one '{'
 *                  - phrases between curly braces are translated individually
 *                  Additionally, they may contain conditionals between '<' and '>'
 *  @param      hideIcons disables auto-inserting icons for ruleset objects and serialized [com.unciv.models.stats.Stats]
 *  @param      hideStats disables auto-inserting icons for individual [com.unciv.models.stats.Stat] names
 *  @return     The translated string
 *                  defaults to the input string if no translation is available,
 *                  but with placeholder or sentence brackets removed.
 */
@Readonly
fun String.tr(hideIcons: Boolean = false, hideStats: Boolean = false): String {
    val language: String = UncivGame.Current.settings.language

    // '<' and '>' checks for quick 'no' answer. Nested "<>" aren't caught here.
    val ltIndex = indexOf('<')
    if (ltIndex >= 0 && indexOf('>', ltIndex + 1) >= 0)
        return translateConditionals(hideIcons, language)

    // curly and square brackets can be nested inside of each other so find the leftmost curly/square
    // bracket then process that first
    val splitIndex = indexOfFirst { it == '[' || it == '{' }
    if (splitIndex < 0)
        return translateIndividualWord(language, hideIcons, hideStats)

    if (get(splitIndex) == '[')
        // Translate placeholders and placeholder-container separately then reassemble
        return translatePlaceholders(language, hideIcons)

    // Translating partial sentences inside {}
    return curlyBraceRegex.replace(this) { it.groups[1]!!.value.tr(hideIcons) }
}

@Readonly
private fun String.translateConditionals(hideIcons: Boolean, language: String): String {
    /**
     * So conditionals can contain placeholders, such as <vs [unitFilter] units>, which themselves
     * can contain multiple filters, such as <vs [{Military} {Water}] units>.
     * Moreover, we can have any amount of conditionals in any order, and translations
     * can reorder these conditionals in any way they like, even putting them in front
     * of the rest of the translatable string.
     * All of this nesting makes it quite difficult to translate, and is the reason we check
     * for these first.
     *
     * The plan: First translate each of the conditionals on its own, and then combine them
     * together into the final fully translated string.
     */

    // Get all special parameters stored in the translation files
    val conditionalOrdering = UncivGame.Current.translations.getConditionalOrder(language)
    val conditionalsAfterUnique = UncivGame.Current.translations.placeConditionalsAfterUnique(language)
    val shouldCapitalize = UncivGame.Current.translations.shouldCapitalize(language)
    val spaceEquivalent = UncivGame.Current.translations.getSpaceEquivalent(language)

    // Somewhere, we asked the translators to reorder all possible conditionals in a way that
    // makes sense in their language. We get this ordering, and map their placeholderText to their ordinal.
    val sortPriorities: Map<String, Int> = pointyBraceRegex
        .findAll(conditionalOrdering)
        .map { it.groupValues[1].getPlaceholderText() }
        .withIndex()
        .associate { (index, key) -> key to index }

    // Now sort the input conditionals by looking their sort priority up (sorting to the end if none defined).
    // Remember this sort operator is stable - entries with identical priority keep their original ordering,
    // so if the conditional order definition is empty, all conditionals keep their place.
    fun sortPriority(unique: Unique) = sortPriorities.getOrDefault(unique.placeholderText, Int.MAX_VALUE)
    val translatedConditionals = this.getModifiersSequence()
        .sortedBy(::sortPriority)
        .map { it.text.tr(hideIcons) }

    // After that, add the translation of the base unique either before or after these conditionals
    val translatedBaseUnique = this.removeConditionals().tr(hideIcons)
    val translatedComponents = sequence {
        if (conditionalsAfterUnique) yield(translatedBaseUnique)
        yieldAll(translatedConditionals)
        if (!conditionalsAfterUnique)
            if (shouldCapitalize) yield(translatedBaseUnique.replaceFirstChar { it.lowercase() })
            else yield(translatedBaseUnique)
    }

    var fullyTranslatedString = translatedComponents.joinToString(spaceEquivalent)
    if (shouldCapitalize)
        fullyTranslatedString = fullyTranslatedString.replaceFirstChar { it.uppercase() }
    return fullyTranslatedString
}

@Readonly
private fun String.translatePlaceholders(language: String, hideIcons: Boolean): String {
    /**
     * I'm SURE there's an easier way to do this but I can't think of it =\
     * So what's all this then?
     * Well, not all languages are like English. So say I want to say "work on Library has completed in Akkad",
     * but in a completely different language like Japanese or German,
     * It could come out "Akkad hast die worken onner Library gerfinishen" or whatever,
     * basically, the order of the words in the sentence is not guaranteed.
     * So to translate this, I give a sentence like "work on [building] has completed in [city]"
     * and the german can put those placeholders where he wants, so  "[city] hast die worken onner [building] gerfinishen"
     * The string on which we call tr() will look like "work on [library] has completed in [Akkad]"
     * We will find the german placeholder text, and replace the placeholders with what was filled in the text we got!
     */

    // Convert "work on [building] has completed in [city]" to "work on [] has completed in []"
    val translationStringWithSquareBracketsOnly = this.getPlaceholderText()
    // That is now the key into the translation HashMap!
    val translationEntry = UncivGame.Current.translations
        .get(translationStringWithSquareBracketsOnly, language, TranslationActiveModsCache.activeMods)

    var languageSpecificPlaceholder: String
    val originalEntry: String
    if (translationEntry == null || !translationEntry.containsKey(language)) {
        // Translation placeholder doesn't exist for this language, default to English
        languageSpecificPlaceholder = this
        originalEntry = this
    } else {
        languageSpecificPlaceholder = translationEntry[language]!!
        originalEntry = translationEntry.entry
    }

    // Take the terms in the message, WITHOUT square brackets
    val termsInMessage = this.getPlaceholderParameters()
    // Take the terms from the placeholder
    val termsInTranslationPlaceholder = originalEntry.getPlaceholderParameters()
    if (termsInMessage.size != termsInTranslationPlaceholder.size)
        throw Exception("Message $this has a different number of terms than the placeholder $translationEntry!")

    for (i in termsInMessage.indices) {
        languageSpecificPlaceholder = languageSpecificPlaceholder.replace(
            "[${termsInTranslationPlaceholder[i]}]", // re-add square brackets to placeholder terms
            termsInMessage[i].tr(hideIcons)
        )
    }
    return languageSpecificPlaceholder      // every component is already translated
}


/** No brackets of any kind, just a single word */
@Readonly
private fun String.translateIndividualWord(language: String, hideIcons: Boolean, hideStatIcons: Boolean): String {
    if (Stats.isStats(this)) {
        val stats = Stats.parse(this)
        return if (hideStatIcons) stats.toStringWithoutIcons() else stats.toString()
    }

    val translation = UncivGame.Current.translations.getText(
        this, language, TranslationActiveModsCache.activeMods
    ).replace(LocaleCode.getNumberRegexForLanguage(language)) {
        val pos = ParsePosition(0)
        val formatter = LocaleCode.getNumberFormatFromLanguage(language)
        val numericString = it.value
        formatter.parse(numericString, pos)
            .takeIf { pos.index == numericString.length }
            ?.let { number -> formatter.format(number) }
            ?: numericString
    }

    val stat = Stat.safeValueOf(this)
    if (!hideStatIcons && stat != null) return stat.character + translation

    if (!hideIcons && FontRulesetIcons.rulesetObjectNameToChar.containsKey(this))
        return FontRulesetIcons.rulesetObjectNameToChar[this]!! + translation

    return translation
}


/**
 * Finds the parameters in a string while IGNORING the lower-leveled braces.
 * For example, a string like 'The city of [New [York]]' will return ['New [York]'],
 * allowing us to have nested translations!
 */
@Pure
fun String.getPlaceholderParameters(): List<String> {
    if (!this.contains('[')) return emptyList()

    val stringToParse = this.removeConditionals()

    val parameters = ArrayList<String>()
    var depthOfBraces = 0
    var startOfCurrentParameter = -1
    stringToParse.indices.forEach { i ->
        val currentChar = stringToParse[i]
        if (currentChar == '[') {
            if (depthOfBraces == 0) startOfCurrentParameter = i+1
            depthOfBraces++
        }
        if (currentChar == ']' && depthOfBraces > 0) {
            depthOfBraces--
            if (depthOfBraces == 0) parameters.add(substring(startOfCurrentParameter,i))
        }
    }
    return parameters
}

@Pure
fun String.getPlaceholderText(): String {
    var stringToReturn = this.removeConditionals()
    @LocalState val placeholderParameters = stringToReturn.getPlaceholderParameters()
    placeholderParameters.forEach { placeholderParameter ->
        stringToReturn = stringToReturn.replaceFirst("[$placeholderParameter]", "[]")
    }
    return stringToReturn
}

@Pure
fun String.equalsPlaceholderText(str: String): Boolean {
    if (isEmpty()) return str.isEmpty()
    if (str.isEmpty()) return false // Empty strings have no .first()
    if (first() != str.first()) return false // for quick negative return 95% of the time
    return this.getPlaceholderText() == str
}

@Pure
fun String.hasPlaceholderParameters(): Boolean {
    if (!this.contains('[')) return false
    return squareBraceRegex.containsMatchIn(this.removeConditionals())
}

/** Substitutes placeholders with [strings], respecting order of appearance. */
@Pure
fun String.fillPlaceholders(vararg strings: String): String {
    @Immutable val keys = this.getPlaceholderParameters()
    if (keys.size > strings.size)
        throw Exception("String $this has a different number of placeholders ${keys.joinToString()} (${keys.size}) than the substitutive strings ${strings.joinToString()} (${strings.size})!")

    var filledString = this.replace(squareBraceRegex, "[]")
    keys.indices.forEach { i ->
        filledString = filledString.replaceFirst("[]", "[${strings[i]}]")
    }
    return filledString
}

@Pure
private fun String.getModifiersSequence(): Sequence<Unique> {
    if (!this.contains('<')) return emptySequence()
    @Immutable val matchResults = pointyBraceRegex.findAll(this)
    return matchResults.map { Unique(it.groups[1]!!.value) }
}

@Readonly
fun String.getModifiers() = getModifiersSequence().toList()

@Pure
fun String.removeConditionals(): String {
    if (!this.contains('<')) return this // no need to regex search
    return this
        .replace(pointyBraceRegex, "")
        // So, this is a quick hack, but it works as long as nobody uses word separators different from " " (space) and "" (none),
        // and no translations start or end with a space.
        // According to https://linguistics.stackexchange.com/questions/6131/is-there-a-long-list-of-languages-whose-writing-systems-dont-use-spaces
        // This is a reasonable but not fully correct assumption to make.
        // By doing it like this, we exclude languages such as Tibetan, Dzongkha (Bhutan), and Ethiopian.
        // If we ever start getting translations for these, we'll work something out then.
        .replace("  ", " ")
        .trim()
}

/** Formats number according to current language
 *
 *  Note: The inverse operation is UncivGame.Current.settings.getCurrentNumberFormat().parse(string), handled in the [UncivTextField.Numeric][com.unciv.ui.components.widgets.UncivTextField.Numeric] widget.
 *
 *  @return locale-dependent String representation of receiver, may contain formatting like thousands separators
 */
@Readonly
fun Number.tr(): String {
    return UncivGame.Current.settings.getCurrentNumberFormat().format(this)
}

/** Formats number according to a specific [language]
 *
 *  Note: The inverse operation is `LocaleCode.getNumberFormatFromLanguage(language).parse(string)`.
 *
 *  @return locale-dependent String representation of receiver, may contain formatting like thousands separators
 */
@Readonly
fun Number.tr(language: String): String {
    return LocaleCode.getNumberFormatFromLanguage(language).format(this)
}
