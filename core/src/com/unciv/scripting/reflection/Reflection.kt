package com.unciv.scripting.reflection

import com.unciv.scripting.utils.TokenizingJson
import kotlin.collections.ArrayList
import kotlin.reflect.KCallable
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.jvm.jvmErasure
import kotlinx.serialization.Serializable
import java.util.*


object Reflection {

    @Suppress("UNCHECKED_CAST")
    fun <R> readInstanceProperty(instance: Any, propertyName: String): R? {
        // From https://stackoverflow.com/a/35539628/12260302
        val property = instance::class.members
            .first { it.name == propertyName } as KProperty1<Any, *>
            // If scripting member access performance becomes an issue, memoizing this could be a potential first step.
            // TODO: Throw more helpful error on failure.
        return property.get(instance) as R?
    }


    /**
     * Dynamic multiple dispatch for Any Kotlin instances by methodName.
     *
     * Uses reflection to first find all members matching the expected method name, and then to narrow them down to members that have the correct signature for a given array of arguments.
     *
     * Varargs can be used, but they must be supplied as a single correctly typed array instead of separate arguments.
     *
     * @property instance The receiver on which to find and call a method.
     * @property methodName The name of the method to resolve and call.
     * @property matchNumbersLeniently Whether to treat all numeric types as the same. Useful for E.G. untyped deserialized data. Adds small extra step to most calls.
     * @property matchClassesQualnames Whether to treat classes as the same if they have the same qualifiedName. Useful for E.G. ignoring the invariant arrays representing vararg parameters. Adds small extra step to many calls.
     * @property resolveAmbiguousSpecificity Whether to try to resolve multiple ambigous matching signatures by finding one that strictly subtypes all others. Rules for this are documented under getMostSpecificCallable. No impact when not needed; Increases function domain properly handled but does not decrease performance in other uses.
     */
    class InstanceMethodDispatcher(
        val instance: Any,
        val methodName: String,
        val matchNumbersLeniently: Boolean = false,
        val matchClassesQualnames: Boolean = false,
        val resolveAmbiguousSpecificity: Boolean = false
        ) {
        // TODO: Allow manually matching to supplied KCallables, E.G., KClass.constructors.
        // Probably: Move actual resolution into a singleton, and prepend the receiver instance to the arguments here instead of skipping it its params during resolution.

        // This isn't just a nice-to-have feature. Before I implemented it, identical calls from demo scripts to methods with multiple versions (E.G. ArrayList().add()) would rarely but randomly fail because the member/signature that was found would change between runs or compilations.

        // TODO: This is going to need unit tests.

        // Could try to implement KCallable interface. But not sure it's be worth it or map closely enough— What do lambdas do? I guess isOpen, isAbstract, etc should just all be False?

        // Not supporting varargs for now. Doing so would require rebuilding the arguments array to move all vararg arguments into a new array for KCallable.call().

        /**
         * Lazily evaluated list of KCallables for every method that matches the given name.
         */
        val methods: List<KCallable<Any?>> by lazy { instance::class.members.filter{ it.name == methodName }.map{ it as KCallable<Any?> } }
        //Filter down to is KCallable if name collisions with properties are a possible issue.

        /**
         * @return Useful representative text.
         */
        override fun toString() = """${this::class.simpleName}(instance=${this.instance::class.simpleName}(), methodName="${this.methodName}") with ${this.methods.size} dispatch candidates"""
        // Used by "docstring" packet action in ScriptingProtocol, which is in turn exposed in interpeters as help text. TODO: Could move to an extension method in ScriptingProtocol, I suppose.

        /**
         * @return Whether a given argument value can be cast to the type of a given KParameter.
         */
        private fun checkParameterMatches(kparam: KParameter, arg: Any?, paramKtypeAppend: ArrayList<KType>): Boolean {
            // TODO: If performance becomes an issue, try inlining these. Then again, the JVM presumably optimizes it at runtime already (and there's far more calls than this containing function).
    //        val ktype = kparam.type // Necessary. For some reason, accessing kparam.ktype here seems to cause kparam.ktype.isMarkedNullable to be incorrectly false in the first "if" block.
    //        val isMarkedNullable = ktype.isMarkedNullable
            paramKtypeAppend.add(kparam.type)
            if (arg == null) {
                // Multiple dispatch of null between Any and Any? seems ambiguous in Kotlin even without reflection.
                // Here, I'm resolving it myself, so it seems fine.
                // However, with generics, even if I find the right KCallable, it seems that a nullable argument T? will usually (but not always, depending on each time you compile) be sent to the non-nullable T version of the function if one has been defined.
                // KCallable.toString() shows the nullable signature, and KParam.name shows the argument name from the nullable version. But an exception is still thrown on .call() with null, and its text will use the argument name from the non-nullable version.
                // I suppose it's not a problem here as it seems broken in Kotlin generally.
                return kparam.type.isMarkedNullable
            }
            val kparamcls = kparam.type.jvmErasure
            val argcls = arg::class
            if (matchNumbersLeniently && argcls.isSubclassOf(Number::class) && kparamcls.isSubclassOf(Number::class)) {
                // I think/hope this basically causes Java-style implicit conversions (or Kotlin implicit casts?).
                // NOTE: However, doesn't correctly forbid unconvertible types.
                // Info: https://docs.oracle.com/javase/specs/jls/se7/html/jls-5.html#jls-5.1.2
                return true
            }
            return kparamcls.isSuperclassOf(argcls)
            // Seems to also work for generics, I guess.
                || (matchClassesQualnames && kparamcls.qualifiedName != null && argcls.qualifiedName != null && kparamcls.qualifiedName == argcls.qualifiedName)
                // Lets more types be matched to invariants, such as for vararg arrays.
                // However, the JVM still throws its own error in that case, so leaving this disabled for now.
        }

        /**
         * @return Whether a given KCallable's signature might be compatible with a given Array of arguments.
         */
        private fun checkCallableMatches(callable: KCallable<Any?>, arguments: Array<Any?>, paramKtypeAppends: HashMap<KCallable<Any?>, ArrayList<KType>>): Boolean {
            // I'm not aware of any situation where this function's behaviour will deviate from Kotlin, but that doesn't mean there aren't any. Wait, no. I do know that runtime checking and resolution of erased generics will probably be looser than at compile time. They seem to act like Any(?).
            val ktypeappend = arrayListOf<KType>()
            paramKtypeAppends[callable] = ktypeappend
            val params = callable.parameters
            return params.size == arguments.size + 1 // Check that the parameters size is same as arguments size plus one for the receiver.
                && (params.slice(1..arguments.size) zip arguments).all { // Check argument classes match parameter types, skipping the receiver.
                    (kparam, arg) -> checkParameterMatches(kparam, arg, paramKtypeAppend=ktypeappend)
                }
        }

        /**
         * @return A list containing a KCallable for every version of this dispatcher's method that has a signature which may be compatible with a given Array of arguments.
         */
        fun getMatchingCallables(arguments: Array<Any?>, paramKtypeAppends: HashMap<KCallable<Any?>, ArrayList<KType>>): List<KCallable<Any?>> {
            return methods.filter { checkCallableMatches(it, arguments, paramKtypeAppends) }
        }

        // Given a List of KCallables and a Map of their parameters' types, try to find one KCallable the signature of which is a subtype of all of the others.

        // For a KCallable to be returned, the following criteria must be true:
        //  Every relevant parameter type in it must be either the same as the corresponding parameter type in every other KCallable or a subtype of the corresponding parameter type in every other KCallable.
        //  At least one parameter type in it must be a strict subtype of the corresponding parameter type for every other KCallable.

        //
        fun getMostSpecificCallable(matches: List<KCallable<Any?>>, paramKtypes: Map<KCallable<Any?>, ArrayList<KType>>): KCallable<Any?>? {
            // Should only be called when multiple/ambiguous signatures have been found.
            return matches.firstOrNull { // Check all signatures.
                val checkcallable = it // The signature we are currently checking for specificity.
                val checkparamktypes = paramKtypes[checkcallable]!!
                matches.all { // Compare currently checked signature to all other signatures. It must be a strict subtype of all other signatures.
                    val othercallable = it
                    if (checkcallable == othercallable) {
                        // Don't check against itself.
                        return@all true
                    }
                    var subtypefound = false
                    var supertypefound = false
                    for ((checkktype, otherktype) in checkparamktypes zip paramKtypes[othercallable]!!) {
                        // Compare all parameter types of currently checked signature to all parameter types of the other signature we are currently comparing it to.
                        if (checkktype == otherktype) {
                            // Identical types have no implications.
                            continue
                        }
                        if (!subtypefound && checkktype.isSubtypeOf(otherktype)) {
                            // At least one strict subtype is needed.
                            subtypefound = true
                        }
                        if (checkktype.isSupertypeOf(otherktype)) {
                            // No strict supertypes are allowed.
                            supertypefound = true
                            break
                        }
                    }
                    (subtypefound && !supertypefound)
                    // I did something similar for Cython once. Well, specifically, someone else had done something similar, and like this, it was running in exponential time or something, so I made it faster by building an index.
                }
            }
        }

        /**
         * Call the correct version of the method for a given array of arguments.
         *
         * @param arguments The arguments with which to call the method.
         * @return The result from dispatching the given arguments to the method definition with a compatible signature.
         * @throws IllegalArgumentException If no compatible signature was found, or if more than one compatible signature was found.
         */
        fun call(arguments: Array<Any?>): Any? {
            // KCallable's .call() takes varargs instead of an array object. But spreads are expensive, so I'm not doing that.
            // To test from Python:
            // gameInfo.civilizations.add(1, civInfo)
            // gameInfo.civilizations.add(civInfo)
            // Both need to work.
            val callableparamktypes = hashMapOf<KCallable<Any?>, ArrayList<KType>>()
            // Map of all traversed KCallables to lists of their parameters' KTypes.
            // Only parameters, and not arguments, are saved, though both are traversed.
            // KCallables that don't match the call arguments should only have as many parameter KTypes saved as it took to find out they don't match.
            val matches = getMatchingCallables(arguments, paramKtypeAppends=callableparamktypes)
            var match: KCallable<Any?>? = null
            if (matches.size < 1) {
                throw IllegalArgumentException("No matching signatures found for calling ${instance::class?.simpleName}.${methodName} with given arguments: (${arguments.map{if (it == null) "null" else it::class?.simpleName ?: "null"}.joinToString(", ")})")
                //FIXME: A lot of non-null assertions and null checks (not here, but generally in the codebase) can probably be replaced with safe calls.
            }
            if (matches.size > 1) {
                if (resolveAmbiguousSpecificity) {
                    //Kotlin seems to choose the most specific signatures based on inheritance hierarchy.
                    //E.G. UncivGame.setScreen(), which uses a more specific parameter type than its GDX base class and thus creates a different, semi-ambiguous (sub-)signature, but still gets resolved.
                    match = getMostSpecificCallable(matches, paramKtypes = callableparamktypes)
                }
                if (match == null) {
                    throw IllegalArgumentException("Multiple matching signatures found for calling ${methodName} with given arguments:\n\t(${arguments.map{if (it == null) "null" else it::class?.simpleName ?: "null"}.joinToString(", ")})\n\t${matches.map{it.toString()}.joinToString("\n\t")}")
                }
            } else {
                match = matches[0]!!
            }
            return match.call(
                instance,
                *arguments
            )
        }
    }


