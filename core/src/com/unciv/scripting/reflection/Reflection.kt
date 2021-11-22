package com.unciv.scripting.reflection

import com.unciv.scripting.utils.TokenizingJson
import kotlin.collections.ArrayList
import kotlin.reflect.KCallable
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
//import kotlin.reflect.KType
//import kotlin.reflect.full.allSupertypes
//import kotlin.reflect.full.defaultType
//import kotlin.reflect.full.createType
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.jvmErasure
//import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*


//TODO: Method dispatch to right methods?

object Reflection {
    @Suppress("UNCHECKED_CAST")
    fun <R> readInstanceProperty(instance: Any, propertyName: String): R? {
        // From https://stackoverflow.com/a/35539628/12260302
        val property = instance::class.members
            .first { it.name == propertyName } as KProperty1<Any, *>
        return property.get(instance) as R?
    }

    class InstanceMethodDispatcher(val instance: Any, val methodName: String) {
        // This isn't just a nice-to-have feature. Before I implemented it, identical calls from demo scripts to methods with multiple dispatches (E.G. ArrayList().add()) would rarely but randomly fail because the member/signature that was found would change between runs or compilations.
        // TODO: This is going to need unit tests.
        // Could try to implement KCallable interface. But not sure it's be worth it or map closely enough— What do lambdas do? I guess isOpen, isAbstract, etc should just all be False?
        val methods: List<KCallable<Any>> by lazy { instance::class.members.filter{ it.name == methodName }.map{ it as KCallable<Any> } }
        //Also filter down to is KCallable if name collisions with properties are a possible issue.

        fun debugprint(v: Any?): Unit {
            if (false) {
                println(v)
            }
        }

    //        private fun ktypeQualified(ktype: KType): String {
    //            return ktype.toString()//.classifier.qualifiedName
            // Identical to ktype.toString(), I believe.
    //        }

    //    private fun checkKtypeGeneric(ktype: KType): Boolean {
    //        //I'm not seeing any way to check for this in the API... Maybe seeing if .jvmErasure or .classifier errors.
    //        //But the single capital letter convention seems pretty universal. And I think real classes will have a dot in their name. So this will hopefully catch all cases.
    //        debugprint("checkKtypeGeneric")
    //        debugprint(ktype::class)
    //        return false
    //        return ktype.classifier !is KClass<*> //Interfaces?
    //
    //    }

        //@OptIn(kotlin.ExperimentalStdlibApi::class) //Required for typeOf<>()
        private fun checkParameterMatches(kparam: KParameter, arg: Any?): Boolean {
            // Check that every argument can be cast to the expected type.
            debugprint("\n\ncheckParameterMatches()")
            //if (arg != null) debugprint("kparam.type: ${kparam.type}\nkparam.type.classifier: ${kparam.type.classifier}\n\narg::class.createType(): ${arg::class.createType()}\n\narg::class.allSupertypes: ${arg::class.allSupertypes}")
            if (arg == null) {
                // Multiple dispatch of null between Any and Any? seems ambiguous in Kotlin even without reflection.
                // Here, I'm resolving it myself, so it seems fine.
                // However, with generics, even if I find the right KCallable, it seems that a nullable argument T? will usually (but not always, depending on when you compiled) be sent to the non-nullable T version of the function.
                // KCallable.toString() shows the nullable signature, and KParam.name shows the argument name from the nullable version. But an exception is still thrown, and its text will use the argument name from the non-nullable version.
                return kparam.type.isMarkedNullable
    //        } else if (checkKtypeGeneric(kparam.type)) {
    //            //Generics are erased at runtime. Type arguments aren't allowed for type parameters, so I can't try to do some kind of "inline fun <C, reified T: Any> typeofany(l: C<T>) = T::class" either to get the instance's type arguments. So the best I'm going to try to do is to treat them like Any?.
    //            return true
            } else {
                return kparam.type.jvmErasure.isSuperclassOf(arg::class)
                    //Seems to also work for generics, I guess.
                    //|| kparam.type in arg::class.allSupertypes
                    // Not totally sure if these KTypes are singleton-like. Hopefully equality-comparison works for containment here even if they aren't?
            }
        }

