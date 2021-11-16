package com.unciv.scripting.utils

import com.unciv.scripting.ScriptingScope
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import com.badlogic.gdx.utils.Json


// Automatically running this should probably be a part of the build process.
// Probably do whatever is done with `TranslationFileWriter`.

@Retention(AnnotationRetention.RUNTIME)
annotation class ExposeScriptingApi
// Eventually use this to whitelist safe API accessible members for security.

data class ApiSpecDef(
    var path: String,
    var isIterable: Boolean,
    var isMapping: Boolean,
    var isCallable: Boolean,
    var callableArgs: List<String?>,
    // These are the basic values that are needed to implement a scripting API.

    var _type: String? = null,
    //_docstring: String? = null,
    var _repeatedReferenceTo: String? = null,
    var _isJsonType: Boolean? = null,
    var _iterableValueType: String? = null,
    var _mappingKeyType: String? = null,
    var _mappingValueType: String? = null,
    var _callableReturnType: String? = null,
    var _callableArgTypes: List<String>? = null
    //var _callableArgDefaults: List<String>
    // These values can be used to enhance behaviour in a scripting API, and may be needed for generating bindings in some languages.
)

fun makeMemberSpecDef(member: KCallable<*>): ApiSpecDef {
    //val kclass = member.returnType.classifier as KClass<*>
    val submembers = mutableSetOf<String>()
    /*for (m in kclass.members) {
        try {
            submembers.add( m.name )
        } catch (e: Exception) {
            println("Error accessing name of property ${m}.")
        }
    }*/
    //val tmp: Collection<String> = kclass.members.filter{ it.name != null }.map{ it.name!! }
    //submembers.addAll(tmp)
    //Using a straight `.map`
    return ApiSpecDef(
        path = member.name,
        isIterable = "iterator" in submembers,
        isMapping = "get" in submembers,
        isCallable = member is KFunction,
        callableArgs = member.parameters.map{ it.name }
    )
}


class ApiSpecGenerator(val scriptingScope: ScriptingScope) {

    fun isUncivClass(cls: KClass<*>): Boolean {
        return cls.qualifiedName!!.startsWith("com.unciv")
    }

    fun getAllUncivClasses(): Set<KClass<*>> {
        val searchclasses = mutableListOf<KClass<*>>(scriptingScope::class)
        val encounteredclasses = mutableSetOf<KClass<*>>()
        var i: Int = 0
        while (i < searchclasses.size) {
            var cls = searchclasses[i]
            for (m in cls.members) {
                var kclass: KClass<*>?
                try {
                    kclass = (m.returnType.classifier as KClass<*>)
                } catch (e: Exception) {
                    println("Skipping property ${m.name} in ${cls.qualifiedName} because of ${e}")
                    continue
                }
                //kclass.members //Directly accessing kclass.members gets a `KotlinInternalReflectionError`, but iterating through `searchclasses` seems to work just fine.
                if (isUncivClass(kclass!!) && kclass!! !in encounteredclasses) {
                    encounteredclasses.add(kclass!!)
                    searchclasses.add(kclass!!)
                }
            }
            i += 1
        }
        return encounteredclasses
    }

    fun generateRootsApi(): List<String> {
        return listOf()
    }

    fun generateFlatApi(): List<String> {
        return scriptingScope::class.members.map{ it.name }
    }

    fun generateClassApi(): Map<String, List<ApiSpecDef>> {
        // Provide options for the scripting languages. This function
        val classes = getAllUncivClasses()
        var c = 0 // Test count. Something like 5,400, IIRC. For now, it's easier to just dynamically generate the API using Python's magic methods and the reflective tools in ScriptingProtocol. JS has proxies too, but other languages may not be so dynamic. // TBF I think some of those might have been GDX/Kotlin/JVM classes, which I should filter oout by `.qualifiedName`.
        val output = mutableMapOf<String, List<ApiSpecDef>>(
            *classes.map{
                it.qualifiedName!! to it.members.map{ c += 1; makeMemberSpecDef(it) }
            }.toTypedArray()
        )
        println("\nGathered ${c} property specifications across ${classes.size} classes.\n")
        return output
    }
}

