package com.unciv.models.ruleset.validation

import yairm210.purity.annotations.Pure
import kotlin.math.min

/**
 * Algorithm:
 *  - Keep an index for each string.
 *  - Iteratively advance by one character in each string.
 *      - If the character at the index of each string is not the same, then pause.
 *          - Try to find the minumum number of characters to skip in the first string to find the current character of the second string.
 *          - Try to find the minimum number of characters to skip in the second string to find the current character of the first string.
 *          - If the above condition cannot be satisifed for either string, then skip both by one character and continue advancing them together.
 *          - Otherwise, skip ahead in either the first string or the second string, depending on which requires the lowest offset, and continue advancing both strings together.
 *      - Stop when either one of the above steps cannot be completed or the end of either string has been reached.
 *  - The distance returned is the apprximately total number of characters skipped, plus the total number of characters unaccounted for at the end.
 *
 * Meant to run in linear-ish time.
 * Order of comparands shouldn't matter too much, but does a little.
 * This seemed simpler than a thorough implementation of other string comparison algorithms, and maybe more performant than a naïve implementation of other string comparisons, as well as sufficient for the fairly simple use case.
 *
 * @param text1 String to compare.
 * @param text2 String to compare.
 * @return Approximate distance between them.
 */
@Pure
fun getTextDistance(text1: String, text2: String): Int {
    var dist = 0
    var i1 = 0
    var i2 = 0

//    fun String.debugTraversal(index: Int) = println(this.substring(0..index-1)+"["+this[index]+"]"+this.substring(index+1..this.lastIndex))
//    /** Uncomment this and stick it at the start of the `while` if you want to see what's happening. */
//    fun debugTraversal() { println(); text1.debugTraversal(i1); text2.debugTraversal(i2); }

    fun inRange() = i1 < text1.length && i2 < text2.length // Length is O(1), apparently.
    while (inRange()) {
//        debugTraversal()
        val char1 = text1[i1] // Indexing may not be, though.
        val char2 = text2[i2]
        if (char1 == char2) {
            i1++
            i2++
        } else if (char1.lowercaseChar() == char2.lowercaseChar()) {
            dist++
            i1++
            i2++
        } else {
            val firstMatchIndex1 = (i1..text1.lastIndex).firstOrNull { text1[it] == char2 }
            val firstMatchIndex2 = (i2..text2.lastIndex).firstOrNull { text2[it] == char1 }
            if (firstMatchIndex1 == null && firstMatchIndex2 == null) {
                dist++
                i1++
                i2++
                continue
            }
            val firstMatchOffset1 = firstMatchIndex1?.minus(i1)
            val firstMatchOffset2 = firstMatchIndex2?.minus(i2)
            when {
                (firstMatchOffset2 == null || (firstMatchOffset1 != null && firstMatchOffset1 < firstMatchOffset2)) -> { // Preferential behaviour when the offsets are equal does make the operation slightly non-commutative, I think.
                    dist += firstMatchOffset1!!
                    i1 = firstMatchIndex1 + 1
                    i2++
                }
                (firstMatchOffset1 == null || firstMatchOffset1 >= firstMatchOffset2) -> {
                    dist += firstMatchOffset2
                    i1++
                    i2 = firstMatchIndex2 + 1
                }
                else -> error("Can't compare Strings:\n\t${text1}\n\t${text2}")
            }
        }
    }
    dist += ((text1.length - i1) + (text2.length - i2)) / 2
    return dist
}

/** @return the [getTextDistance] of two strings relative to their average length.
 * The original algorithm is very weak to short strings with errors at the start (can't figure out that "on [] tiles" and "in [] tiles" are the same)
 * So we run it twice, once with the string reversed */
@Pure
fun getRelativeTextDistance(text1: String, text2: String): Double {
    fun textDistance(a: String, b: String): Double = getTextDistance(a, b).toDouble() / (text1.length + text2.length) * 2.0
    return min(textDistance(text1, text2), textDistance(text1.reversed(), text2.reversed()))
}