    fun readInstanceItem(instance: Any, keyOrIndex: Any): Any? {
        // TODO: Make this work with operator overloading. Though Map is already an interface that anything can implement, so maybe not.
        if (keyOrIndex is Int) {
            return (instance as List<Any?>)[keyOrIndex]
        } else {
            return (instance as Map<Any, Any?>)[keyOrIndex]
        }
    }


    fun <T> setInstanceProperty(instance: Any, propertyName: String, value: T?): Unit {
        val property = instance::class.members
            .first { it.name == propertyName } as KMutableProperty1<Any, T?>
        property.set(instance, value)
    }

    fun setInstanceItem(instance: Any, keyOrIndex: Any, value: Any?): Unit {
        if (keyOrIndex is Int) {
            (instance as MutableList<Any?>)[keyOrIndex] = value
        } else {
            (instance as MutableMap<Any, Any?>)[keyOrIndex] = value
        }
    }

    fun removeInstanceItem(instance: Any, keyOrIndex: Any): Unit {
        if (keyOrIndex is Int) {
            (instance as MutableList<Any?>).removeAt(keyOrIndex)
        } else {
            (instance as MutableMap<Any, Any?>).remove(keyOrIndex)
        }
    }


    enum class PathElementType() {
        Property(),
        Key(),
        Call()
    }

