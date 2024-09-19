package com.unciv.ui.screens.devconsole

import com.badlogic.gdx.graphics.Color
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.ui.screens.devconsole.CliInput.Companion.equals

/**
 *  Represents the method used to convert/display ruleset object (or other) names in console input.
 *  - Goal is to make them comparable, and to make parameter-delimiting spaces unambiguous, both in a user-friendly way.
 *  - Method 1: Everything is lowercase and spaces replaced with '-': `mechanized-infantry`.
 *  - Method 2: See [toQuotedRepresentation]: `"Mechanized Infantry"`, `"Ship of the Line"` (case from json except the first word which gets titlecased).
 *  - Note: Method 2 supports "open" quoting, that is, the closing quote is missing from parsed input, for autocomplete purposes. See [splitToCliInput]
 *  - Supports method-agnostic Comparison with other instances or with Strings, but efficient comparison in loops requires predetermining a consistent method throughout the loop.
 *  - Note: Method 2 requires case-insensitive comparison, while Method 1 does not, and a comparison must convert both sides using the same method. [compareTo] ensures that.
 */
internal class CliInput(
    parameter: String,
    method: Method? = null
) : Comparable<CliInput> {
    enum class Method {
        Dashed,
        Quoted;
        infix fun or(other: Method) = if (this == Dashed && other == Dashed) Dashed else Quoted
        infix fun and(other: Method) = if (this == Quoted && other == Quoted) Quoted else Dashed
    }

    //////////////////////////////////////////////////////////////// region Fields and initialization

    /** 'type'
     *  - [Dashed][Method.Dashed] means [content] is stored and presented lowercased and blanks converted to dashes
     *  - [Quoted][Method.Quoted] means [content] is stored titlecased and multiple consecutive blanks converted to one, and is parsed/presented with quotes
     */
    val method: Method = method ?: if (parameter.hasLeadingQuote()) Method.Quoted else Method.Dashed

    /** 'massaged' parameter text */
    val content: String = when (this.method) {
        Method.Dashed -> parameter.toDashedRepresentation()
        Method.Quoted -> parameter.toQuotedRepresentation()
    }

    /** The original parameter
     *  - Unavoidable to get a quoted representation from an instance set to Dashed in [getAutocompleteString]
     *  - Also used for [originalLength], [compareTo], [startsWith], [hashCode]
     */
    private val original: String = parameter

    //endregion
    //////////////////////////////////////////////////////////////// region Overrides

    override fun toString() = when(method) {
        Method.Dashed -> content
        Method.Quoted -> "\"$content\""
    }

    /** Comparison for two [CliInput] instances.
     *  - Two [Method.Dashed] instances are compared directly as their [content] field is already [lowercase]'ed
     *  - Two [Method.Quoted] instances are compared with `ignoreCase = true`
     *  - For mixed methods, the Quoted side is converted to Dashed representation.
     */
    override fun compareTo(other: CliInput): Int = when {
        method == Method.Dashed && other.method == Method.Dashed ->
            content.compareTo(other.content)
        method == Method.Quoted && other.method == Method.Quoted ->
            content.compareTo(other.content, ignoreCase = true)
        method == Method.Dashed ->
            content.compareTo(other.original.toDashedRepresentation())
        else ->
            original.toDashedRepresentation().compareTo(other.content)
    }

    /** Test equality for `this` parameter with either a [CliInput] or [String] [other]. Case-insensitive.
     *  @see compareTo
     */
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is CliInput -> compareTo(other) == 0
        other is String -> compareTo(other.replace("[","").replace("]",""), method) == 0
        else -> false
    }

    // Remember hashCode is not required to return different results for equals()==false,
    // but required to return equal results for equals()==true
    override fun hashCode() = getDashedRepresentation().hashCode()

    //endregion
    //////////////////////////////////////////////////////////////// region Private helpers

    private fun getAutocompleteString(paramMethod: Method, upTo: Int = content.length, toAppend: String = ""): String {
        if (paramMethod == Method.Dashed && method == Method.Dashed)
            return content.substring(0, upTo.coerceAtMost(content.length)) + toAppend
        val source = if (method == Method.Quoted) content else original.toQuotedRepresentation()
        val suffix = if (toAppend.isNotEmpty()) "\"" + toAppend else ""
        return "\"" + source.substring(0, upTo.coerceAtMost(source.length)) + suffix
    }

    private fun getDashedRepresentation() = when(method) {
        Method.Dashed -> content
        Method.Quoted -> original.toDashedRepresentation()
    }

    //endregion
    //////////////////////////////////////////////////////////////// region Public methods

    /** returns an equivalent instance with the specified method */
    fun toMethod(method: Method) = if (this.method == method) this else CliInput(original, method)

    operator fun compareTo(other: String) = compareTo(other, method)
    fun compareTo(other: String, method: Method) = compareTo(CliInput(other, method))

    /** Similar to [equals]/[compareTo] in [method] treatment, but does the comparison for the common prefix only */
    fun startsWith(other: CliInput): Boolean = when {
        method == Method.Dashed && other.method == Method.Dashed ->
            content.startsWith(other.content)
        method == Method.Quoted && other.method == Method.Quoted ->
            content.startsWith(other.content, ignoreCase = true)
        method == Method.Dashed ->
            content.startsWith(other.original.toDashedRepresentation())
        else ->
            original.toDashedRepresentation().startsWith(other.content)
    }

    fun isEmpty() = content.isEmpty()
    fun isNotEmpty() = content.isNotEmpty()

    /** length of the original parameter, for autocomplete replacing */
    fun originalLength() = original.length

    /** original parameter with any outer quotes removed, for activatetrigger parameters */
    fun originalUnquoted() = original.removeOuterQuotes()

    /** Parses `this` parameter as an Int number.
     *  @throws ConsoleErrorException if the string is not a valid representation of a number. */
    fun toInt(): Int = content.toIntOrNull() ?: throw ConsoleErrorException("'$this' is not a valid number.")

    /** Parses `this` parameter as a Float number.
     *  @throws ConsoleErrorException if the string is not a valid representation of a number. */
    fun toFloat(): Float = content.toFloatOrNull() ?: throw ConsoleErrorException("'$this' is not a valid number.")

    /** Parses `this` parameter as the name of a [Stat].
     *  @throws ConsoleErrorException if the string is not a Stat name. */
    fun toStat(): Stat = enumValueOrNull<Stat>() ?: throw ConsoleErrorException("'$this' is not an acceptable Stat.")

    /** Finds an enum instance of type [T] whose name [equals] `this` parameter.
     *  @return `null` if not found. */
    inline fun <reified T: Enum<T>> enumValueOrNull(): T? = enumValues<T>().firstOrNull { equals(it.name) }

    /** Finds an enum instance of type [T] whose name [equals] `this` parameter.
     *  @throws ConsoleErrorException if not found. */
    inline fun <reified T: Enum<T>> enumValue(): T = enumValueOrNull<T>()
        ?: throw ConsoleErrorException("'$this' is not a valid ${T::class.java.simpleName}. Options are: ${enumValues<T>().map { it.name }}.")

    /** Finds the first entry that [equals] `this` parameter.
     *  @return `null` if not found. */
    fun findOrNull(options: Iterable<String>): String? = options.firstOrNull { equals(it) }

    /** Finds the first entry that [equals] `this` parameter.
     *  @throws ConsoleErrorException if not found. */
    // YAGNI at time of writing, kept for symmetry
    fun find(options: Iterable<String>, typeName: String): String = options.firstOrNull { equals(it) }
        ?: throw ConsoleErrorException("'$this' is not a valid $typeName. Options are: ${options.joinToStringLimited()}")

    /** Finds the first entry whose [name][INamed.name] [equals] `this` parameter.
     *  @return `null` if not found. */
    fun <T: INamed> findOrNull(options: Iterable<T>): T? = options.firstOrNull { equals(it.name) }

    /** Finds the first entry whose [name][INamed.name] [equals] `this` parameter.
     *  @throws ConsoleErrorException if not found. */
    inline fun <reified T: INamed> find(options: Iterable<T>): T = findOrNull(options)
        ?: throw ConsoleErrorException("'$this' is not a valid ${T::class.java.simpleName}. Options are: ${options.joinToStringLimited { it.name }}.")

    /** Finds the first entry whose [name][INamed.name] [equals] `this` parameter.
     *  @return `null` if not found. */
    fun <T: INamed> findOrNull(options: Sequence<T>): T? = options.firstOrNull { equals(it.name) }

    /** Finds the first entry whose [name][INamed.name] [equals] `this` parameter.
     *  @throws ConsoleErrorException if not found. */
    inline fun <reified T: INamed> find(options: Sequence<T>): T = find(options.asIterable())

    //endregion

    companion object {
        val empty = CliInput("")

        //////////////////////////////////////////////////////////////// region Private Helpers

        private fun String.hasLeadingQuote() = startsWith('\"')
        private fun String.toDashedRepresentation() = removeOuterQuotes().lowercase().replace(" ","-")
        private fun String.removeOuterQuotes() = removePrefix("\"").removeSuffix("\"")

        /**
         *  Parses input for storage as [content] with [Method.Quoted].
         *  @return Input without any surrounding quotes, no repeated whitespace, and the first character titlecased
         */
        private fun String.toQuotedRepresentation(): String {
            // Can be done with String functions, but this might be faster
            val sb = StringBuilder(length)
            val start = indexOfFirst(::charIsNotAQuote).coerceAtLeast(0)
            val end = indexOfLast(::charIsNotAQuote) + 1
            if (end > start) {
                sb.append(get(start).titlecaseChar())
                if (end > start + 1)
                    sb.append(substring(start + 1, end).replace(repeatedWhiteSpaceRegex, ""))
            }
            return sb.toString()
        }

        private fun charIsNotAQuote(char: Char) = char != '\"'

        private val repeatedWhiteSpaceRegex = Regex("""(?<=\s)\s+""")
        // Read: Any whitespace sequence preceded by a whitespace (using positive lookbehind, so the preceding whitespace is not part of the match)

        @Suppress("RegExpRepeatedSpace", "RegExpUnnecessaryNonCapturingGroup")
        private val splitStringRegex = Regex("""
            "[^"]+(?:"|$)       # A quoted phrase, but the closing quote is optional at the end of the string
            |                   # OR
            \S+                 # consecutive non-whitespace
            |                   # OR
            (?:(?<=\s)$)        # a terminal empty string if preceded by whitespace
        """, RegexOption.COMMENTS)
        // (the unfinished quoted string or empty token at the end are allowed to support autocomplete)

        //endregion

        fun String.splitToCliInput() =
            splitStringRegex.findAll(this)
                .map { CliInput(it.value) }
                .toList()

        fun CliInput?.orEmpty() = this ?: empty

        @Suppress("USELESS_CAST")  // not useless, filterIsInstance annotates `T` with `@NoInfer`
        /** Finds ruleset objects of type [T] whose name matches parameter [param].
         *  Receiver [DevConsolePopup] is used to access the ruleset.
         *  - Note this has a level of redundancy with the [CliInput.find] and [CliInput.findOrNull] family of methods.
         *    (Actually this delegates to [CliInput.findOrNull] but passes `allRulesetObjects().filterIsInstance<T>` as collection to search).
         *    `param.findOrNull(console.gameInfo.ruleset.<collectionOfT>)` is more efficient than this but more verbose to write, and this can find subclasses of <T>.
         */
        inline fun <reified T: IRulesetObject> DevConsolePopup.findCliInput(param: CliInput) =
            param.findOrNull(gameInfo.ruleset.allRulesetObjects().filterIsInstance<T>() as Sequence<T>)

        /** For use in overrides of [ConsoleCommand.autocomplete]:
         *  Gets the string to replace the last parameter [lastWord] with from [allOptions].
         */
        internal fun getAutocompleteString(lastWord: CliInput, allOptions: Iterable<CliInput>, console: DevConsolePopup): String? {
            console.showResponse(null, Color.WHITE)

            val matchingOptions = allOptions.filter { it.startsWith(lastWord) }
            if (matchingOptions.isEmpty()) return null
            if (matchingOptions.size == 1)
                return matchingOptions.first().getAutocompleteString(lastWord.method, toAppend = " ")

            val showMethod = lastWord.method or matchingOptions.first().method
            val message = matchingOptions.asSequence()
                .map { it.toMethod(showMethod) }
                .asIterable()
                .joinToStringLimited(prefix = "Matching completions: ")
            console.showResponse(message, Color.LIME.lerp(Color.OLIVE.cpy(), 0.5f))

            val firstOption = matchingOptions.first()
            for ((index, char) in firstOption.getDashedRepresentation().withIndex()) {
                if (matchingOptions.any { it.getDashedRepresentation().lastIndex < index } ||
                    matchingOptions.any { it.getDashedRepresentation()[index] != char })
                    return firstOption.getAutocompleteString(lastWord.method, index)
            }
            return firstOption.getAutocompleteString(lastWord.method)  // don't add space, e.g. found drill-i and user might want drill-ii
        }

        @JvmName("getAutocompleteStringFromStrings")
        internal fun getAutocompleteString(lastWord: CliInput, allOptions: Iterable<String>, console: DevConsolePopup): String? =
            getAutocompleteString(lastWord, allOptions.map { CliInput(it) }, console)

        private fun <T> Iterable<T>.joinToStringLimited(separator: String = ", ", prefix: String = "", postfix: String = "", limit: Int = 42, transform: ((T)->String)? = null)
            = joinToString(separator, prefix, postfix, limit, "... (${count() - limit} not shown)", transform)
    }
}
