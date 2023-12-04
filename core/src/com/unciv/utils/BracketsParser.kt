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
 *      * [+] Able to return placeholderText, that is, one level of inner brackets reduced to empty brackets, for every level
 */
class BracketsParser(
    private val maxDepth: Int,
    allowedTypes: String = "[{<",
    private val supportPlaceholderText: Boolean = false
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

    interface StackEntry {
        var type: BracketType
        var position: Int
        fun set(type: BracketType, position: Int): StackEntry
        fun addExclusion(from: Int, to: Int) {}
        fun getPlaceHolderTextFactory(input: String, limit: Int): () -> String = { "" }
    }
    private class StackEntryLite(override var type: BracketType, override var position: Int) : StackEntry {
        override fun set(type: BracketType, position: Int): StackEntry {
            this.type = type
            this.position = position
            return this
        }
    }
    private class StackEntryFull(override var type: BracketType, override var position: Int) : StackEntry {
        private val exclusionsFrom = ArrayList<Int>(3)
        private val exclusionsTo = ArrayList<Int>(3)
        override fun set(type: BracketType, position: Int): StackEntry {
            this.type = type
            this.position = position
            exclusionsFrom.clear()
            exclusionsTo.clear()
            return this
        }
        override fun addExclusion(from: Int, to: Int) {
            exclusionsFrom.add(from)
            exclusionsTo.add(to)
        }
        override fun getPlaceHolderTextFactory(input: String, limit: Int): ()->String {
            // This is comparable to a lambda as it carries information as closures, but a bit more readable IMHO
            fun getPlaceholderText(): String {
                val sb = StringBuilder(limit - position - 1)
                var pos = position + 1
                val toIter = exclusionsTo.iterator()
                for (from in exclusionsFrom) {
                    if (pos < from)
                        sb.append(input.substring(pos, from))
                    pos = toIter.next()
                }
                if (pos < limit)
                    sb.append(input.substring(pos, limit))
                return sb.toString()
            }
            return ::getPlaceholderText
        }
    }

    private val stack = ArrayDeque<StackEntry>(maxDepth)
    private val pool = object : Pool<StackEntry>() {
        override fun newObject() = if (supportPlaceholderText) StackEntryFull(BracketType.Round, -1)
                else StackEntryLite(BracketType.Round, -1)
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
        emit: (level: Int, position: Int, type: BracketType, error: ErrorType, value: String, getPlaceholderText: ()->String) -> Unit
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
                    if (stack.size < maxDepth) {
                        emit(stack.size, top.position, type, ErrorType.Result, input.substring(top.position + 1, index), top.getPlaceHolderTextFactory(input, index))
                    }
                    stack.lastOrNull()?.addExclusion(top.position + 1, index)
                    pool.free(top)
                } else {
                    // Invalid closing bracket
                    emit(stack.size, index, type, ErrorType.InvalidClosing, "", emptyStringLambda)
                }
            }
            for (unmatched in stack) {
                emit(-1, unmatched.position, unmatched.type, ErrorType.UnmatchedOpening, "", emptyStringLambda)
            }
        } catch (_: CancellationException) {}
        if (stack.isEmpty()) return
        pool.freeAll(stack.toGdxArray())
        stack.clear()
    }

    fun parse(input: String): List<ResultEntry> = ArrayList<ResultEntry>().apply {
        parse(input) {
            level, position, type, error, value, _ ->
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
                _, _, type, error, value, _ ->
                if (type == BracketType.Pointy) throw CancellationException()
                if (error == ErrorType.Result) result.add(value)
            }
            return result
        }
    }
}
