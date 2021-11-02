package com.unciv.scripting

import kotlin.collections.ArrayList
import kotlin.reflect.KProperty1
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import java.util.*


@Suppress("UNCHECKED_CAST")
fun <R> readInstanceProperty(instance: Any, propertyName: String): R? {
    // From https://stackoverflow.com/a/35539628/12260302
    val property = instance::class.members
        .first { it.name == propertyName } as KProperty1<Any, *>
    return property.get(instance) as R?
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


interface PathElementArg {
    val value: Any
}

data class PathElementArgString(override val value: String): PathElementArg
data class PathElementArgInt(override val value: Int): PathElementArg
data class PathElementArgFloat(override val value: Float): PathElementArg
data class PathElementArgBoolean(override val value: Boolean): PathElementArg


enum class PathElementType() {
    Property(),
    Key(),
    //Index(),
    Call()
}

data class PathElement(
    val type: PathElementType,
    val name: String,
    //val args: Collection<PathElementArg>,
    //For IPC with an actual interpreter, it should be possible to pass JSON arrays of basic types instead of just parsing the string.
    //Mostly I'm not sure how and where to cleanly determine whether to use the args field, or parse the string field.
    val doEval: Boolean = false
)


private val brackettypes: Map<Char, String> = mapOf(
    '[' to "[]",
    '(' to "()"
)

private val bracketmeanings: Map<String, PathElementType> = mapOf(
    "[]" to PathElementType.Key,
    "()" to PathElementType.Call
)

fun parseKotlinPath(text: String): List<PathElement> {
    var path:MutableList<PathElement> = ArrayList<PathElement>()
    var curr_type = PathElementType.Property
    var curr_name = ArrayList<Char>()
    var curr_brackets = ""
    var curr_bracketdepth = 0
    var just_closed_brackets = true
    for (char in text) {
        if (curr_bracketdepth == 0) {
            if (char == '.') {
                if (!just_closed_brackets) {
                    path.add(PathElement(PathElementType.Property, curr_name.joinToString("")))
                }
                curr_name.clear()
                just_closed_brackets = false
                continue
            }
            if (char in brackettypes) {
                if (!just_closed_brackets) {
                    path.add(PathElement(PathElementType.Property, curr_name.joinToString("")))
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
        path.add(PathElement(PathElementType.Property, curr_name.joinToString("")))
        curr_name.clear()
    }
    if (curr_bracketdepth > 0) {
        throw IllegalArgumentException("Unclosed parentheses.")
    }
    return path
}


fun stringifyKotlinPath() {
}


fun resolveInstancePath(instance: Any, path: List<PathElement>): Any? {
    var obj: Any? = instance
    for (element in path) {
        when (element.type) {
            PathElementType.Property -> {
                obj = readInstanceProperty(obj!!, element.name)
            }
            PathElementType.Key -> {
                obj = readInstanceItem(
                    obj!!,
                    if (element.doEval)
                        evalKotlinString(instance!!, element.name)!!
                    else
                        element.name
                )
            }
            PathElementType.Call -> {
                throw UnsupportedOperationException("Calls not implemented.")
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
            throw UnsupportedOperationException("Keys not implemented.")
            leafobj = readInstanceItem(
                leafobj!!,
                if (leafelement.doEval)
                    evalKotlinString(instance, leafelement.name)!!
                else
                    leafelement.name
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

