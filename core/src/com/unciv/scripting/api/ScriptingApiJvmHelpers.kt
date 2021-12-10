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

private const val exposeStates = false

/**
 * For use in ScriptingScope. Allows interpreted scripts access Kotlin/JVM class functionality that isn't attached to any application instances.
 */
object ScriptingApiJvmHelpers {

    val enumMapsByQualname = LazyMap(::enumQualnameToMap)

    val classByQualname = LazyMap({ qualName: String -> Class.forName(qualName).kotlin }, exposeState = exposeStates)

    val singletonByQualname = LazyMap({ qualName: String -> classByQualname[qualName]?.objectInstance }, exposeState = exposeStates)

    val companionByQualClass = LazyMap({ qualName: String -> classByQualname[qualName]?.companionObjectInstance }, exposeState = exposeStates)

    val functionByQualClassAndName = LazyMap({ jclassQualname: String ->
        val cls = Class.forName(jclassQualname)
        LazyMap({ methodName: String -> makeFunctionDispatcher(cls.getDeclaredMethods().asSequence().filter { it.name == methodName }.map { it.kotlinFunction }.filterNotNull().toList() as List<KCallable<Any?>>) }, exposeState = exposeStates)
    }, exposeState = exposeStates)
    // apiHelpers.Jvm.functionByQualClassAndName["com.unciv.ui.utils.ExtensionFunctionsKt"]["toLabel"]("Test")

    // TODO: Right... Extension properties?
    // Class.forName("kotlin.reflect.full.KClasses").getMethods().map{it.name}
    // apiHelpers.Jvm.functionByQualClassAndName["kotlin.reflect.full.KClasses"]["getFunctions"](apiHelpers.Jvm.classByQualname["com.badlogic.gdx.scenes.scene2d.ui.Cell"])
    // Right. .kotlinFunction is null for extension property getters:
    //  Class.forName("kotlin.reflect.full.KClasses").getDeclaredMethods().first{it.name == "getFunctions"}.kotlinFunction

    val staticPropertyByQualClassAndName = LazyMap({ jclassQualname: String ->
        val kcls = Class.forName(jclassQualname).kotlin
        LazyMap({ name: String -> Reflection.readClassProperty(kcls, name) as Any? }, exposeState = exposeStates)
    }, exposeState = exposeStates)
    // apiHelpers.Jvm.classByQualname["com.badlogic.gdx.graphics.Color"].members[50].get()
    // apiHelpers.Jvm.staticPropertyByQualClassAndName["com.badlogic.gdx.graphics.Color"]['WHITE']

    val constructorByQualname = LazyMap({ qualName: String -> makeFunctionDispatcher(Class.forName(qualName).kotlin.constructors) }, exposeState = exposeStates)
    // TODO (Later, Maybe): This would actually be quite easy to whitelist by package paths.

    val classByInstance = FakeMap{ obj: Any? -> obj!!::class }

    fun toString(obj: Any?) = obj.toString()

    fun arrayOfAny(elements: Collection<Any?>): Array<*> = elements.toTypedArray() // Rename to toArray? Hm. Named for role, not for semantics— This seems more useful for making new arrays, whereas the toString, toList, etc, are for converting existing instances.
    fun arrayOfTyped(elements: Collection<Any?>): Array<*> = when (val item = elements.firstOrNull()) {
        // For scripting API/reflection. Return type won't be known in IDE, but that's fine as it's erased at runtime anyway. Important thing is that the compiler uses the right functions, creating the right typed arrays at run time.
        is String -> (elements as Collection<String>).toTypedArray()
        is Number -> (elements as Collection<Number>).toTypedArray()
        else -> throw IllegalArgumentException("${item!!::class.qualifiedName}")
    }

    fun arrayOfTyped1(item: Any?) = arrayOfTyped(listOf(item)) // The "Pathcode" DSL doesn't have any syntax for array or collection literals, and adding such would be beyond its scope. So these helper functions let small arrays be used (1) in the reflective scripting backend and (2), more importantly, in the programm-y and more speed-focused helper functions in ScriptingApiMappers and ModApiHelpers.
    fun arrayOfTyped2(item1: Any?, item2: Any?) = arrayOfTyped(listOf(item1, item2))
    fun arrayOfTyped3(item1: Any?, item2: Any?, item3: Any?) = arrayOfTyped(listOf(item1, item2, item3))
    fun arrayOfTyped4(item1: Any?, item2: Any?, item3: Any?, item4: Any?) = arrayOfTyped(listOf(item1, item2, item3, item4))
    fun arrayOfTyped5(item1: Any?, item2: Any?, item3: Any?, item4: Any?, item5: Any?) = arrayOfTyped(listOf(item1, item2, item3, item4, item5))

    fun toList(array: Array<*>) = array.toList() // sorted([real(m.getName()) for m in apiHelpers.Jvm.classByQualname["kotlin.collections.ArraysKt"].jClass.getMethods()])
    fun toList(iterable: Iterable<*>) = iterable.toList()
    fun toList(sequence: Sequence<*>) = sequence.toList()

    //fun toChar(string: CharSequence)
}
