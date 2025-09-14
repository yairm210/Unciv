package com.unciv.logic

import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import yairm210.purity.annotations.Pure

object MultiFilter {
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
    @Readonly
    fun multiFilter(
        input: String,
        @Readonly filterFunction: (String) -> Boolean,
        forUniqueValidityTests: Boolean = false
    ): Boolean {
        if (isAnd(input))
            return getAndFilters(input)
                .all { multiFilter(it, filterFunction, forUniqueValidityTests) }
        if (isNot(input)) {
            //same as `return multiFilter() == forUniqueValidityTests`, but clearer
            val internalResult = multiFilter(getNotFilter(input), filterFunction, forUniqueValidityTests)
            return if (forUniqueValidityTests) internalResult else !internalResult
        }
        return filterFunction(input)
    }

    @Pure
    fun getAllSingleFilters(input: String): Sequence<String> = when {
        isAnd(input) -> {
            // Resolve "AND" filters
            @LocalState val filters = getAndFilters(input)
            filters.flatMap { getAllSingleFilters(it) }
        }
        isNot(input) ->
            // Simply remove "non" syntax
            getAllSingleFilters(getNotFilter(input))
        else -> sequenceOf(input)
    }
    
    @Pure fun isAnd(input: String) = input.startsWith(andPrefixChar) && input.endsWith(andSuffixChar) && input.contains(andSeparator)
    @Pure fun getAndFilters(input: String) = input.removeSurrounding(andPrefix, andSuffix).splitToSequence(andSeparator)
    @Pure fun isNot(input: String) = input.endsWith(notSuffixChar) && input.startsWith(notPrefix)
    @Pure fun getNotFilter(input: String) = input.removeSurrounding(notPrefix, notSuffix)
}
