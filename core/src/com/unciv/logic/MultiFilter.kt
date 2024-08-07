package com.unciv.logic

object MultiFilter {
    // Chars are for startsWith and endsWith performance
    private const val andPrefixChar = '{'
    private const val andPrefix = andPrefixChar.toString()
    private const val andSeparator = "} {"
    private const val andSuffixChar = '}'
    private const val andSuffix = andSuffixChar.toString()
    private const val notPrefix = "non-["
    private const val notSuffixChar = ']'
    private const val notSuffix = notSuffixChar.toString()

    /**
     *  Implements `and` and `not` logic on top of a [filterFunction].
     *
     *  Syntax:
     *      - `and`: `{filter1} {filter2}`... (can repeat as needed)
     *      - `not`: `non-[filter]`
     *  @param input The complex filtering term
     *  @param filterFunction The single filter implementation
     *  @param forUniqueValidityTests Inverts the `non-[filter]` test because Unique validity doesn't check for actual matching
     */
    fun multiFilter(
        input: String,
        filterFunction: (String) -> Boolean,
        forUniqueValidityTests: Boolean = false
    ): Boolean {
        if (isAndFilter(input))
            return input.removeSurrounding(andPrefix, andSuffix).splitToSequence(andSeparator)
                .all { multiFilter(it, filterFunction, forUniqueValidityTests) }
        if (isNotFilter(input)) {
            //same as `return multiFilter() == forUniqueValidityTests`, but clearer
            val internalResult = multiFilter(input.removeSurrounding(notPrefix, notSuffix), filterFunction, forUniqueValidityTests)
            return if (forUniqueValidityTests) internalResult else !internalResult
        }
        return filterFunction(input)
    }

    private fun isAndFilter(input: String) = input.startsWith(andPrefixChar)
            && input.endsWith(andSuffixChar)
            && input.contains(andSeparator)

    private fun isNotFilter(input: String) = input.endsWith(notSuffixChar) && input.startsWith(notSuffix)

    fun getAllSingleFilters(input: String): Sequence<String> = when {
        isAndFilter(input) ->
            input.removeSurrounding(andPrefix, andSuffix)
                .splitToSequence(andSeparator)
                .flatMap { getAllSingleFilters(it) }
        isNotFilter(input) ->
            // Simply remove "non" syntax
            getAllSingleFilters(input.removeSurrounding(notPrefix, notSuffix))
        else -> sequenceOf(input)
    }
}
