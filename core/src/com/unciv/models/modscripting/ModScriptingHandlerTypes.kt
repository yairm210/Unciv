@file:Suppress("RemoveExplicitTypeArguments")

package com.unciv.models.modscripting

import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
//import kotlin.reflect.full.isSubtypeOf
//import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf


// Dependency/feature stack:
//  ModScriptingHandlerTypes —> ModScriptingRunManager —> ModScriptingRegistrationHandler
//  com.unciv.scripting —> com.unciv.models.modscripting
// Later namespaces in each stack may use members and features from earlier namespaces, but earlier namespaces should have behaviour that is completely independent of any later items.
// The scripting execution model (com.unciv.scripting) only *runs* scripts— Anything to do with loading them should go in here (com.unciv.models.modscripting) instead.
// Likewise, ModScriptingHandlerTypes is only for defining handlerTypes, ModScriptingRunManager is only for using that during gameplay, and anything to do with parsing mod structures should go into the level of ModScriptingRegistrationHandler.


typealias Params = Map<String, Any?>?
typealias ParamGetter = (Params?) -> Params
// Some handlerTypes may have parameters that should be set universally; Others may use or simply pass on parameters from where they're called.

interface HandlerContext {
    val name: String
    val handlerTypes: Collection<HandlerType>
}

interface HandlerType {
    val name: String
    val paramTypes: Map<String, KType>?
    val paramGetter: ParamGetter
    fun checkParamsValid(checkParams: Params): Boolean { // If this becomes a major performance sink, it could be disabled in release builds. But
        if (paramTypes == null || checkParams == null) {
            return checkParams == paramTypes
        }
        if (paramTypes!!.keys != checkParams.keys) {
            return false
        }
        return checkParams.all { (k, v) ->
            if (v == null) {
                paramTypes!![k]!!.isMarkedNullable
            } else {
                //v::class.starProjectedType.isSubtypeOf(paramTypes!![k]!!)
                // In FunctionDispatcher I compare the .jvmErasure KClass instead of the erased type.
                // Right. Erased/star-projected types probably aren't subclasses of the argumented types from typeOf(). Could implement custom typeOfInstance that looks for most specific common element in allSuperTypes for collection contents, but sounds excessive and expensive.
                v::class.isSubclassOf(paramTypes!![k]!!.jvmErasure)
            }
        }
    }
}

// For defining the types and behaviours of available handlerTypes.
object ModScriptingHandlerTypes {

    private val defaultParamGetter: ParamGetter = { it }

//    @Suppress("EnumEntryName") // Handler names are not Kotlin code, but more like JSON keys in mod configuration files.
    @Suppress("EnumEntryName")
    @OptIn(ExperimentalStdlibApi::class) // For typeOf(). Technically isn't needed for functional release builds (see HandlerType.checkParamsValid).
    object All { // Not a great name for imports.
        enum class UncivGame(override val paramGetter: ParamGetter = defaultParamGetter): HandlerType {
            after_enter() {
                override val paramTypes = mapOf<String, KType>(
                    "testArg" to typeOf<Any?>()
                )
            }
            ;
            companion object: HandlerContext {
                override val name = "UncivGame"
                override val handlerTypes = values().toList()
            }
        }

        enum class GameInfo(override val paramGetter: ParamGetter = defaultParamGetter): HandlerType {
            ;
            companion object: HandlerContext {
                override val name = "GameInfo"
                override val handlerTypes = values().toList()
            }
        }
    }
    val all = listOf<HandlerContext>( // Explicitly type so anything with wrong or missing companion raises compile/IDE error.
        All.UncivGame,
        All.GameInfo
    )
    val byContextAndName = all.associate { context ->
        context.name to context.handlerTypes.associateWith { handler ->
            handler.name
        }
    }
}
