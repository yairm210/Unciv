package com.unciv.scripting.api

import com.unciv.scripting.reflection.FunctionDispatcher
import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.reflection.makeFunctionDispatcher
import kotlin.reflect.KCallable
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.kotlinFunction


//// TODO (Later, maybe.): Use ClassGraph to automatically find all relevant classes on build.
// https://github.com/classgraph/classgraph/wiki/Build-Time-Scanning
// Honestly, it's fine. Having scripts provide the qualpaths themselves keeps everything dynamic, and the LazyMap caching keeps it (as) performant (as the rest of the API is, probably), so the only real "benefit" of indexing everything beforehand would be enabling autocompletion.


// TODO: Rename this, to "Jvm", maybe.

/**
 * For use in ScriptingScope. Allows interpreted scripts to make new instances of Kotlin/JVM classes.
 */
object ScriptingApiFactories {

    //val javaClassByQualname

    val kotlinClassByQualname = LazyMap({ qualName: String -> Class.forName(qualName).kotlin }, exposeState = true)
    //// Mostly for debug. Scripts may, but probably usually shouldn't, use this.

    // Actually, this works for singletons too:
    // apiHelpers.Factories.kotlinClassByQualname['com.unciv.scripting.reflection.Reflection'].objectInstance

    // Works for files: apiHelpers.Factories.kotlinClassByQualname['com.unciv.scripting.ScriptingStateKt']. Kinda. (Top-level functions?)
    // apiHelpers.instancesAsInstances[apiHelpers.Factories.kotlinClassByQualname['com.unciv.ui.utils.ExtensionFunctionsKt'].jClass.getMethods()][0].getName()

    // [m.getName() for m in apiHelpers.instancesAsInstances[apiHelpers.Factories.kotlinClassByQualname['com.unciv.ui.utils.ExtensionFunctionsKt'].jClass.getMethods()] ]
    // m=next(m for m in apiHelpers.instancesAsInstances[apiHelpers.Factories.kotlinClassByQualname['com.unciv.ui.utils.ExtensionFunctionsKt'].jClass.getMethods()] if m.getName() == 'enable')

    //val companionByQualName TODO?
    // Fails: apiHelpers.Factories.kotlinClassByQualname["com.unciv.scripting.SpyScriptingBackend.Metadata"]

//    val toplevelFunctionByQualname = LazyMap ({ qualName: String ->
//            val simpleName = qualName.substringAfterLast('.')
//            makeFunctionDispatcher(Class.forName("${qualName.substringBeforeLast('.')}Kt").getDeclaredMethods().asSequence().filter { it.name == simpleName }.map { it.kotlinFunction }.toList() as List<KCallable<Any?>>)
//            // I think I read somewhere at some point that the "${Filename}Kt" name for files is documented, but I couldn't find it again.
//        }, exposeState = true)
//    // TODO: exposeState should probably be unset in all these?
//    // apiHelpers.Factories.toplevelFunctionByQualname["com.unciv.ui.utils.ExtensionFunctions.onClick"]
//    // apiHelpers.Factories.toplevelFunctionByQualname["com.unciv.ui.utils.ExtensionFunctions.toLabel"]("Test")
//    // This *is* rather convenient, but maybe it should be unboundMethodsByQualname or something like that instead? "Top-level function" and "Java method" seem quite far intuitively in Kotlin semantics, but the code is only two string characters apart (and they're apparently the same thing on the JVM).

    val functionByQualClassAndMethodName = LazyMap ({ jclassQualname: String ->
        val cls = Class.forName(jclassQualname)
        LazyMap({ methodName: String -> makeFunctionDispatcher(cls.getDeclaredMethods().asSequence().filter { it.name == methodName }.map { it.kotlinFunction }.toList() as List<KCallable<Any?>>) }, exposeState = true)
        // Could initialize the second LazyMap here by accessing for all names— Only benefit would be for autocomplete, at higher first-call time and memory use, though.
    }, exposeState = true)
    // TODO: exposeState should probably be unset in all these?
    // apiHelpers.Factories.functionByQualClassAndMethodName["com.unciv.ui.utils.ExtensionFunctionsKt"]["toLabel"]("Test")

    val staticPropertyByQualClassAndName = LazyMap ({ jclassQualname: String ->
        val kcls = Class.forName(jclassQualname).kotlin
        LazyMap({ name: String -> Reflection.readClassProperty(kcls, name) as Any? }, exposeState = true)
    }, exposeState = true)
    // apiHelpers.Factories.kotlinClassByQualname["com.badlogic.gdx.graphics.Color"].members[50].get()
    // apiHelpers.Factories.staticPropertyByQualClassAndName["com.badlogic.gdx.graphics.Color"]['WHITE']

    val constructorByQualname = LazyMap({ qualName: String -> makeFunctionDispatcher(Class.forName(qualName).kotlin.constructors) }, exposeState = true)
    // TODO (Later, Maybe): This would actually be quite easy to whitelist by package paths.

    fun arrayOf(elements: Collection<Any?>): Array<*> = elements.toTypedArray()
    fun arrayOfAny(elements: Collection<Any>): Array<Any> = elements.toTypedArray()
    fun arrayOfString(elements: Collection<String>): Array<String> = elements.toTypedArray()
}

