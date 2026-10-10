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

/** Expect a literal [ followed by a captured () group and a literal ].
 * The group may contain any number of any character except ] - pattern [^]] */
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
val squareBraceRegex = Regex("""\[([^]]*)\]""")

// Analogous as above:
/** Expect a literal { followed by a captured () group and a literal }.
 * The group may contain any number of any character except } - pattern [^}] */
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
@VisibleForTesting
val curlyBraceRegex = Regex("""\{([^}]*)\}""")

// Similar to above
/** Expect a <> pair with any chars but > in between and capture that.
 * Includes any trailing blanks in the match, for removeConditionals, and only for cases trimEnd() doesn't cover - those are misformed with text after or between the conditionals.
 * Note: At the time of writing, this had only two use patterns: replace("") and extracting group 1. Any different new use would need reconsideration.
 */
@Suppress("RegExpRedundantEscape") // Some Android versions may need <> escaped
internal val pointyBraceRegex = Regex("""\<([^>]*)\> *""")

private const val supportNestingSquare = true
private const val supportNestingCurly = false
private const val supportNestingPointy = false
private const val supportNestingHelpers = false


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
 *  @param      hideStats disables auto-inserting icons for individual [com.unciv.models.stats.Stat] names, only if the string contains no [],{} or <> brackets.
 *  @return     The translated string
 *                  defaults to the input string if no translation is available,
 *                  but with placeholder or sentence brackets removed.
 */
@Readonly
fun String.tr(hideIcons: Boolean = false, hideStats: Boolean = false) =
    tr(UncivGame.Current.settings.language, hideIcons, hideStats)

@Readonly
fun String.tr(language: String, hideIcons: Boolean = false, hideStats: Boolean = false): String {
    // '<' and '>' checks for quick 'no' answer. Nested "<>" aren't caught here.
    val ltIndex = indexOf('<')
    if (ltIndex >= 0 && indexOf('>', ltIndex + 1) >= 0)
        return translateConditionals(language, hideIcons)

    // curly and square brackets can be nested inside of each other so find the leftmost curly/square
    // bracket then process that first
    val splitIndex = indexOfFirst { it == '[' || it == '{' }
    if (splitIndex < 0)
        return translateIndividualWord(language, hideIcons, hideStats)

    if (get(splitIndex) == '[')
        // Translate placeholders and placeholder-container separately then reassemble
        return translatePlaceholders(language, hideIcons, splitIndex)

    // Translating partial sentences inside {}
    return translatePartial(language, hideIcons, splitIndex)
}

/** Finds a matching closing bracket in [text] starting at [openPos] supporting nesting of [open]/[close] bracket pairs. */
@Readonly
private fun findMatching(text: String, openPos: Int, open: Char, close: Char): Int {
    var depth = 1
    for (pos in openPos + 1 until text.length) {
        val c = text[pos]
        if (c == open) depth++
        else if (c == close && --depth <= 0) return pos
    }
    return -1
}

/** Reports successive [open]/[close] pairs as (gapStart, openPos, closePos); unmatched openers end the scan.
 *  @return start of the unprocessed tail */
@Readonly
private inline fun String.scanPairs(
    open: Char, close: Char,
    firstPos: Int = indexOf(open),
    supportNesting: Boolean,
    onPair: (gapStart: Int, openPos: Int, closePos: Int) -> Unit
): Int {
    var pos = 0
    var openPos = firstPos
    while (openPos >= 0) {
        val closePos = if (supportNesting) findMatching(this, openPos, open, close) else indexOf(close, openPos + 1)
        if (closePos < 0) break
        onPair(pos, openPos, closePos)
        pos = closePos + 1
        openPos = indexOf(open, pos)
    }
    return pos
}

@Readonly
private inline fun String.parseKeyAndPlaceholders(
    firstPos: Int = indexOf('['),
    supportNesting: Boolean,
    appendToKey: (String, Int, Int) -> Unit = { _, _, _ -> }
): List<String> {
    val placeholderContents = mutableListOf<String>()
    val tail = scanPairs('[', ']', firstPos, supportNesting) { gapStart, openPos, closePos ->
        appendToKey(this, gapStart, openPos)
        appendToKey("[]", 0, 2)
        placeholderContents += substring(openPos + 1, closePos)
    }
    appendToKey(this, tail, length)
    return placeholderContents
}

@Readonly
private fun String.translatePartial(language: String, hideIcons: Boolean, firstPos: Int): String {
    @LocalState
    val sb = StringBuilder(length + 16)
    val tail = scanPairs('{', '}', firstPos, supportNestingCurly) { gapStart, openPos, closePos ->
        sb.append(this, gapStart, openPos)
        sb.append(substring(openPos + 1, closePos).tr(language, hideIcons))
    }
    sb.append(this, tail, length)
    return sb.toString()
}

