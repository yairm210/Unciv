package com.unciv.scripting.api

import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.reflection.makeFunctionDispatcher
import com.unciv.scripting.utils.FakeMap
import com.unciv.scripting.utils.LazyMap
import kotlin.reflect.KCallable
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.jvm.kotlinFunction

// Could also use ClassGraph to automatically find all relevant classes on build.
// https://github.com/classgraph/classgraph/wiki/Build-Time-Scanning
// Honestly, it's fine though. Having scripts provide the qualpaths themselves keeps everything dynamic, and the LazyMap caching keeps it (as) performant (as the rest of the API is, probably). The only real "benefit" of indexing everything beforehand would be enabling autocompletion.

// Convert an Enum type parameter into a Map of its constants by their names.
// inline fun <reified T: Enum<T>> enumToMap() = enumValues<T>().associateBy { it.name }

fun enumQualnameToMap(qualName: String) = Class.forName(qualName).enumConstants.associateBy { (it as Enum<*>).name }
// Always return a built-in Map class instance here, so its gets serialized as JSON object instead of tokenized, and scripts can refer directly to its items.
// I cast to Enum<*> fully expecting it would crash because it felt metaclass-y. But apparently it's just a base class, so it works?


/**
 * For use in ScriptingScope. Allows interpreted scripts access Kotlin/JVM class functionality that isn't attached to any application instances.
 */
object ScriptingApiJvmHelpers {

    private const val exposeStates = true // Probably keep this false?

    val enumMapsByQualname = LazyMap(::enumQualnameToMap)

    val kotlinClassByQualname = LazyMap({ qualName: String -> Class.forName(qualName).kotlin }, exposeState = exposeStates)

    val kotlinSingletonByQualname = LazyMap({ qualName: String -> kotlinClassByQualname[qualName]?.objectInstance }, exposeState = exposeStates)

    val kotlinCompanionByQualClass = LazyMap({ qualName: String -> kotlinClassByQualname[qualName]?.companionObjectInstance }, exposeState = exposeStates)

    val functionByQualClassAndMethodName = LazyMap({ jclassQualname: String -> // TODO: Rename, remove "Method".
        val cls = Class.forName(jclassQualname)
        LazyMap({ methodName: String -> makeFunctionDispatcher(cls.getDeclaredMethods().asSequence().filter { it.name == methodName }.map { it.kotlinFunction }.toList() as List<KCallable<Any?>>) }, exposeState = exposeStates)
        // Could initialize the second LazyMap here by accessing for all names— Only benefit would be for autocomplete, at higher first-call time and memory use, though.
    }, exposeState = exposeStates)
    // apiHelpers.Jvm.functionByQualClassAndMethodName["com.unciv.ui.utils.ExtensionFunctionsKt"]["toLabel"]("Test")

    val staticPropertyByQualClassAndName = LazyMap({ jclassQualname: String ->
        val kcls = Class.forName(jclassQualname).kotlin
        LazyMap({ name: String -> Reflection.readClassProperty(kcls, name) as Any? }, exposeState = exposeStates)
    }, exposeState = exposeStates)
    // apiHelpers.Jvm.kotlinClassByQualname["com.badlogic.gdx.graphics.Color"].members[50].get()
    // apiHelpers.Jvm.staticPropertyByQualClassAndName["com.badlogic.gdx.graphics.Color"]['WHITE']

    val constructorByQualname = LazyMap({ qualName: String -> makeFunctionDispatcher(Class.forName(qualName).kotlin.constructors) }, exposeState = exposeStates)
    // TODO (Later, Maybe): This would actually be quite easy to whitelist by package paths.

    val kotlinClassByInstance = FakeMap{ obj: Any? -> obj!!::class }

    fun toString(obj: Any?) = obj.toString()

//    fun arrayOf(elements: Collection<Any?>): Array<*> = elements.toTypedArray()
//    fun arrayOfAny(elements: Collection<Any>): Array<Any> = elements.toTypedArray()
//    fun arrayOfString(elements: Collection<String>): Array<String> = elements.toTypedArray()

    //TODO: Heavily overloaded toList or some such converters for Arrays, Sets, Sequences, Iterators, etc.
}

