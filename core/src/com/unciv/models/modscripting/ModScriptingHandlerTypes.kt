@file:Suppress("RemoveExplicitTypeArguments")

package com.unciv.models.modscripting

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.consolescreen.ConsoleScreen
import com.unciv.ui.utils.stringifyException
import kotlin.reflect.KType
//import kotlin.reflect.full.isSubtypeOf
//import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

// Dependency/feature stack:
//  ModScriptingHandlerTypes —> ModScriptingRunManager —> ModScriptingRegistrationHandler
//  com.unciv.scripting —> com.unciv.models.modscripting
// Later namespaces in each stack may use members and features from earlier namespaces, but earlier namespaces should have behaviour that is completely independent of any later items.
// The scripting execution model (com.unciv.scripting) only *runs* scripts— Anything to do with loading them should go in here (com.unciv.models.modscripting) instead.
// Likewise, ModScriptingHandlerTypes is only for defining handlerTypes, ModScriptingRunManager is only for using that during gameplay, and anything to do with parsing mod structures should go into the level of ModScriptingRegistrationHandler.

val HANDLER_DEFINITIONS = handlerDefinitions {
    handlerContext(ContextId.UncivGame) {
        addInstantiationHandlers<UncivGame>()
    }
    handlerContext(ContextId.GameInfo) {
        handler<Pair<GameInfo, CivilizationInfo?>>(HandlerId.after_instantiate){
            param<GameInfo>("gameInfo") { it.first }
            param<CivilizationInfo?>("civInfo") { it.second }
        }
        handler<Pair<GameInfo, CivilizationInfo?>>(HandlerId.before_discard) {
            param<GameInfo>("gameInfo") { it.first }
            param<CivilizationInfo?>("civInfo") { it.second }
        }
        addSingleParamHandlers<GameInfo>(
            HandlerId.after_open,
            HandlerId.before_close
        )
    }
    handlerContext(ContextId.ConsoleScreen) {
        addInstantiationHandlers<ConsoleScreen>()
        addSingleParamHandlers<ConsoleScreen>(
            HandlerId.after_open,
            HandlerId.before_close
        )
    }
}

private inline fun <reified V> HandlerDefinitionsBuilderScope.HandlerContextBuilderScope.addSingleParamHandlers(vararg handlerTypes: HandlerId, paramName: String? = null) {
    for (handlerType in handlerTypes) {
        handler<V>(handlerType) {
            param<V>(paramName ?: V::class.simpleName!!.replaceFirstChar { it.lowercase()[0] })
        }
    }
}

private inline fun <reified V> HandlerDefinitionsBuilderScope.HandlerContextBuilderScope.addInstantiationHandlers(paramName: String? = null) {
    addSingleParamHandlers<V>(HandlerId.after_instantiate, HandlerId.before_discard, paramName = paramName)
}

val ALL_HANDLER_TYPES = HANDLER_DEFINITIONS.handlerContexts.values.asSequence()
    .map { it.handlerTypes.values }
    .flatten().toSet()


// Enum of identifiers for a scripting handler context namespace.
enum class ContextId { // These are mostly so autocompletion, typodetection, usage search, etc will work.
    UncivGame,
    MainMenuScreen,
    PausePopup,
    GameInfo,
    WorldScreen,
    DiplomacyScreen,
    PolicyPickerScreen,
    TradeScreen,
    MapEditor,
    ConsoleScreen,
}

// Enum of identifiers for names
@Suppress("EnumEntryName", "SpellCheckingInspection")
enum class HandlerId {
    // After creation of a new object with the same name as the context.
    after_instantiate,
    // Before destruction of each object with the same name as the context. Not guaranteed to be run.
    before_discard,
    // After data represented by object is exposed as context to player, including if such happens multiple times.
    after_open,
    // After data represented by object is hidden replaced as context to player, including if such happens multiple times.
    before_close,
    before_gamesave,
    after_turnautostart,
    after_unitmove,
    after_cityconstruction,
    after_cityfound,
    after_techfinished,
    after_policyadopted,
    before_turnend,
    after_tileclicked,
    after_modload,
    before_modunload,
}


typealias Params = Map<String, Any?>?
typealias ParamGetter = (Any?) -> Params
// Some handlerTypes may have parameters that should be set universally; Others may use or simply pass on parameters from where they're called.

interface HandlerDefinitions {
    val handlerContexts: Map<ContextId, HandlerContext>
    fun get(key: ContextId): HandlerContext {
        val handlerContext = handlerContexts[key]
        if (handlerContext == null) {
            val exc = NullPointerException("Unknown HandlerContext $key!")
            println(exc.stringifyException())
            throw exc
        }
        return handlerContext
    }
    operator fun get(contextKey: ContextId, handlerKey: HandlerId) = get(contextKey).get(handlerKey)
}

interface HandlerContext {
    val name: ContextId
    val handlerTypes: Map<HandlerId, HandlerType>
    fun get(key: HandlerId): HandlerType {
        val handlerType = handlerTypes[key]
        if (handlerType == null) {
            val exc = NullPointerException("Unknown HandlerType $key for $name context!")
            println(exc.stringifyException())
            throw exc
        }
        return handlerType
    }
}

