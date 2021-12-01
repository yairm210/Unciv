package com.unciv.scripting.api

import com.unciv.scripting.reflection.FunctionDispatcher
import com.unciv.scripting.reflection.makeFunctionDispatcher
import kotlin.reflect.full.primaryConstructor


// TODO (Later): Use ClassGraph to automatically find all relevant classes on build.
// https://github.com/classgraph/classgraph/wiki/Build-Time-Scanning


/**
 * For use in ScriptingScope. Allows interpreted scripts to make new instances of Kotlin/JVM classes.
 */
object ScriptingApiFactories {
    //This, and possible ApiHelpers itself, need better nested namespaces.
    val Game = object {
    }
    val Math = object {
    }
    val Rulesets = object {
    }
    val Kotlin = object {
    }
    val Gui = object {
    }

//    fun kotlinClassFromQualname(qualName: String) = memoizedQualnameToKclass(qualName) // For debug. Probably comment out in builds. // TODO: Check is disabled in unit tests. Actually nah. May as well leave this around.

    val kotlinClassByQualname= LazyMap({ qualName: String -> Class.forName(qualName).kotlin }, exposeState = true) // Mostly for debug. Generally scripts shouldn't be using this.

    val constructorByQualname = LazyMap({ qualName: String -> makeFunctionDispatcher(Class.forName(qualName).kotlin.constructors)
//        val cls = Class.forName(qualName).kotlin
//        val cons = cls.primaryConstructor
//        if (false && cons != null)
//            makeFunctionDispatcher(listOf(cons::call))
//            // Seems that empty constructors take vararg arrays? E.G. MapEditorScreen, MainMenuScreen
//            // Actually, it seems like .primaryConstructor is a lie.
//        else makeFunctionDispatcher(cls.constructors)
    }, exposeState = true)

//    fun instanceFromQualname(qualName: String, args: List<Any?>): Any? {
//        // TODO: Deprecate.
//        val cls = Class.forName(qualName).kotlin
//        val cons = cls.primaryConstructor
//        return if (cons != null)
//            cons.call(*args.toTypedArray())
//        else FunctionDispatcher(
//            functions = cls.constructors,
//            matchNumbersLeniently = true,
//            matchClassesQualnames = false,
//            resolveAmbiguousSpecificity = true
//        ).call(args.toTypedArray())
//    }
//    // TODO: Use generalized InstanceMethodDispatcher to find right callable in KClass.constructors if no primary constructor.

    // apiHelpers.Factories.instanceFromQualname('java.lang.String', [])
    // apiHelpers.Factories.instanceFromQualname('com.unciv.logic.map.MapUnit', [])
    // apiHelpers.Factories.instanceFromQualname('com.unciv.logic.city.CityStats', [civInfo.cities[0]])
    // apiHelpers.Factories.instanceFromQualname('com.badlogic.gdx.math.Vector2', [1, 2])

    // Refer: https://stackoverflow.com/questions/40672880/creating-a-new-instance-of-a-kclass

    // See https://stackoverflow.com/questions/59936471/kotlin-reflect-package-and-get-all-classes. Build structured map of all classes?
    // https://stackoverflow.com/questions/3845823/getting-list-of-fully-qualified-names-from-a-simple-name
    // https://stackoverflow.com/questions/52573605/kotlin-can-i-get-all-objects-that-implements-specific-interface

    // The JAR for Reflections is only a couple hundred KB. It also doesn't work on Android.

    // apiHelpers.registeredInstances['x'] = apiHelpers.Factories.test2('com.badlogic.gdx.math.Vector2')
    // apiHelpers.registeredInstances['x'].constructors[1].call(apiHelpers.Factories.arrayOf([1, 2]))
    // apiHelpers.registeredInstances['y'] = apiHelpers.Factories.test('com.badlogic.gdx.math.Vector2')
    fun arrayOf(elements: Collection<Any?>): Array<*> = elements.toTypedArray()
    fun arrayOfAny(elements: Collection<Any>): Array<Any> = elements.toTypedArray()
    fun arrayOfString(elements: Collection<String>): Array<String> = elements.toTypedArray()
}