    @Serializable
    data class PathElement(
        val type: PathElementType,
        val name: String,
        /**
         * For key and index accesses, and function calls, whether to evaluate name instead of using params for arguments/key.
         * This lets simple parsers be written and used, that can simply break up a common subset of many programming languages into string components without themselves having to analyze or understand any more complex semantics.
         *
         * Default should be false, so deserialized JSON path lists are configured correctly in ScriptingProtocol.kt.
         */
        val doEval: Boolean = false,
        val params: List<@Serializable(with=TokenizingJson.TokenizingSerializer::class) Any?> = listOf()
        //val namedParams
        //Probably not worth it. But if you want to add support for named arguments in calls (which will also require changing InstanceMethodDispatcher's multiple dispatch resolution, and which respect default arguments), then it will probably have to be in a new field.
    )


    private val brackettypes: Map<Char, String> = mapOf(
        '[' to "[]",
        '(' to "()"
    )

    private val bracketmeanings: Map<String, PathElementType> = mapOf(
        "[]" to PathElementType.Key,
        "()" to PathElementType.Call
    )

    fun parseKotlinPath(code: String): List<PathElement> {
        var path: MutableList<PathElement> = ArrayList<PathElement>()
        var curr_type = PathElementType.Property
        var curr_name = ArrayList<Char>()
        var curr_brackets = ""
        var curr_bracketdepth = 0
        var just_closed_brackets = true
        for (char in code) {
            if (curr_bracketdepth == 0) {
                if (char == '.') {
                    if (!just_closed_brackets) {
                        path.add(PathElement(
                            PathElementType.Property,
                            curr_name.joinToString("")
                        ))
                    }
                    curr_name.clear()
                    just_closed_brackets = false
                    continue
                }
                if (char in brackettypes) {
                    if (!just_closed_brackets) {
                        path.add(PathElement(
                            PathElementType.Property,
                            curr_name.joinToString("")
                        ))
                    }
                    curr_name.clear()
                    curr_brackets = brackettypes[char]!!
                    curr_bracketdepth += 1
                    just_closed_brackets = false
                    continue
                }
                curr_name.add(char)
            }
            just_closed_brackets = false
            if (curr_bracketdepth > 0) {
                if (char == curr_brackets[1]) {
                    curr_bracketdepth -= 1
                    if (curr_bracketdepth == 0) {
                        path.add(PathElement(
                            bracketmeanings[curr_brackets]!!,
                            curr_name.joinToString(""),
                            true
                        ))
                        curr_brackets = ""
                        curr_name.clear()
                        just_closed_brackets = true
                        continue
                    }
                } else if (char == curr_brackets[0]) {
                    curr_bracketdepth += 1
                }
                curr_name.add(char)
            }
        }
        if (!just_closed_brackets && curr_bracketdepth == 0) {
            path.add(PathElement(
                PathElementType.Property,
                curr_name.joinToString("")
            ))
            curr_name.clear()
        }
        if (curr_bracketdepth > 0) {
            throw IllegalArgumentException("Unclosed parentheses.")
        }
        return path
    }


//    fun stringifyKotlinPath() {
//    }

//    private val closingbrackets = null

//    data class OpenBracket(
//        val char: Char,
//        var offset: Int
//    )