interface HandlerType {
    val name: HandlerId
    val paramTypes: Map<String, KType>?
    val paramGetter: ParamGetter
//    fun checkParamsValid(checkParams: Params): Boolean { // If this becomes a major performance sink, it could be disabled in release builds. But
//        if (paramTypes == null || checkParams == null) {
//            return checkParams == paramTypes
//        }
//        if (paramTypes!!.keys != checkParams.keys) {
//            return false
//        }
//        return checkParams.all { (k, v) ->
//            if (v == null) {
//                paramTypes!![k]!!.isMarkedNullable
//            } else {
//                //v::class.starProjectedType.isSubtypeOf(paramTypes!![k]!!)
//                // In FunctionDispatcher I compare the .jvmErasure KClass instead of the erased type.
//                // Right. Erased/star-projected types probably aren't subclasses of the argumented types from typeOf(). Could implement custom typeOfInstance that looks for most specific common element in allSuperTypes for collection contents, but sounds excessive and expensive.
//                v::class.isSubclassOf(paramTypes!![k]!!.jvmErasure)
//            }
//        }
//    }
}


@DslMarker
private annotation class HandlerBuilder

// Type-safe builder scope for the root hierarchy of all handler context and handler type definitions.
@HandlerBuilder
private class HandlerDefinitionsBuilderScope {

    val handlerContexts = mutableMapOf<ContextId, HandlerContext>()

    // Type-safe builder scope for a handler context namespace.

    // @param name Name of the context.
    @HandlerBuilder
    class HandlerContextBuilderScope(val name: ContextId) {

        val handlers = mutableMapOf<HandlerId, HandlerType>()

        // Type-safe builder scope for a handler type.

        // @param V The type that may be provided as an argument when running this handler type.
        // @param name Name of the handler type.
        @HandlerBuilder
        class HandlerTypeBuilderScope<V>(val name: HandlerId) {

            val paramTypes = mutableMapOf<String, KType>()
            val paramGetters = mutableMapOf<String, (V) -> Any?>()

            // Type-safe builder method for a parameter that a handler type accepts and then sets in a Map accessible by the scripting API while the handler type is running.

            // @param R The type that this value is to be set to in the script's execution context.
            // @param name The key of this parameter in the scripting-accessible Map.
            // @param getter A function that returns the value of this parameter in the scripting-accessible Map, when given the argument passed to this handler type.
            @OptIn(ExperimentalStdlibApi::class)
            inline fun <reified R> param(name: String, noinline getter: (V) -> R = { it as R }) {
                paramTypes[name] = typeOf<R>()
                paramGetters[name] = getter
            }

        }

        // Type-safe builder method for a handler type.

        // @param V The type that may be provided as an argument when running this handler type.
        // @param name The name of this handler type.
        fun <V> handler(name: HandlerId, init: HandlerTypeBuilderScope<V>.() -> Unit) {
            val unconfigured = HandlerTypeBuilderScope<V>(name)
            unconfigured.init()
            handlers[name] = object: HandlerType {
                override val name = name
                val context = this@HandlerContextBuilderScope
                override val paramTypes = unconfigured.paramTypes.toMap()
                override val paramGetter: ParamGetter = { given: Any? -> unconfigured.paramGetters.entries.associate { it.key to it.value(given as V) } }
                override fun toString() = "HandlerType:${context.name}/${name}"
            }
        }

    }

    // Type-safe builder method for a handler context namespace.

    // @param name The name of this context.
    fun handlerContext(name: ContextId, init: HandlerContextBuilderScope.() -> Unit) {
        val unconfigured = HandlerContextBuilderScope(name)
        unconfigured.init()
        handlerContexts[name] = object: HandlerContext {
            override val name = unconfigured.name
            override val handlerTypes = unconfigured.handlers.toMap()
        }
    }

}

// Type-safe builder function for the root hierarchy of all handler context and handler type definitions.
private fun handlerDefinitions(init: HandlerDefinitionsBuilderScope.() -> Unit): HandlerDefinitions {
    val unconfigured = HandlerDefinitionsBuilderScope()
    unconfigured.init()
    return object: HandlerDefinitions {
        override val handlerContexts = unconfigured.handlerContexts.toMap()
    }
}


//object ModScripthingHandlerTypes2 {
//}

// For defining the types and behaviours of available handlerTypes.
//object ModScriptingHandlerTypes {
//
//
//
////    private val defaultParamGetter: ParamGetter = { it }
//    private val defaultParamGetter: ParamGetter = { mapOf() }
//
////    @Suppress("EnumEntryName") // Handler names are not Kotlin code, but more like JSON keys in mod configuration files.
//    @Suppress("EnumEntryName")
//    @OptIn(ExperimentalStdlibApi::class) // For typeOf(). Technically isn't needed for functional release builds (see HandlerType.checkParamsValid).
//    object All { // Not a great name for imports.
//        enum class UncivGame(override val paramGetter: ParamGetter = defaultParamGetter): HandlerType {
//            after_instantiate() {
//                override val paramTypes = mapOf<String, KType>(
//                    "testArg" to typeOf<Any?>()
//                )
//            }
//            ;
//            companion object: HandlerContext {
//                override val name = "UncivGame"
//                override val handlerTypes = values().toList()
//            }
//        }
//
//        enum class GameInfo(override val paramGetter: ParamGetter = defaultParamGetter): HandlerType {
//            ;
//            companion object: HandlerContext {
//                override val name = "GameInfo"
//                override val handlerTypes = values().toList()
//            }
//        }
//    }
//    val all = listOf<HandlerContext>( // Explicitly type so anything with wrong or missing companion raises compile/IDE error.
//        All.UncivGame,
//        All.GameInfo
//    )
//    val byContextAndName = all.associate { context ->
//        context.name to context.handlerTypes.associateWith { handler ->
//            handler.name
//        }
//    }
//}
