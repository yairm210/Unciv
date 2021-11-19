package com.unciv.scripting.reflection

import com.unciv.scripting.utils.TokenizingJson
import kotlin.collections.ArrayList
import kotlin.reflect.KCallable
import kotlin.reflect.KMutableProperty1
//import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
//import kotlin.reflect.KType
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
/*
    class InstanceMethodDispatcher(val instance: Any, val methodName: String) {
        val methods: List<KCallable<Any>> by lazy { instance::class.members.filter{ it.name == methodName }.map{ it as KCallable<Any> } }
        fun call(arguments: Array<Any?>): Any? {
            val matches = methods.filter {
                    val params = it.parameters
                    params.size == arguments.size + 1
                    // Check that the parameters size is same as arguments size plus one for the receiver.
                    && (arguments zip params.slice(1..arguments.size)).all{
                        (arg, kparam) ->
                            // Check that every argument can be cast to the expected type.
                            if (arg == null)
                                kparam.type.isMarkedNullable
                            else
                                kparam.type in arg::class.supertypes
                                // Not totally sure if these KTypes are singleton-like. Hopefully equality-comparison works for containment here even if they aren't?
                    }
                }
            if (matches.size < 1) {
                throw IllegalArgumentException("No matching signatures found for calling ${instance::class?.simpleName}.${methodName} with given arguments: (${arguments.map{if (it == null) "null" else it::class?.simpleName ?: "null"}.joinToString(", ")})")
                //FIXME: A lot of non-null assertions and null checks can probably be replaced with safe calls.
            }
            if (matches.size > 1) {
                //I guess could also allow this, producing ambiguous behaviour and leaving it up to the methods to avoid creating such scenarios. Actually no, that sounds like a terrible idea reading it back.
                //TODO: Should try to choose most specific signatures based on inheritance hierarchy.
                throw IllegalArgumentException("Multiple matching signatures found for calling ${methodName}:\n\t${matches.map{it.toString()}.joinToString("\n\t")}")
            }
            return matches[0]!!.call(
                    instance,
                    *arguments
                )
        }
    }*/

    fun readInstanceMethod(instance: Any, methodName: String): KCallable<Any> {
        val method = instance::class.members
            .first { it.name == methodName } as KCallable<Any>
        return method
    }

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
//        val params: List<@Contextual Any?> = listOf()
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
                    obj = (obj as KCallable<Any>).call(
                        lastobj1!!,
                        *(
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
