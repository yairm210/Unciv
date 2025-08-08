@file:Suppress("ConstPropertyName")

package com.unciv.ui.components.formatting

import com.badlogic.gdx.utils.Base64Coder
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import java.nio.ByteBuffer
import kotlin.math.pow


/**
 *  RNG seeds can be formatted for display in a "mnemonic" form or parsed in a few different ways.
 *
 *  ### Parse formats:
 *  - Leading and trailing blanks are ignored
 *  - For historical reasons, "" is parsed as 0L
 *  - As produced by plain Long.toString()
 *  - As produced by Number.tr() - probably containing thousands separators: when the [parseNumber] override supports it
 *  - As base-64 grouped into groups of 3 characters by dashes, e.g.: "eKa-1xN-Pi8-QA", padding with a "=" allowed but not required
 *  - Shattered Pixel's even more mnemonic "XYZ-PQR-CDF" format - base24 covering almos 42bits
 *  - Parsing arbitrary phrases returns String.hashCode() reimplemented as Long
 *
 *  ### Notes
 *  - Generation of random seeds is unaffected, therefore this must cover the entire Long range
 *
 *  ### API
 *  @property format Call site syntax: `RNGSeedFormat.format(text)` - the formatter deciding which of the supported formats to produce
 *  @property parse Call site syntax: `RNGSeedFormat.parse(text)` - the parser supporting all formats named above
 *  @property parseNumber Hook for o subclass to provide trivial or localized number parsing
 */
abstract class IRNGSeedFormat {
    /////////////////// API

    /** Seed to String: Abstract because the subclass decides which format and in the numric case provides the localized formatter */
    abstract fun format(value: Long): String

    /** String to Seed: final because all formats will be parsed */
    fun parse(text: String) = text.trim(' ').run {
        when {
            isEmpty() -> 0L
            isBase2Xdecorated() -> parseBase2X()
            isBase64decorated() -> parseBase64()
            isNumber() -> parseNumber(this@run)
            else -> hashCode64()
        }
    }

    /** Subclass-provided, used when the input was recognized as a number */
    protected abstract fun parseNumber(text: String): Long

    /////////////////// Constants

    companion object {
        private const val thousandsSeparators = ",.\u00A0\u202F"
        private const val minusSymbols = "-\u2212"
        /** To change base, changing the number of these symbols is enough, the math adapts automatically.
         *  With 24 symbols, we get the shorter format up to May 18, 2085 */
        private const val base2XSymbols = "BCDFGHIJKLMNOPQRSTUVWXYZ"
        private const val maxNumberFormatLength = 26
        private const val minBase64FormatLength = 14 // Long.MIN_VALUE in Base64/decorated is "gAA-AAA-AAA-AA"
        private const val maxBase64FormatLength = 15 // allow one padding '='
        private const val base2XFormatLength = 11
        private const val base2Xbase = base2XSymbols.length

        @JvmStatic
        protected val maxBase2XValue = base2Xbase.toDouble().pow(9.0).toLong() - 1L
    }

    /////////////////// Checking

    private fun isValidNumberChar(char: Char, index: Int) =
        char.isDigit() || char in thousandsSeparators || char in minusSymbols && index == 0
    private fun isValidBase64Char(char: Char, index: Int) =
        if (index == minBase64FormatLength) char == '='
        else if (index % 4 == 3) char == '-'
        else char.isLetterOrDigit() || char == '+' || char == '/'
    private fun isValidBase2XChar(char: Char, index: Int) =
        if (index % 4 == 3) char == '-'
        else char in base2XSymbols

    private fun String.isNumber() = length in 1..maxNumberFormatLength &&
        withIndex().all { (index, char) -> isValidNumberChar(char, index) }
    private fun String.isBase2Xdecorated() = length == base2XFormatLength &&
        withIndex().all { (index, char) -> isValidBase2XChar(char, index) }
    private fun String.isBase64decorated() = length in minBase64FormatLength..maxBase64FormatLength &&
        withIndex().all { (index, char) -> isValidBase64Char(char, index) }

    /////////////////// Formatting

    private fun Long.toBase64String(): String {
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES)
        buffer.putLong(this)
        return String(Base64Coder.encode(buffer.array()))
    }

    private fun String.decorate() =
        trimEnd('=').withIndex()
            .groupBy({ it.index / 3 }) { it.value } // Gives Map<Int, List<Char>>
            .map { it.value.joinToString("") } // Join chars per group
            .joinToString("-")

    private fun Long.formatBase64() = toBase64String().decorate()

    private fun Long.formatBase2X() = buildString(base2XFormatLength) {
        var residue = this@formatBase2X
        for (i in 0 until base2XFormatLength) {
            if (i % 4 == 3) {
                insert(0, '-')
                continue
            }
            val nextBits = (residue % base2Xbase).toInt()
            residue /= base2Xbase
            insert(0, base2XSymbols[nextBits])
        }
    }

    protected fun Long.prettyFormat() =
        if (this in 0L..maxBase2XValue) formatBase2X() else formatBase64()

    /////////////////// Parsing

    private fun String.undecorateBase64() =
        filterNot { it == '-' } // Uses `String.filterNot(..): String` - not a fallback to `Iterable<Char>`
            .run { padEnd(((length + 3) / 4) * 4, '=') } // ensure length is a multiple of 4

    private fun String.base64toLong(): Long {
        val bytes = Base64Coder.decode(this)
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES)
        buffer.put(bytes)
        buffer.rewind()
        return buffer.getLong()
    }

    private fun String.parseBase64() = undecorateBase64().base64toLong()

    private fun String.parseBase2X(): Long {
        var result = 0L
        for (char in this@parseBase2X) {
            if (char == '-') continue
            result = base2Xbase * result + base2XSymbols.indexOf(char)
        }
        return result
    }

    protected fun String.hashCode64(): Long {
        var result = 0L
        for (char in this@hashCode64) {
            result = 31 * result + char.hashCode()
        }
        return result
    }
}

/**
 *  Static implementation supporting localized numbers
 *  [format] delivers Number.tr() for compatibility purposes: Once the code supporting parsing the other formats is established, this can change
 *           //TODO Replace with: fun format(value: Long) = value.prettyFormat()
 *  [parse] understands localized numbers
 */
object RNGSeedFormat : IRNGSeedFormat() {
    override fun format(value: Long) = value.tr()
    override fun parseNumber(text: String) =
        UncivGame.Current.settings.getCurrentNumberFormat().parse(text).toLong()
}