    //class OpenBracketIterator() {
    //}


    //fun getOpenBracketStack() {
    //}

    fun splitToplevelExprs(code: String, delimiters: String = ","): List<String> {
        return code.split(',').map{ it.trim(' ') }
        var segs = ArrayList<String>()
        val bracketdepths = mutableMapOf<Char, Int>(
            *brackettypes.keys.map{ it to 0 }.toTypedArray()
        )
        //TODO: Actually try to parse for parenthesization, strings, etc.
    }


    fun resolveInstancePath(instance: Any, path: List<PathElement>): Any? {
        //TODO: Allow passing an ((Any?)->Unit)? (or maybe Boolean) function as a parameter that gets called at every stage of resolution, to let exceptions be thrown if accessing something not whitelisted.
        var obj: Any? = instance
        for (element in path) {
            when (element.type) {
                PathElementType.Property -> {
                    try {
                        obj = readInstanceProperty(obj!!, element.name)
                    } catch (e: ClassCastException) {
                        obj = InstanceMethodDispatcher(
                            obj!!,
                            element.name,
                            matchNumbersLeniently = true,
                            matchClassesQualnames = false,
                            resolveAmbiguousSpecificity = true
                        )
                    }
                }
                PathElementType.Key -> {
                    obj = readInstanceItem(
                        obj!!,
                        if (element.doEval)
                            evalKotlinString(instance!!, element.name)!!
                        else
                            element.params[0]!!
                    )
                }
                PathElementType.Call -> {
                    // TODO: Handle invoke operator. Easy enough, just recurse to access the .invoke.
                    // Maybe TODO: Handle lambdas. E.G. WorldScreen.nextTurnAction. Honestly, it may be better to just expose wrapping non-lambdas.
                    obj = (obj as InstanceMethodDispatcher).call(
                        (
                            if (element.doEval)
                                splitToplevelExprs(element.name).map{ evalKotlinString(instance!!, it) }
                            else
                                element.params
                        ).toTypedArray()
                    )
                }
                else -> {
                    throw UnsupportedOperationException("Unknown path element type: ${element.type}")
                }
            }
        }
        return obj
    }