        private fun checkCallableMatches(callable: KCallable<Any>, arguments: Array<Any?>): Boolean {
            val params = callable.parameters
            debugprint("\n\ncheckCallableMatches()")
            debugprint("params: ${params.map{ it.type }}")
            return params.size == arguments.size + 1 // Check that the parameters size is same as arguments size plus one for the receiver.
                && (params.slice(1..arguments.size) zip arguments).all {
                    (kparam, arg) -> checkParameterMatches(kparam, arg)
                }
        }

        fun getMatchingCallables(arguments: Array<Any?>): List<KCallable<Any>> {
            debugprint("\ngetMatchingCallables()")
            debugprint("instance::class.typeParameters: ${instance::class.typeParameters}")
            debugprint("\narguments: ${arguments.toList()}")
            return methods.filter { checkCallableMatches(it, arguments) }
        }

        fun call(arguments: Array<Any?>): Any? {
            // KCallable's .call() takes varargs instead of an array object. But spreads are expensive.
            // To test from Python:
            // gameInfo.civilizations.add(1, civInfo)
            // gameInfo.civilizations.add(civInfo)
            // Both need to work.
            debugprint("call()")
            val matches = getMatchingCallables(arguments)
            //println("matches: $matches")
            //println(arguments.toList())//
            if (matches.size < 1) {
                throw IllegalArgumentException("No matching signatures found for calling ${instance::class?.simpleName}.${methodName} with given arguments: (${arguments.map{if (it == null) "null" else it::class?.simpleName ?: "null"}.joinToString(", ")})")
                //FIXME: A lot of non-null assertions and null checks (not here, but generally in the codebase) can probably be replaced with safe calls.
            }
            if (matches.size > 1) {
                //I guess could also allow this, producing ambiguous behaviour and leaving it up to the methods to avoid creating such scenarios. Actually no, that sounds like a terrible idea reading it back.
                //Could try to choose most specific signatures based on inheritance hierarchy. Does Kotlin do that? Probably not. So shouldn't do it here, then, if that's the case.
                throw IllegalArgumentException("Multiple matching signatures found for calling ${methodName}:\n\t${matches.map{it.toString()}.joinToString("\n\t")}")
            }
            return matches[0]!!.call(
                    instance,
                    *arguments
                )
        }
    }

//    fun readInstanceMethod(instance: Any, methodName: String): KCallable<Any> {
//        val method = instance::class.members
//            .first { it.name == methodName } as KCallable<Any>
//        return method
//    }
    fun readInstanceMethod(instance: Any, methodName: String) = InstanceMethodDispatcher(instance, methodName)

    fun readInstanceItem(instance: Any, keyOrIndex: Any): Any? {
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


    fun stringifyKotlinPath() {
    }

    private val closingbrackets = null

    data class OpenBracket(
        val char: Char,
        var offset: Int
    )

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
        var lastobj0: Any? = null
        var lastobj1: Any? = null // Keep the second last object traversed, for function calls to bind to.
        for (element in path) {
            lastobj1 = lastobj0
            lastobj0 = obj
            when (element.type) {
                PathElementType.Property -> {
                    try {
                        obj = readInstanceProperty(obj!!, element.name)
                    } catch (e: ClassCastException) {
                        obj = readInstanceMethod(obj!!, element.name)
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
//                    obj = (obj as KCallable<Any>).call(
//                        lastobj1!!,
//                        *(
//                            if (element.doEval)
//                                splitToplevelExprs(element.name).map{ evalKotlinString(instance!!, it) }
//                            else
//                                element.params
//                        ).toTypedArray()
//                    )
                    obj = (obj as InstanceMethodDispatcher).call(element.params.toTypedArray())
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
