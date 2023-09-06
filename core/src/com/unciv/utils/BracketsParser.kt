package com.unciv.utils

import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import com.unciv.ui.components.extensions.toGdxArray
import kotlin.coroutines.cancellation.CancellationException

/**
 *  A tool to parse strings for matching, possibly nested brackets of various types
 *
 *  Requirements:
 *      * [+] Parses nested brackets of multiple types () [] {} <>
 *      * [+] Which of these are allowed in parsing is configurable
 *      * [+] Depth limit - deeper results are skipped and parsing is completed
 *      * [+] Error reporting
 *      * [+] O(n) perf
 *      * [+] No allocations during a parse, minimal on creation or parse start
 */
class BracketsParser(
    private val maxDepth: Int,
    allowedTypes: String = "[{<"
) : Disposable {
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

    private class StackEntry(var type: BracketType, var position: Int) {
        fun set(type: BracketType, position: Int): StackEntry {
            this.type = type
            this.position = position
            return this
        }
    }
    private val stack = ArrayDeque<StackEntry>(maxDepth)
    private val pool = object : Pool<StackEntry>() {
        override fun newObject() = StackEntry(BracketType.Round, -1)
        init {
            fill(maxDepth)
        }
    }

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
            throw IllegalArgumentException("Invalid or empty `allowedTypes` parameter")
    }

    override fun dispose() {
        pool.clear()
    }

    fun parse(
        input: String,
        emit: (level: Int, position: Int, type: BracketType, error: ErrorType, value: String) -> Unit
    ) {
        try {
            for ((index, char) in input.withIndex()) {
                val type = typeMap[char] ?: continue
                if (char == type.opening) {
                    // Valid opening bracket
                    stack.add(pool.obtain().set(type, index))
                } else if (stack.lastOrNull()?.type == type) {
                    // Valid closing bracket
                    val top = stack.removeLast()
                    if (stack.size < maxDepth)
                        emit(stack.size, top.position, type, ErrorType.Result, input.substring(top.position + 1, index))
                    pool.free(top)
                } else {
                    // Invalid closing bracket
                    emit(stack.size, index, type, ErrorType.InvalidClosing, "")
                }
            }
            for (unmatched in stack) {
                emit(-1, unmatched.position, unmatched.type, ErrorType.UnmatchedOpening, "")
            }
        } catch (_: CancellationException) {}
        if (stack.isEmpty()) return
        pool.freeAll(stack.toGdxArray())
        stack.clear()
    }

    fun parse(input: String): List<ResultEntry> = ArrayList<ResultEntry>().apply {
        parse(input) {
            level, position, type, error, value ->
            add(ResultEntry(level, position, type, error, value))
        }
    }

    companion object {
        @Suppress("GDXKotlinStaticResource")
        private val parameterParser = BracketsParser(1, "[<")

        fun String.getPlaceholderParameters(): List<String> {
            if ('[' !in this) return emptyList()
            val result = ArrayList<String>()
            parameterParser.parse(this@getPlaceholderParameters) {
                _, _, type, error, value ->
                if (type == BracketType.Pointy) throw CancellationException()
                if (error == ErrorType.Result) result.add(value)
            }
            return result
        }
    }
}
