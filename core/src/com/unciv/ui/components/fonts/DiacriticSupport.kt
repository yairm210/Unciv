package com.unciv.ui.components.fonts

import com.unciv.utils.Log
import org.jetbrains.annotations.VisibleForTesting

/**
 *  ## An engine to support languages with heavy diacritic usage through Gdx Scene2D
 *
 *  ### Concepts
 *  - This is not needed for diacritics where Unicode already defines the combined glyphs as individual codepoints
 *  - Gdx text rendering assumes one Char one Glyph (and left-to-right)
 *  - The underlying OS **does** have the capability to render glyphs created by combining diacritic joiners with other characters (if not, this fails with ugly output but hopefully no exceptions).
 *  - We'll deal with one glyph at a time arranges left to right, and expect a finite number of combination glyphs (all fit into the Unicode Private Use Area **together** with [FontRulesetIcons]).
 *  - We'll recognize these combos in the translated texts at translation loading time and map each combo into a fake alphabet, which fulfills the "one Char one Glyph" tenet.
 *  - Conversely, the loader will build a map of distinct combinations -codepoint sequences- that map into a single glyph and correlate each with their fake alphabet codepoint.
 *  - At render time, only the map of fake alphabet codepoints to their original codepoint sequences is needed.
 *        * Remember, NativeBitmapFontData renders and caches glyphs on demand
 *        * GlyphLayout (a Gdx class) needs a Glyph that's not yet cached, then:
 *        * If it's in the fake alphabet, we'll ask the OS to render the original codepoint sequence instead
 *        * Otherwise render the single Char as before
 *
 *  ### Usage
 *  - Call [reset] when translation loading starts over
 *  - Instantiate [DiacriticSupport] through the constructor-like factory [invoke] once a translation file is read (their map of key (left of =) to translation (right of =) is in memory, pass that as argument)
 *  - Check [isEnabled] - if false, the rest of that language load need not bother with diacritics
 *  - Call [remapDiacritics] on each translation and store the result instead of the original value
 *  - If you wish to save some memory, call [freeTranslationData] after all required languages are done
 *  - Later, [NativeBitmapFontData.createAndCacheGlyph] will use [getStringFor] to map the fake alphabet back to codepoint sequences
 *
 *  ### Notes
 *  - [FontRulesetIcons] initialize ***after*** Translation loading. If this ever changes, this might need some tweaking.
 *  - The primary constructor is only used from the [Companion.invoke] factory and for testing.
 */
