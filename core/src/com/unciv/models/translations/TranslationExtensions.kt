package com.unciv.models.translations

import com.unciv.UncivGame
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.fonts.FontRulesetIcons
import java.text.ParsePosition
import java.util.Locale
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/** If you really need one of these for a different purpose - think again, they don't support nesting - and if you're sure, copy them elsewhere */
@Suppress("unused")
private object ObsoleteRegexes {
    // We don't need to allocate different memory for these every time we .tr() - or recompile them.
    // Please note: The extra \] and \} are NOT removable, despite what Android Studio might recommend -
    //   they are necessary for Android Java 6 phones to parse the regex properly!

    /** Expect a literal [ followed by a captured () group and a literal ].
     * The group may contain any number of any character except ] - pattern [^]] */
    @Deprecated("Use getPlaceholderText, getPlaceholderParameters, hasPlaceholderParameters or fillPlaceholders instead")
    @Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
    val squareBraceRegex = Regex("""\[([^]]*)\]""")

    // Analogous as above:
    /** Expect a literal { followed by a captured () group and a literal }.
     * The group may contain any number of any character except } - pattern [^}] */
    @Deprecated("Use getPartsInsideCurlies instead")
    @Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
    val curlyBraceRegex = Regex("""\{([^}]*)\}""")

    // Similar to above
    /** Expect a <> pair with any chars but > in between and capture that.
     * Includes any trailing blanks in the match, for removeConditionals, and only for cases trimEnd() doesn't cover - those are misformed with text after or between the conditionals.
     * Note: At the time of writing, this had only two use patterns: replace("") and extracting group 1. Any different new use would need reconsideration.
     */
    @Deprecated("Use getModifiers, getModifierTexts, removeConditionals instead")
    @Suppress("RegExpRedundantEscape") // Some Android versions may need <> escaped
    val pointyBraceRegex = Regex("""\<([^>]*)\> *""")
}

/** Affects scanning in [String.translatePlaceholders], [String.getPlaceholderParameters] and [String.getPlaceholderText] */
private const val supportNestingSquare = true
/** Affects the replacement in [String.translatePlaceholders]: `false` can replace the result of previous substitutions, `true` is single-pass and cannot. */
private const val supportNestingSquareReplacement = true
/** Affects [String.translatePartial] */
private const val supportNestingCurly = true
/** Affects [String.translateConditionals], [String.removeConditionals], [String.getModifiers] */
private const val supportNestingPointy = true
/** Affects [String.fillPlaceholders] */
private const val supportNestingHelpers = true


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
    tr(TranslationCache.language, hideIcons, hideStats)

