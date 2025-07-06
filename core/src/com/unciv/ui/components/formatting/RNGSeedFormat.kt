@file:Suppress("ConstPropertyName")

package com.unciv.ui.components.formatting

import com.badlogic.gdx.utils.Base64Coder
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import java.nio.ByteBuffer
import org.jetbrains.annotations.VisibleForTesting


/**
 *  RNG seeds can be formatted for display in a "mnemonic" form or parsed in a few different ways.
 *
 *  ### Parse formats:
 *  - Leading and trailing blanks are ignored
 *  - For historical reasons, "" is parsed as 0L
 *  - As produced by plain Long.toString()
 *  - As produced by Number.tr() - probably containing thousands separators
 *  - As base-64 grouped into groups of 3 characters by dashes, e.g.: "eKa-1xN-Pi8-QA", padding with a "=" allowed but not required
 *  - Shattered Pixel's even more mnemonic "XYZ-PQR-CDF" format - base21 covering ~40bits
 *  - Parsing arbitrary phrases returns String.hashCode() reimplemented as Long
 *
 *  ### Formatting output:
 *  - Number.tr() for compatibility purposes: Once the code supporting parsing the other formats is established, this can change
 *  - TODO: Don't forget to update this comment when doing so
 *
 *  ### Notes
 *  - Generation of random seeds is unaffected, therefore this **must** cover the entire Long range
 *
 *  ### API
 *  @property parse Call site syntax: `RNGSeedFormat.parse(text)` - the parser supporting all formats named above
 *  @property format Call site syntax: `RNGSeedFormat.format(text)` - the formatter deciding which of the supported formats to produce
 */
object RNGSeedFormat {

    fun format(value: Long) = value.tr()
    //TODO Replace with: fun format(value: Long) = value.prettyFormat()
    // when the parser accepting all is well-established

    fun parse(text: String) = text.trim(' ').run {
        when {
            isEmpty() -> 0L
            isBase21decorated() -> parseBase21()
            isBase64decorated() -> parseBase64()
            isNumber() -> UncivGame.Current.settings.getCurrentNumberFormat().parse(this@run).toLong()
            else -> hashCode64()
        }
    }

    private const val thousandsSeparators = ",.\u00A0\u202F"
    private const val minusSymbols = "-\u2212"
    private const val base21Symbols = "BCDFGHJKLMNPQRSTVWXYZ"
    private const val maxNumberFormatLength = 26
    private const val minBase64FormatLength = 14 // Long.MIN_VALUE in Base64/decorated is "gAA-AAA-AAA-AA"
    private const val maxBase64FormatLength = 15 // allow one padding '='
    private const val base21FormatLength = 11

    @VisibleForTesting
    const val maxBase21Value = 794280046580L //21.0.pow(9.0).toLong() - 1L

    /////////////////// Checking

    private fun isValidNumberChar(char: Char, index: Int) =
        char.isDigit() || char in thousandsSeparators || char in minusSymbols && index == 0
    private fun isValidBase64Char(char: Char, index: Int) =
        if (index == minBase64FormatLength) char == '='
        else if (index % 4 == 3) char == '-'
        else char.isLetterOrDigit() || char == '+' || char == '/'
    private fun isValidBase21Char(char: Char, index: Int) =
        if (index % 4 == 3) char == '-'
        else char in base21Symbols

    private fun String.isNumber() = length in 1..maxNumberFormatLength &&
        withIndex().all { (index, char) -> isValidNumberChar(char, index) }
    private fun String.isBase21decorated() = length == base21FormatLength &&
        withIndex().all { (index, char) -> isValidBase21Char(char, index) }
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

    private fun Long.formatBase21() = buildString(base21FormatLength) {
        var residue = this@formatBase21
        for (i in 0 until base21FormatLength) {
            if (i % 4 == 3) {
                insert(0, '-')
                continue
            }
            val nextBits = (residue % 21L).toInt()
            residue /= 21
            insert(0, base21Symbols[nextBits])
        }
    }

    private fun Long.prettyFormat() =
        if (this in 0L..maxBase21Value) formatBase21() else formatBase64()


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

    private fun String.parseBase21(): Long {
        var result = 0L
        for (char in this@parseBase21) {
            if (char == '-') continue
            result = 21L * result + base21Symbols.indexOf(char)
        }
        return result
    }

    @VisibleForTesting
    fun String.hashCode64(): Long {
        var result = 0L
        for (char in this@hashCode64) {
            result = 31 * result + char.hashCode()
        }
        return result
    }

    @VisibleForTesting
    fun unitTestFormat(input: Long) = input.prettyFormat()
}