class DiacriticSupport(
    private val enabled: Boolean = false,
    range: CharRange,
    leftDiacritics: String,
    rightDiacritics: String,
    joinerDiacritics: String
) {
    private object TranslationKeys {
        const val enable = "diacritics_support"
        const val rangeStart = "unicode_block_start_character"
        const val rangeEnd = "unicode_block_end_character"
        const val left = "left_joining_diacritics"
        const val right = "right_joining_diacritics"
        const val joiner = "left_and_right_joiners"
    }

    companion object {
        /** Start at end of Unicode Private Use Area and go down from there: UShort is the preferred Char() constructor parameter! */
        private const val startingReplacementCodepoint: UShort = 63743u // 0xF8FF
        /** Use stdlib CharCategory to determine which codepoints represent combining diacritical marks.
         *  We're defaulting all punctuation, currency & other symbols, and nonprinting to None,
         *  meaning they won't combine even when followed by a diacritic. */
        private val charCategoryToClass = mapOf(
            CharCategory.UPPERCASE_LETTER to CharClass.Base,
            CharCategory.LOWERCASE_LETTER to CharClass.Base,
            CharCategory.TITLECASE_LETTER to CharClass.Base,
            CharCategory.OTHER_LETTER to CharClass.Base,
            CharCategory.MODIFIER_LETTER to CharClass.Base,
            CharCategory.DECIMAL_DIGIT_NUMBER to CharClass.Base,
            CharCategory.LETTER_NUMBER to CharClass.Base,
            CharCategory.OTHER_NUMBER to CharClass.Base,
            CharCategory.COMBINING_SPACING_MARK to CharClass.LeftJoiner,
            CharCategory.NON_SPACING_MARK to CharClass.LeftJoiner,
            CharCategory.ENCLOSING_MARK to CharClass.LeftJoiner,
            CharCategory.SURROGATE to CharClass.Surrogate
        )
        private const val defaultRangeStart = '\u0021'
        private const val defaultRangeEnd = '\uFFEE'

        private var nextFreeDiacriticReplacementCodepoint = startingReplacementCodepoint
        private val fakeAlphabet = mutableMapOf<Char, String>()
        private val inverseMap = mutableMapOf<String, Char>()

        /** Prepares this for a complete start-over, expecting a language load to instantiate a DiacriticSupport next */
        fun reset() {
            fakeAlphabet.clear()
            freeTranslationData()
            nextFreeDiacriticReplacementCodepoint = startingReplacementCodepoint
        }

        /** This is the main engine for rendering text glyphs after the translation loader has filled up this `object`
         *  @param  char The real or "fake alphabet" char stored by [remapDiacritics] to render
         *  @return The one to many (probably 8 max) codepoint string to be rendered into a single glyph by native font services
         */
        fun getStringFor(char: Char) = fakeAlphabet[char] ?: char.toString()

        /** Call when use of [remapDiacritics] is finished to save some memory */
        fun freeTranslationData() {
            for ((length, examples) in inverseMap.keys.groupBy { it.length }.toSortedMap()) {
                Log.debug("Length %d - example %s", length, examples.first())
            }
            inverseMap.clear()
        }

        /** Other "fake" alphabets can use Unicode Private Use Areas from U+E000 up to including... */
        fun getCurrentFreeCode() = Char(nextFreeDiacriticReplacementCodepoint)

        /** If this is true, no need to bother [remapping chars at render time][getStringFor] */
        fun isEmpty() = fakeAlphabet.isEmpty()

        /** Factory that gets the primary constructor parameters by extracting the translation entries for [TranslationKeys] */
        operator fun invoke(translations: HashMap<String, String>): DiacriticSupport {
            val stripCommentRegex = """^"?(.*?)"?(?:\s*#.*)?$""".toRegex()
            fun String?.parseDiacriticEntry(): String {
                if (isNullOrEmpty()) return ""
                val tokens = stripCommentRegex.matchEntire(this)!!.groupValues[1]
                    .splitToSequence(' ').filter { it.isNotEmpty() }.toMutableList()
                for (index in tokens.indices) {
                    val token = tokens[index]
                    when {
                        token.length == 1 -> continue
                        token.startsWith("u+", true) -> tokens[index] = Char(token.drop(2).toInt(16)).toString()
                        tokens.size == 1 -> continue
                        else -> throw IllegalArgumentException("Invalid diacritic definition: \"$token\" is not a single character or unicode codepoint notation")
                    }
                }
                return tokens.joinToString("")
            }

            val enable = translations[TranslationKeys.enable].parseDiacriticEntry() == "true"
            val rangeStart = translations[TranslationKeys.rangeStart].parseDiacriticEntry()
            val rangeEnd = translations[TranslationKeys.rangeEnd].parseDiacriticEntry()
            val range = if (rangeStart.isEmpty() || rangeEnd.isEmpty()) CharRange.EMPTY
                else rangeStart.min()..rangeEnd.max()
            val leftDiacritics = translations[TranslationKeys.left].parseDiacriticEntry()
            val rightDiacritics = translations[TranslationKeys.right].parseDiacriticEntry()
            val joinerDiacritics = translations[TranslationKeys.joiner].parseDiacriticEntry()

            return DiacriticSupport(enable, range, leftDiacritics, rightDiacritics, joinerDiacritics)
        }
    }

    private val charClassMap = mutableMapOf<Char, CharClass>()

    /** Holds all information to process a single translation line and replace diacritic combinations with fake alphabet codepoints */
    private inner class LineData(capacity: Int) {
        val output = StringBuilder(capacity)
        val accumulator = StringBuilder(9) // touhidurrr said there can be nine
        var waitingHighSurrogate = Char.MIN_VALUE

        fun expectsJoin() = accumulator.isNotEmpty() && getCharClass(accumulator.last()).expectsRightJoin
        fun flush() {
            if (accumulator.length <= 1) output.append(accumulator)
            else output.append(getReplacementChar(accumulator.toString()))
            accumulator.clear()
        }
        fun forbidWaitingHighSurrogate() {
            if (waitingHighSurrogate != Char.MIN_VALUE)
                throw IllegalArgumentException("Invalid Unicode: High surrogate without low surrogate")
        }
        fun accumulate(char: Char) {
            forbidWaitingHighSurrogate()
            accumulator.append(char)
        }
        fun flushAccumulate(char: Char) {
            forbidWaitingHighSurrogate()
            if (!expectsJoin()) flush()
            accumulator.append(char)
        }
        fun flushAppend(char: Char) {
            forbidWaitingHighSurrogate()
            flush()
            output.append(char)
        }
        fun surrogate(char: Char) {
            if (char.isHighSurrogate()) {
                forbidWaitingHighSurrogate()
                waitingHighSurrogate = char
            } else {
                if (waitingHighSurrogate == Char.MIN_VALUE) throw IllegalArgumentException("Invalid Unicode: Low surrogate without high surrogate")
                if (!expectsJoin()) flush()
                accumulator.append(waitingHighSurrogate)
                accumulator.append(char)
                waitingHighSurrogate = Char.MIN_VALUE
            }
        }
        fun result(): String {
            flush()
            return output.toString()
        }
    }

    /** Represents a class of input character and its processing method when processing a translation line */
    private enum class CharClass(val expectsRightJoin: Boolean = false) {
        None {
            override fun process(data: LineData, char: Char) = data.flushAppend(char)
        },
        Base {
            override fun process(data: LineData, char: Char) = data.flushAccumulate(char)
        },
        LeftJoiner {
            override fun process(data: LineData, char: Char) = data.accumulate(char)
        },
        RightJoiner(true) {
            override fun process(data: LineData, char: Char) = data.flushAccumulate(char)
        },
        LeftRightJoiner(true) {
            override fun process(data: LineData, char: Char) = data.accumulate(char)
        },
        Surrogate {
            override fun process(data: LineData, char: Char) = data.surrogate(char)
        };
        abstract fun process(data: LineData, char: Char)
    }

    @VisibleForTesting
    fun getKnownCombinations(): Set<String> = inverseMap.keys

    /** Set at instatiation, if true the translation loader need not bother passing stuff through [remapDiacritics]. */
    fun isEnabled() = enabled

    private fun getCharClass(char: Char) = charClassMap[char] ?: CharClass.None

    private fun getReplacementChar(joined: String) = inverseMap[joined] ?: createReplacementChar(joined)

    private fun createReplacementChar(joined: String): Char {
        val char = getCurrentFreeCode()
        nextFreeDiacriticReplacementCodepoint--
        if (nextFreeDiacriticReplacementCodepoint < FontRulesetIcons.UNUSED_CHARACTER_CODES_START.toUInt())
            throw IllegalStateException("DiacriticsSupport has exhausted the Unicode private use area")
        fakeAlphabet[char] = joined
        inverseMap[joined] = char
        return char
    }

    init {
        if (enabled) {
            val rangeStart = if (range.isEmpty()) defaultRangeStart else range.first
            val rangeEnd = if (range.isEmpty()) defaultRangeEnd else range.last
            for (char in rangeStart..rangeEnd)
                charClassMap[char] = charCategoryToClass[char.category] ?: continue
            for (char in leftDiacritics) charClassMap[char] = CharClass.LeftJoiner
            for (char in rightDiacritics) charClassMap[char] = CharClass.RightJoiner
            for (char in joinerDiacritics) charClassMap[char] = CharClass.LeftRightJoiner
        }
    }

    /** Replaces the combos of diacritics/joiners with their affected characters with a "fake" alphabet */
    fun remapDiacritics(value: String): String {
        if (!enabled)
            throw IllegalStateException("DiacriticSupport not set up properly for translation processing")

        val data = LineData(value.length)
        for (char in value) {
            getCharClass(char).process(data, char)
        }
        return data.result()
    }
}