@Readonly
fun String.tr(language: String, hideIcons: Boolean = false, hideStats: Boolean = false): String {
    // '<' and '>' checks for quick 'no' answer. Nested "<>" aren't caught here.
    val ltIndex = indexOf('<')
    if (ltIndex >= 0 && indexOf('>', ltIndex + 1) >= 0)
        return translateConditionals(language, hideIcons, ltIndex)

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

/** Scans for [open]/[close] pairs
 * - Reports pairs as [emitInside]`(startPos, endPos)`.
 * - Reports string portions outside pairs as [emitOutside]`(startPos, endPos)`.
 * - Both 'emit' lambdas are passed this function's `this` as receiver.
 *   Pay attention if the call is inside a String extension but `parsePairs` is called on a different sting -
 *   `this` in the lambdas will refer to that other string, not the caller's receiver.
 * - Order is preserved - when a pair is found, the preceding outside tail is reported before the inside.
 * - With [supportNesting] off, an [open] is always matched with the next [close].
 * - With [supportNesting] ON, depth is tracked so an [open] is matched with the same-depth [close],
 *   and the string reported to [emitInside] can in turn contain [open]/[close] pairs.
 * - Unmatched openers end the scan (though it would be easy to report those as "inside", but that would need unit test adjustment).
 */
@Readonly
private inline fun String.parsePairs(
    open: Char, close: Char,
    firstPos: Int = indexOf(open),
    supportNesting: Boolean,
    emitInside: String.(start: Int, end: Int) -> Unit,
    emitOutside: String.(start: Int, end: Int) -> Unit
) {
    var pos = 0
    var openPos = firstPos
    while (openPos >= 0) {
        val closePos = if (supportNesting) findMatching(this, openPos, open, close) else indexOf(close, openPos + 1)
        if (closePos < 0) break
        emitOutside(pos, openPos)
        emitInside(openPos + 1, closePos)
        pos = closePos + 1
        openPos = indexOf(open, pos)
    }
    emitOutside(pos, length)
}

/** Helper for [removeConditionals] and the related the conditionals scan in [translateConditionals]
 *
 *  Manages trimming the "outside conditionals" part the same way the regex did
 */
private fun StringBuilder.appendTrimming(input: String, start: Int, end: Int) {
    if (start >= end || start >= input.length) return
    var start = start
    if (start > 0)
        while (start < end && input[start] == ' ') start++
    append(input, start, end)
}

/** Do a `trimEnd` directly on [StringBuilder] memory
 *
 * (otherwise `sb.trimEnd()` would use the one on [CharSequence] which allocates and returns a copy) */
private fun StringBuilder.trimEnd() {
    val trimPos = indexOfLast { !it.isWhitespace() } + 1
    if (trimPos < length) deleteRange(trimPos, length)
}

@Readonly
private fun String.translatePartial(language: String, hideIcons: Boolean, firstPos: Int): String {
    @LocalState
    val sb = StringBuilder(length + 16)
    parsePairs('{', '}', firstPos, supportNestingCurly,
        emitInside = { start, end -> sb.append( substring(start, end).tr(language, hideIcons) ) },
        emitOutside = { start, end -> sb.append(this, start, end) }
    )
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
    @LocalState val key = StringBuilder(length)
    // Collect the terms in the message, WITHOUT square brackets
    @LocalState val termsInMessage = mutableListOf<String>()
    parsePairs('[', ']', firstPos, supportNestingSquare,
        emitInside = { start, end ->
            termsInMessage += substring(start, end)
            key.append("[]")
        },
        emitOutside = { start, end -> key.append(this, start, end) }
    )
    // For input "work on [building] has completed in [city]", `key` would now contain "work on [] has completed in []"

    // That is now the key into the translation HashMap!
    val translationEntry = UncivGame.Current.translations
        .get(key.toString(), language, TranslationCache.activeMods)

    val translatedWithPlaceholders: String
    val originalWithPlaceholders: String
    val languageEntry = translationEntry?.get(language)
    if (languageEntry != null) {
        translatedWithPlaceholders = languageEntry
        originalWithPlaceholders = translationEntry.entry
    } else {
        translatedWithPlaceholders = this
        originalWithPlaceholders = this
    }

    // Take the terms from the placeholder - same helper as above but discarding the parts outside the [] brackets
    @LocalState val termsInTranslationPlaceholder = mutableListOf<String>()
    originalWithPlaceholders.parsePairs('[', ']', supportNesting = supportNestingSquare,
        emitInside = { start, end -> termsInTranslationPlaceholder += substring(start, end) },
        emitOutside = { _, _ -> }
    )
    // `termsInTranslationPlaceholder` are the "names" like [unit], not the content to translate, like [Warrior], and we use them to match
    // corresponding terms in the translated string, to replace them with the actual message's placeholdercontent, translated.
    // This is what makes reordered placeholders in a translation work.
    if (termsInMessage.size != termsInTranslationPlaceholder.size)
        throw Exception("Message $this has a different number of terms than the placeholder $translationEntry!")

    if (supportNestingSquareReplacement) {
        val substitutions = HashMap<String, String>()
        for (i in termsInMessage.indices) {
            val translated = termsInMessage[i].tr(language, hideIcons)
            substitutions.putIfAbsent(termsInTranslationPlaceholder[i], translated)
        }

        val sb = StringBuilder(translatedWithPlaceholders.length + 16)
        translatedWithPlaceholders.parsePairs('[', ']', supportNesting = true,
            emitInside = { start, end ->
                val replacement = substitutions[substring(start, end)]
                if (replacement != null)
                    sb.append(replacement)
                else
                    sb.append(this, start, end)
            },
            emitOutside = { start, end -> sb.append(this, start, end) }
        )
        return sb.toString()
    } else {
        // TODO Legacy implementation, has the weakness that a later replacement can hit the result of an earlier replacement in weird cases.
        //      Remove this else and the `supportNestingSquareReplacement` when nesting support is turned on and changes ot unit tests are checked.
        var languageSpecificPlaceholder = translatedWithPlaceholders
        for (i in termsInMessage.indices) {
            languageSpecificPlaceholder = languageSpecificPlaceholder.replace(
                "[${termsInTranslationPlaceholder[i]}]", // re-add square brackets to placeholder terms
                termsInMessage[i].tr(hideIcons)
            )
        }
        return languageSpecificPlaceholder
    }
}

@Readonly
private fun String.translateConditionals(language: String, hideIcons: Boolean, firstPos: Int): String {
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

    val langData = TranslationCache.getLangData()
    val locale = langData.locale
    val conditionalsAfterUnique = langData.conditionalsAfterUnique
    val shouldCapitalize = langData.shouldCapitalize
    val spaceEquivalent = langData.spaceEquivalent
    val sortPriorities = langData.sortPriorities

    // Parse the text into base and modifiers
    val conditionalContents = mutableListOf<String>()
    val base = StringBuilder(length)
    parsePairs('<', '>', firstPos, supportNestingPointy,
        emitInside = { start, end -> conditionalContents += substring(start, end) },
        emitOutside = { start, end -> base.appendTrimming(this, start, end) }
    )
    base.trimEnd()

    // Sort the input conditionals by looking their sort priority up (sorting to the end if none defined).
    // Remember this sort operator is stable - entries with identical priority keep their original ordering,
    // so if the conditional order definition is empty, all conditionals keep their place.
    fun sortPriority(modifierText: String) = sortPriorities.getOrDefault(modifierText.getPlaceholderText(), Int.MAX_VALUE)
    @Suppress("purity") // mapTo into our own local state
    val translatedConditionals = conditionalContents
        .sortedBy(::sortPriority)
        .mapTo(ArrayList(conditionalContents.size)) { it.tr(language, hideIcons) }

    // After that, add the translation of the base unique either before or after these conditionals
    val translatedBaseUnique = base.toString().tr(language, hideIcons)
    @LocalState val sb = StringBuilder(length + 16) // Would be more readable with `buildString`, but: @Purity
    fun appendChangingFirstChar(text: String, transform: Char.(Locale) -> String) {
        if (text.isEmpty()) return
        if (!shouldCapitalize) {
            sb.append(text)
            return
        }
        sb.append(text.first().transform(locale))
        sb.append(text, 1, text.length)
    }

    if (conditionalsAfterUnique)
        appendChangingFirstChar(translatedBaseUnique, Char::uppercase)
    var capitalizeOnce = !conditionalsAfterUnique // Also happens to be the case where no separating spaceEquivalent is needed
    for (translatedConditional in translatedConditionals) {
        if (capitalizeOnce) {
            appendChangingFirstChar(translatedConditional, Char::uppercase)
            capitalizeOnce = false
        } else {
            sb.append(spaceEquivalent)
            sb.append(translatedConditional)
        }
    }
    if (!conditionalsAfterUnique) {
        sb.append(spaceEquivalent)
        appendChangingFirstChar(translatedBaseUnique, Char::lowercase)
    }
    return sb.toString()
}

/** No brackets of any kind, just a single word */
@Readonly
private fun String.translateIndividualWord(language: String, hideIcons: Boolean, hideStatIcons: Boolean): String {
    if (Stats.isStats(this)) {
        val stats = Stats.parse(this)
        return if (hideStatIcons) stats.toStringWithoutIcons() else stats.toString()
    }

    val translationBeforeNumberScan = UncivGame.Current.translations.getText(this, language, TranslationCache.activeMods)
    val translation = if (translationBeforeNumberScan.none { it.isDigit() }) translationBeforeNumberScan else {
        val langData = TranslationCache.getLangData()
        translationBeforeNumberScan.replace(langData.numberRegex) {
            val numericString = it.value
            langData.reformat(numericString) ?: numericString
        }
    }

    val stat = Stat.safeValueOf(this)
    if (!hideStatIcons && stat != null) return stat.character + translation

    if (hideIcons) return translation
    val iconChar = FontRulesetIcons.rulesetObjectNameToChar[this]
    return if (iconChar == null) translation else iconChar + translation
}


/**
 * Finds the parameters in a string while IGNORING the lower-leveled braces.
 * For example, a string like 'The city of [New [York]]' will return ['New [York]'],
 * allowing us to have nested translations!
 * Meant for Unique parsing, and will therefore remove conditionals first.
 * @see getPlaceholderText
 */
@Pure
fun String.getPlaceholderParameters(): List<String> {
    val stringToParse = this.removeConditionals()
    val firstPos = stringToParse.indexOf('[')
    if (firstPos < 0) return emptyList()

    @LocalState val parameters = mutableListOf<String>()
    stringToParse.parsePairs('[', ']', firstPos, supportNestingSquare,
        emitInside = { start, end -> parameters += substring(start, end) },
        emitOutside = { _, _ ->  }
    )
    return parameters
}

/**
 * Gets a translation key or [Unique.placeholderText] for the input.
 * All parameters delimited inside `[]` get replaced by `[]`, supporting nesting.
 * Removes conditionals first.
 * @see getPlaceholderParameters
 */
@Pure
fun String.getPlaceholderText(): String {
    val stringToParse = this.removeConditionals()
    val firstPos = stringToParse.indexOf('[')
    if (firstPos < 0) return stringToParse

    @LocalState val key = StringBuilder(length)
    stringToParse.parsePairs('[', ']', firstPos, supportNestingSquare,
        emitInside = { _, _ -> key.append("[]") },
        emitOutside = { start, end -> key.append(this, start, end) }
    )
    return key.toString()
}

/**
 *  Compares `this` string's [getPlaceholderText] with [str].[getPlaceholderText] with an optimization.
 */
@Pure
fun String.equalsPlaceholderText(str: String): Boolean {
    if (isEmpty()) return str.isEmpty()
    if (str.isEmpty()) return false // Empty strings have no .first()
    if (first() != str.first()) return false // for quick negative return 95% of the time
    return this.getPlaceholderText() == str
}

/**
 *  Quickly tests whether `this` contains placeholder parameters
 *  @see getPlaceholderParameters
 */
@Pure
fun String.hasPlaceholderParameters(): Boolean {
    if (!contains('[')) return false
    val text = removeConditionals()
    val openPos = text.indexOf('[')
    // TODO This will return true for "[a[b]", while getPlaceholderParameters returns an emptyList for it
    //      This un-balance was present in the Regex-based version, too. To "fix" use findMatching(text, openPos, '[', ']') >= 0
    return openPos >= 0 && text.indexOf(']', openPos + 1) >= 0
}

/** Substitutes placeholders with [strings], respecting order of appearance.
 *  - Note this does not support nested [] brackets, unlike the translation engine.
 *    (to "fix", use [findMatching] for the `val closePos =` line)
 *  - Note this does not recogize conditionals, given enough [strings], it might fill out placeholders inside those too.
 */
@Pure
fun String.fillPlaceholders(vararg strings: String): String {
    @LocalState val sb = StringBuilder(length + 16)
    var index = 0
    fun substituteNext() {
        if (index < strings.size) {
            sb.append('[')
            sb.append(strings[index])
            sb.append(']')
        } else {
            sb.append("[]")
        }
        index++
    }
    parsePairs('[', ']', supportNesting = supportNestingHelpers,
        emitInside = { _, _ -> substituteNext() },
        emitOutside = { start, end -> sb.append(this, start, end) }
    )
    if (index > strings.size)
        throw IllegalArgumentException("String $this has a different number of placeholders ($index) than the substitutive strings ${strings.joinToString()} (${strings.size})!")
    return sb.toString()
}

/** @return Parts inside `{}` for TranslationFileWriter */
@VisibleForTesting
fun String.getPartsInsideCurlies(): List<String> {
    @LocalState val result = mutableListOf<String>()
    parsePairs('{', '}', supportNesting = supportNestingCurly,
        emitInside = { start, end -> result.add(substring(start, end)) },
        emitOutside = { _, _ -> }
    )
    return result
}

/** @return Modifier texts as Sequence */
@VisibleForTesting
fun String.getModifierTexts() = sequence {
    parsePairs('<', '>', supportNesting = supportNestingPointy,
        emitInside = { start, end -> yield(substring(start, end)) },
        emitOutside = { _, _ -> }
    )
}

/**
 *  @return Modifiers (parts between `<` and `>`) in `this` translated to [Unique]s.
 *  @see removeConditionals
 *  @see supportNestingPointy
 */
@Readonly
fun String.getModifiers(): List<Unique> {
    // Could be mapped from getModifierTexts, but let's save one SequenceBuilder and one Sequence allocation
    @LocalState val result = mutableListOf<Unique>()
    parsePairs('<', '>', supportNesting = supportNestingPointy,
        emitInside = { start, end -> result.add(Unique(substring(start, end))) },
        emitOutside = { _, _ -> }
    )
    return result
}

/**
 *  Returns `this` with Modifiers (parts between `<` and `>`) removed, right-trimmed (any Unicode whitespace).
 *  Any Spaces to the right of a modifier are removed during scanning
 *  (affects output only if the rule to place conditionals only at the end wasn't followed)
 *  @see getModifiers
 *  @see supportNestingPointy
 */
@Pure
fun String.removeConditionals(): String {
    val firstPos = indexOf('<')
    if (firstPos < 0) return this
    @LocalState val sb = StringBuilder(length)
    parsePairs('<', '>', supportNesting = supportNestingPointy,
        emitInside = { _, _ -> },
        emitOutside = { start, end -> sb.appendTrimming(this, start, end) }
    )
    sb.trimEnd()
    return sb.toString()
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
    if (recursionDepth > 9)
        println()
    if (recursionDepth >= MAX_CONDITIONAL_NESTING) return "excessive nesting of conditionals"
    return getModifiers().firstNotNullOfOrNull { it.text.validateConditionals(recursionDepth + 1) }
}

/** Formats number according to current language
 *
 *  Note: The inverse operation is TranslationCache.getNumberFormatter().parse(string), handled in the [UncivTextField.Numeric][com.unciv.ui.components.widgets.UncivTextField.Numeric] widget.
 *
 *  @return locale-dependent String representation of receiver, may contain formatting like thousands separators
 */
@Readonly
fun Number.tr(): String {
    return TranslationCache.getLangData().format(this)
}

/** Formats number according to a specific [language]
 *
 *  Note: The inverse operation is `TranslationCache.getNumberFormatForLanguage(language).parse(string)`.
 *
 *  @return locale-dependent String representation of receiver, may contain formatting like thousands separators
 */
@Readonly
@VisibleForTesting
fun Number.tr(language: String): String {
    return TranslationCache.getNumberFormatForLanguage(language).format(this)
}
