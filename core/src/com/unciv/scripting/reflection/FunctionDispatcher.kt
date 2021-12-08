package com.unciv.scripting.reflection

import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.jvm.jvmErasure

// I'm choosing to define this as a class to avoid having to pass the three configuration parameters through every function, but I do suspect that instantiating the class for every dynamic function call may be measurably slower than making the whole class a singleton object and passing the configurations through function arguments instead.

// Return a [FunctionDispatcher]() with consistent settings for the scripting API.
fun makeFunctionDispatcher(functions: Collection<KCallable<Any?>>) = FunctionDispatcher(
    functions = functions,
    matchNumbersLeniently = true,
    matchClassesQualnames = false,
    resolveAmbiguousSpecificity = true
)

/**
 * Dynamic dispatch to one of multiple KCallables.
 *
 * Uses reflection to narrow down functions to the one(s) that have the correct signature for a given array of arguments
 *
 * Varargs can be used, but they must be supplied as a single correctly typed array instead of as separate arguments.
 *
 * @property functions List of functions against which to resolve calls.
 * @property matchNumbersLeniently Whether to treat all numeric types as the same. Useful for E.G. untyped deserialized data. Adds small extra step to most calls.
 * @property matchClassesQualnames Whether to treat classes as the same if they have the same qualifiedName. Useful for E.G. ignoring the invariant arrays representing vararg parameters. Adds small extra step to some calls.
 * @property resolveAmbiguousSpecificity Whether to try to resolve multiple ambiguous matching signatures by finding one that strictly subtypes all others. Rules for this are documented under getMostSpecificCallable. Does not add any extra steps unless needed; Increases function domain properly handled but does not decrease performance in other uses.
 */
open class FunctionDispatcher(
    val functions: Collection<KCallable<Any?>>,
    val matchNumbersLeniently: Boolean = false,
    val matchClassesQualnames: Boolean = false,
    val resolveAmbiguousSpecificity: Boolean = false
) {

    // Could try to implement KCallable interface. But not sure it's be worth it or map closely enough— What do lambdas do? I guess isOpen, isAbstract, etc should just all be False?

    // Not supporting varargs for now. Doing so would require rebuilding the arguments array to move all vararg arguments into a new array for KCallable.call().

    // Right. It's called "Overload Resolution" when done statically, and Kotlin has specs under that title.

    /**
     * @return Whether a given argument value can be cast to the type of a given KParameter.
     */
    private fun checkParameterMatches(kparam: KParameter, arg: Any?, paramKtypeAppend: ArrayList<KType>): Boolean {
        // If performance becomes an issue, try inlining these. Then again, the JVM presumably optimizes it at runtime already (and there's far more calls than this containing function).
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
            // NOTE: However, doesn't correctly forbid unconvertible types. E.G. Doubles match against Floats.
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
        return params.size == arguments.size
            && (params zip arguments).all { // Check argument classes match parameter types, skipping the receiver.
                (kparam, arg) -> checkParameterMatches(kparam, arg, paramKtypeAppend=ktypeappend)
            }
    }

    /**
     * @return The KCallables that have a signature which may be compatible with a given Array of arguments.
     */
    private fun getMatchingCallables(arguments: Array<Any?>, paramKtypeAppends: HashMap<KCallable<Any?>, ArrayList<KType>>): List<KCallable<Any?>> {
        // Private because subclasses may choose to modify the arguments passed to call().
        return functions.filter { checkCallableMatches(it, arguments, paramKtypeAppends) }
    }

    // Given a List of KCallables and a Map of their parameters' types, try to find one KCallable the signature of which is a subtype of all of the others.

    // For a KCallable to be returned, the following criteria must be true:
    //  Every relevant parameter type in it must be either the same as the corresponding parameter type in every other KCallable or a subtype of the corresponding parameter type in every other KCallable.
    //  At least one parameter type in it must be a strict subtype of the corresponding parameter type for every other KCallable.

    // This is essentially equivalent to the behaviour specified in Chapter 11.7 "Choosing the most specific candidate from the overload candidate set" of the Kotlin language specification.

    //
    private fun getMostSpecificCallable(matches: List<KCallable<Any?>>, paramKtypes: Map<KCallable<Any?>, ArrayList<KType>>): KCallable<Any?>? {
        // Private because subclasses may choose to modify the arguments passed to call().
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
                        // Identical types that neither allow nor forbid a match.
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
     * Call the correct function for a given array of arguments.
     *
     * @param arguments The arguments with which to call the function.
     * @return The result from dispatching the given arguments to the function definition with a compatible signature.
     * @throws IllegalArgumentException If no compatible signature was found, or if more than one compatible signature was found.
     */
    open fun call(arguments: Array<Any?>): Any? { // TODO: Let return be typed.
        // KCallable's .call() takes varargs instead of an array object. But spreads are expensive, so I'm not doing that.
        // To test from Python:
        // gameInfo.civilizations.add(1, civInfo)
        // gameInfo.civilizations.add(civInfo)
        // Both need to work.
        // Supporting named parameters would greatly complicate both signature matching and specificity resolution, and is not currently planned.
        val callableparamktypes = hashMapOf<KCallable<Any?>, ArrayList<KType>>()
        // Map of all traversed KCallables to lists of their parameters' KTypes.
        // Only parameters, and not arguments, are saved, though both are traversed.
        // KCallables that don't match the call arguments should only have as many parameter KTypes saved as it took to find out they don't match.
        val matches = getMatchingCallables(arguments, paramKtypeAppends=callableparamktypes)
        var match: KCallable<Any?>? = null
        if (matches.isEmpty()) {
            throw IllegalArgumentException("No matching signatures found for calling ${nounifyFunctions()} with given arguments: (${arguments.map {if (it == null) "null" else it::class?.simpleName ?: "null"}.joinToString(", ")})")
            //FIXME: A lot of non-null assertions and null checks (not here, but generally in the codebase) can probably be replaced with safe calls.
        }
        if (matches.size > 1) {
            if (resolveAmbiguousSpecificity) {
                //Kotlin seems to choose the most specific signatures based on inheritance hierarchy.
                //E.G. UncivGame.setScreen(), which uses a more specific parameter type than its GDX base class and thus creates a different, semi-ambiguous (sub-)signature, but still gets resolved.
                //Yeah, Kotlin semantics "choose the most specific function": https://kotlinlang.org/spec/overload-resolution.html#the-forms-of-call-expression
                match = getMostSpecificCallable(matches, paramKtypes = callableparamktypes)
            }
            if (match == null) {
                throw IllegalArgumentException("Multiple matching signatures found for calling ${nounifyFunctions()} with given arguments:\n\t(${arguments.map {if (it == null) "null" else it::class?.simpleName ?: "null"}.joinToString(", ")})\n\t${matches.map {it.toString()}.joinToString("\n\t")}")
            }
        } else {
            match = matches[0]!!
        }
        return match.call(
            *arguments
        )
    }

    /**
     * @return A short, human-readable string that describes the target functions collectively.
     */
    open fun nounifyFunctions() = "${functions.size} functions"
}
