package com.unciv.utils

import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import com.unciv.ui.components.extensions.toGdxArray
import kotlin.coroutines.cancellation.CancellationException

/**
 *  Signature for your function that receives match results
 *  - **level**: Nesting level of match, starting at 0
 *  - **position**: 0-based position of the opening bracket in the input string
 *  - **type**: Which bracket type was found
 *  - **error**: `BracketsParser.ErrorType.Result` for a valid match, or `InvalidClosing` / `UnmatchedOpening`
 *        to report invalid input (value and getPlaceholderText return empty strings in that case)
 *  - **value**: entire content of the bracket pair, without the brackets themselves
 *  - **getPlaceholderText**: a factory to build a placeholderText for this match, returns empty string if the parser was configured without support
 */
typealias MatchReceiver = (
        level: Int,
        position: Int,
        type: BracketsParser.BracketType,
        error: BracketsParser.ErrorType,
        value: String,
        getPlaceholderText: ()->String
    ) -> Unit

/**
 *  A tool to parse strings for matching (possibly nested) brackets of various types
 *
 *  Methods:
 *  - `results = `[parse]`(input)` - Simple use, returns a List<[ResultEntry]>.
 *  - [parse]`(input) { match-receiver }` - Advanced use, "streaming" api, can support placeholderText, supports early exit.
 *  - String.[getPlaceholderParameters] A String extension to fetch `Unique` parameters while removing conditionals
 *
 *  Notes:
 *  - The ability to return placeholderText means: one level of inner brackets is reduced to corresponding empty brackets.
 *  - placeholderText is delivered as factory, The expensive string building is performed only on-demand, and leaving the feature off saves all memory allocation related to it.
 *  - One instance is reusable, but not in a thread-safe manner. Support parameters cannot be changed after instantiation.
 *  - Implements Gdx [Disposable] as it uses Gdx [Pool]s
 *  - Error reporting is done entirely through the output: [ResultEntry.error], or the corresponding parameter of the match receiver.
 *  - O(n) performance
 *  - No allocations during a parse, minimal on creation or parse start (exception: placeholderText enabled and consecutive inner brackets exceed 3)
 *
 * @param maxDepth maximum depth of nesting. Input exceeding this depth will still be parsed correctly, but inner matches beyond maxDepth will not be included in the output, and there will be additional memory allocation.
 * @param allowedTypes a set of Chars as String, each enabling their bracket type (pass th opening one)
 * @param supportPlaceholderText Default `false` saves GC load, but the emit calls won't support placeholderText; pass `true` to enable collecting state for, and generating factories to, get placeholderText.
 * @throws IllegalArgumentException only for invalid `allowedTypes` parameter input, all other errors are reported within the output
 */
class BracketsParser(
    private val maxDepth: Int,
    allowedTypes: CharSequence = "[{<",
    private val supportPlaceholderText: Boolean = false
) : Disposable {

    /** Supported bracket pairs, feel free to extend as needed.
     *
     *  Note: `Quotes('"', '"') would work, but offer no provision for escaped quotes within a parsed string.
     */
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

    /** Match error type - [Result] means a successful match
     *
     *  Note: Implies a certain interpretation decision: `parse("(abc [def) ghi]")`
     *  will report the square brackets as match and consequetly both round brackets as errors.
     */
    enum class ErrorType { Result, InvalidClosing, UnmatchedOpening }

    /** Holder for one bracket match - fields correspond to the [MatchReceiver] parameters. */
    data class ResultEntry(val level: Int, val position: Int, val type: BracketType, val error: ErrorType, val value: String)

    //region Stack

    /** Needs to be mutable to be reusable in a Pool - [set] is the readability helper after pool.obtain() */
    private interface StackEntry {
        var type: BracketType
        var position: Int
        fun set(type: BracketType, position: Int): StackEntry {
            this.type = type
            this.position = position
            return this
        }
        fun addExclusion(from: Int, to: Int) {}
        fun getPlaceHolderTextFactory(input: String, limit: Int): () -> String = { "" }
    }

    /** For basic parsing without placeholderText support */
    private class StackEntryLite(override var type: BracketType, override var position: Int) : StackEntry

    /** For extended parsing with placeholderText support */
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

    //endregion

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

    /**
     *  Advanced usage, this parses [input] and passes all bracket pairs found to [emit], see [MatchReceiver].
     *
     *  Unlike the `result = parse(input)` overload, this supports placeholderText and early exit by throwing [CancellationException] from within [emit].
     */
    fun parse(input: String, emit: MatchReceiver) {
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

    /** Simple usage: Parse [input] and return bracket matches.
     *  @return A List of matches with e.g. nesting level and position within the input, in order of the position of their _closing_ bracket.
     */
    fun parse(input: String): List<ResultEntry> = ArrayList<ResultEntry>().apply {
        parse(input) {
            level, position, type, error, value, _ ->
            add(ResultEntry(level, position, type, error, value))
        }
    }

    companion object {
        @Suppress("GDXKotlinStaticResource")
        private val parameterParser = BracketsParser(1, "[<")
        private val emptyStringLambda: ()->String  = { "" }

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
