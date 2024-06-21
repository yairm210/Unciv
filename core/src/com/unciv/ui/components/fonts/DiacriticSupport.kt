package com.unciv.ui.components.fonts

//todo Will FontRulesetIcons initialize first or will the Translations loader fill this up first???
object DiacriticSupport {
    private var nextFreeDiacriticReplacementCodepoint = 0xF8FF
    private val fakeAlphabet = mutableMapOf<Char, String>()
    private val inverseMap = mutableMapOf<String, Char>()

    fun reset() {
        fakeAlphabet.clear()
        inverseMap.clear()
        nextFreeDiacriticReplacementCodepoint = 0xF8FF
    }

    /** This is the main engine for rendering after the translation loader filled this up
     *  @param  char The real or "fake alphabet" char stored by [remapDiacritics] to render
     *  @return The one to three codepoint string to be rendered into a single glyphby native font services
     */
    fun getStringFor(char: Char) = fakeAlphabet[char] ?: char.toString()

    /** Call when use of [remapDiacritics] is finished to save some memory */
    fun freeInverseMap() = inverseMap.clear()

    /** Other "fake" alphabets can use Unicode Private Use Areas from U+E000 up to including... */
    fun getNextFreeCode() = Char((nextFreeDiacriticReplacementCodepoint).toUShort())

    /** If this is true, no need to bother translating chars at all */
    fun isEmpty() = fakeAlphabet.isEmpty()

    private fun getReplacementChar(joined: String) = inverseMap[joined] ?: createReplacementChar(joined)

    private fun createReplacementChar(joined: String): Char {
        val char = getNextFreeCode()
        nextFreeDiacriticReplacementCodepoint--
        fakeAlphabet[char] = joined
        inverseMap[joined] = char
        return char
    }

    /** Replaces the combos of diacritics/joiners with their affected characters with a "fake" alphabet */
    fun remapDiacritics(value: String, leftDiacritics: String, joinerDiacritics: String): String {
        val sb = StringBuilder(value.length)
        val accumulator = StringBuilder(3)

        fun flush() {
            sb.append(accumulator)
            accumulator.clear()
        }
        fun flushJoined(char: Char) {
            if (accumulator.isEmpty()) {
                sb.append(char) // pass out-of-place joiners through
            } else {
                accumulator.append(char)
                sb.append(getReplacementChar(accumulator.toString()))
                accumulator.clear()
            }
        }
        fun flushAndStore(char: Char) {
            if (accumulator.length == 2 && accumulator[1] in joinerDiacritics) {
                flushJoined(char)
            } else {
                flush()
                accumulator.append(char)
            }
        }

        for (char in value) {
            when (char) {
                in leftDiacritics -> flushJoined(char)
                in joinerDiacritics -> accumulator.append(char)
                else -> flushAndStore(char)
            }
        }

        flush()
        return sb.toString()
    }
}