@Readonly
private fun String.translatePlaceholders(language: String, hideIcons: Boolean, firstPos: Int): String {
    /**
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
    // Collect the message without placeholder content, only "[]"
    @LocalState
    val key = StringBuilder(length)
    // Collect the terms in the message, WITHOUT square brackets
    val termsInMessage = parseKeyAndPlaceholders(firstPos, supportNestingSquare) { sb, start, end -> key.append(sb, start, end) }
    // For input "work on [building] has completed in [city]", `key` would now contain "work on [] has completed in []"

    // That is now the key into the translation HashMap!
    val translationEntry = UncivGame.Current.translations
        .get(key.toString(), language, TranslationActiveModsCache.activeMods)

    val translatedWithPlaceholders: String
    val originalWithPlaceholders: String
    if (translationEntry == null || !translationEntry.containsKey(language)) {
        // Translation entry doesn't exist for this language, default to English
        translatedWithPlaceholders = this
        originalWithPlaceholders = this
    } else {
        translatedWithPlaceholders = translationEntry[language]!!
        originalWithPlaceholders = translationEntry.entry
    }

    // Take the terms from the placeholder - same helper as above but discarding the parts outside the [] brackets
    val termsInTranslationPlaceholder = originalWithPlaceholders.parseKeyAndPlaceholders(supportNesting = supportNestingSquare)
    // `termsInTranslationPlaceholder` are the "names" like [unit], not the content to translate, like [Warrior], and we use them to match
    // corresponding terms in the translated string, to replace them with the actual message's placeholdercontent, translated.
    // This is what makes reordered placeholders in a translation work.
    if (termsInMessage.size != termsInTranslationPlaceholder.size)
        throw Exception("Message $this has a different number of terms than the placeholder $translationEntry!")
    val substitutions = HashMap<String, String>()
    for (i in termsInMessage.indices) {
        val translated = termsInMessage[i].tr(language, hideIcons)
        substitutions.putIfAbsent(termsInTranslationPlaceholder[i], translated)
    }

    val sb = StringBuilder(translatedWithPlaceholders.length + 16)
    val tail = translatedWithPlaceholders.scanPairs('[', ']', supportNesting = supportNestingSquare) { gapStart, openPos, closePos ->
        sb.append(translatedWithPlaceholders, gapStart, openPos)
        val replacement = substitutions[translatedWithPlaceholders.substring(openPos + 1, closePos)]
        if (replacement != null)
            sb.append(replacement)
        else
            sb.append(translatedWithPlaceholders, openPos, closePos + 1)
    }
    sb.append(translatedWithPlaceholders, tail, translatedWithPlaceholders.length)
    return sb.toString()
}

@Readonly
private fun String.translateConditionals(language: String, hideIcons: Boolean): String {
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
    val stringToParse = this.removeConditionals()
    val firstPos = stringToParse.indexOf('[')
    if (firstPos < 0) return emptyList()
    return stringToParse.parseKeyAndPlaceholders(firstPos, supportNestingSquare)
}

@Pure
fun String.getPlaceholderText(): String {
    val stringToParse = this.removeConditionals()
    val firstPos = stringToParse.indexOf('[')
    if (firstPos < 0) return stringToParse
    val sb = StringBuilder(length)
    stringToParse.parseKeyAndPlaceholders(firstPos, supportNestingSquare) { text, start, end -> sb.append(text, start, end) }
    return sb.toString()
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
    if (!contains('[')) return false
    val text = removeConditionals()
    val openPos = text.indexOf('[')
    // This will return true for "[a[b]", while getPlaceholderParameters returns an emptyList for it
    // This un-balance was present in the Regex-based version, too. To "fix" use findMatching(text, openPos, '[', ']') >= 0
    return openPos >= 0 && text.indexOf(']', openPos + 1) >= 0
}

/** Substitutes placeholders with [strings], respecting order of appearance.
 *  - Note this does not support nested [] brackets, unlike the translation engine.
 *    (to "fix", use [findMatching] for the `val closePos =` line)
 *  - Note this does not recogize conditionals, given enough [strings], it might fill out placeholders inside those too.
 */
@Pure
fun String.fillPlaceholders(vararg strings: String): String {
    @Immutable val keys = getPlaceholderParameters()
    if (keys.size > strings.size)
        throw IllegalArgumentException("String $this has a different number of placeholders ${keys.joinToString()} (${keys.size}) than the substitutive strings ${strings.joinToString()} (${strings.size})!")

    @LocalState
    val sb = StringBuilder(length + 16)
    var index = 0
    val tail = scanPairs('[', ']', supportNesting = supportNestingHelpers) { gapStart, openPos, _ ->
        sb.append(this, gapStart, openPos)
        if (index < strings.size) {
            sb.append('[')
            sb.append(strings[index])
            sb.append(']')
        } else {
            sb.append("[]")
        }
        index++
    }
    sb.append(this, tail, length)
    return sb.toString()
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
    val result = replace(pointyBraceRegex, "")
    return if (result.length == length) this else result.trimEnd()
}

/** For use in [UniqueValidator][com.unciv.models.ruleset.validation.UniqueValidator]
 *
 *  Note: Whether nested modifiers validate depends on whether removeConditionals supports nesting.
 *  @return null if conditionals are well-formed, else a description of the first problem found.
 */
@Readonly
fun String.validateConditionals() = validateConditionals(0)
@VisibleForTesting
const val MAX_CONDITIONAL_NESTING = 666
@Readonly
private fun String.validateConditionals(recursionDepth: Int): String? {
    if ('<' !in this) return null
    val base = removeConditionals()
    if ('<' in base || '>' in base) return "mismatched conditional braces"
    if (!trimStart().startsWith(base.trimStart())) return "text between or after conditionals"
    if (recursionDepth >= MAX_CONDITIONAL_NESTING) return "excessive nesting of conditionals"
    return getModifiers().firstNotNullOfOrNull { it.text.validateConditionals(recursionDepth + 1) }
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