    fun evalKotlinString(scope: Any, string: String): Any? {
        val trimmed = string.trim(' ')
        if (trimmed == "null") {
            return null
        }
        if (trimmed == "true") {
            return true
        }
        if (trimmed == "false") {
            return false
        }
        if (trimmed.length > 1 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            return trimmed.slice(1..trimmed.length-2)
        }
        val asint = trimmed.toIntOrNull()
        if (asint != null) {
            return asint
        }
        val asfloat = trimmed.toFloatOrNull()
        if (asfloat != null) {
            return asfloat
        }
        return resolveInstancePath(scope, parseKotlinPath(trimmed))
    }


    fun setInstancePath(instance: Any, path: List<PathElement>, value: Any?): Unit {
        val leafobj = resolveInstancePath(instance, path.slice(0..path.size-2))
        val leafelement = path[path.size - 1]
        when (leafelement.type) {
            PathElementType.Property -> {
                setInstanceProperty(leafobj!!, leafelement.name, value)
            }
            PathElementType.Key -> {
                setInstanceItem(
                    leafobj!!,
                    if (leafelement.doEval)
                        evalKotlinString(instance, leafelement.name)!!
                    else
                        leafelement.params[0]!!,
                    value
                )
            }
            PathElementType.Call -> {
                throw UnsupportedOperationException("Cannot assign to function call.")
            }
            else -> {
                throw UnsupportedOperationException("Unknown path element type: ${leafelement.type}")
            }
        }
    }

    fun removeInstancePath(instance: Any, path: List<PathElement>): Unit {
        val leafobj = resolveInstancePath(instance, path.slice(0..path.size-2))
        val leafelement = path[path.size - 1]
        when (leafelement.type) {
            PathElementType.Property -> {
                throw UnsupportedOperationException("Cannot remove instance property.")
            }
            PathElementType.Key -> {
                removeInstanceItem(
                    leafobj!!,
                    if (leafelement.doEval)
                        evalKotlinString(instance, leafelement.name)!!
                    else
                        leafelement.params[0]!!
                )
            }
            PathElementType.Call -> {
                throw UnsupportedOperationException("Cannot remove function call.")
            }
            else -> {
                throw UnsupportedOperationException("Unknown path element type: ${leafelement.type}")
            }
        }
    }
}
