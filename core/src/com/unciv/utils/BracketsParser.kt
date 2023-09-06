package com.unciv.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 *  Wanted:
 *      * [+] Parses nested brackets of multiple types () [] {} <>
 *      * [+] Which are allowed in parsing configurable
 *      * [+] Depth limit
 *      * Error reporting that can be shown / logged / RulesetValidator use
 *      * [+] O(n) perf
 *      * [-] No allocations during a parse, minimal on creation or parse start
 */
class BracketsParser(
    private val maxDepth: Int,
    allowedTypes: String = "[{<"
) {
    enum class BracketType(val opening: Char, val closing: Char) {
        Round('(', ')'),
        Square('[', ']'),
        Curly('{', '}'),
        Pointy('<', '>'),
        ;
        companion object {
            operator fun invoke(char: Char) = values().firstOrNull { it.opening == char }
        }
    }

    enum class ErrorType { Result, InvalidClosing, UnmatchedOpening }
    data class ResultEntry(val level: Int, val position: Int, val type: BracketType, val error: ErrorType, val value: String)

    private class StackEntry(val type: BracketType, val position: Int)
    private val stack = ArrayDeque<StackEntry>(maxDepth)

    private val typeMap: Map<Char, BracketType> =
        // This does not throw if someone passes a bad allowedTypes
        allowedTypes.asSequence()
            .mapNotNull { BracketType(it) }
            .flatMap { sequence {
                yield(it.opening to it)
                yield(it.closing to it)
            } }
            .associate { it }

    init {
        if (typeMap.isEmpty())
            throw kotlin.IllegalArgumentException("Invalid or empty `allowedTypes` parameter")
    }

    fun parse(
        input: String,
        emit: (level: Int, position: Int, type: BracketType, error: ErrorType, value: String) -> Unit
    ) {
        stack.clear()
        try {
            for ((index, char) in input.withIndex()) {
                val type = typeMap[char] ?: continue
                if (char == type.opening) {
                    // Valid opening bracket
                    stack.add(StackEntry(type, index))
                } else if (stack.lastOrNull()?.type == type) {
                    // Valid closing bracket
                    val top = stack.removeLast()
                    if (stack.size < maxDepth)
                        emit(stack.size, top.position, type, ErrorType.Result, input.substring(top.position + 1, index))
                } else {
                    // Invalid closing bracket
                    emit(stack.size, index, type, ErrorType.InvalidClosing, "")
                }
            }
            for (unmatched in stack) {
                emit(-1, unmatched.position, unmatched.type, ErrorType.UnmatchedOpening, "")
            }
        } catch (_: CancellationException) {}
    }

    fun parse(input: String): List<ResultEntry> = ArrayList<ResultEntry>().apply {
        parse(input) {
            level, position, type, error, value ->
            add(ResultEntry(level, position, type, error, value))
        }
    }

    companion object {
        private val parameterParser = BracketsParser(1, "[<")

        fun String.getPlaceholderParameters(): List<String> {
            if ('[' !in this) return emptyList()
            return ArrayList<String>().apply {
                parameterParser.parse(this@getPlaceholderParameters) {
                    _, _, type, error, value ->
                    if (type == BracketType.Pointy) throw CancellationException()
                    if (error == ErrorType.Result) add(value)
                }
            }
        }
    }
}
