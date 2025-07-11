package com.unciv.logic

import org.jetbrains.annotations.Contract

object MultiFilter {
    private const val andPrefix = "{"
    private const val andSeparator = "} {"
    private const val andSuffix = "}"
    private const val notPrefix = "non-["
    private const val notSuffix = "]"

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
    @Contract("readonly") @Suppress("purity")
    fun multiFilter(
        input: String,
        filterFunction: (String) -> Boolean,
        forUniqueValidityTests: Boolean = false
    ): Boolean {
        if (input.hasSurrounding(andPrefix, andSuffix) && input.contains(andSeparator))
            return input.removeSurrounding(andPrefix, andSuffix).split(andSeparator)
                .all { multiFilter(it, filterFunction, forUniqueValidityTests) }
        if (input.hasSurrounding(notPrefix, notSuffix)) {
            //same as `return multiFilter() == forUniqueValidityTests`, but clearer
            val internalResult = multiFilter(input.removeSurrounding(notPrefix, notSuffix), filterFunction, forUniqueValidityTests)
            return if (forUniqueValidityTests) internalResult else !internalResult
        }
        return filterFunction(input)
    }

    @Contract("readonly")
    fun getAllSingleFilters(input: String): Sequence<String> = when {
        input.hasSurrounding(andPrefix, andSuffix) && input.contains(andSeparator) ->
            // Resolve "AND" filters
            input.removeSurrounding(andPrefix, andSuffix)
                .splitToSequence(andSeparator)
                .flatMap { getAllSingleFilters(it) }
        input.hasSurrounding(notPrefix, notSuffix) ->
            // Simply remove "non" syntax
            getAllSingleFilters(input.removeSurrounding(notPrefix, notSuffix))
        else -> sequenceOf(input)
    }

    @Contract("readonly")
    fun String.hasSurrounding(prefix: String, suffix: String) =
        startsWith(prefix) && endsWith(suffix)
}
