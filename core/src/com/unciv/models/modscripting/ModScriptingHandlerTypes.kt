package com.unciv.models.modscripting


// Dependency/feature stack:
//  ModScriptingHandlerTypes —> ModScriptingRunManager —> ModScriptingRegistrationHandler
//  com.unciv.scripting —> com.unciv.models.modscripting
// Later namespaces in each stack may use members and features from earlier namespaces, but earlier namespaces should have behaviour that is completely independent of any later items.
// The scripting execution model (com.unciv.scripting) only *runs* scripts— Anything to do with loading them should go in here (com.unciv.models.modscripting) instead.
// Likewise, ModScriptingHandlerTypes is only for defining handlers, ModScriptingRunManager is only for using that during gameplay, and anything to do with parsing mod structures should go into the level of ModScriptingRegistrationHandler.


typealias Params = Map<String, Any?>?
typealias ParamGetter = (Params?) -> Params
// Some handlers may have parameters that should be set universally; Others may use or simply pass on parameters from where they're called.

interface Context {
    val name: String
    val handlers: Collection<Handler>
}
interface Handler {
    val name: String
    val paramGetter: ParamGetter
}

// For defining the types and behaviours of available handlers.
object ModScriptingHandlerTypes {

    private val defaultParamGetter: ParamGetter = { it }

    object All { // Not a great name for imports.
        enum class UncivGame(override val paramGetter: ParamGetter = defaultParamGetter): Handler {
            after_enter(),
            before_exit()
            ;
            companion object: Context {
                override val name = "UncivGame"
                override val handlers = values().toList()
            }
        }

        enum class GameInfo(override val paramGetter: ParamGetter = defaultParamGetter): Handler {
            ;
            companion object: Context {
                override val name = "GameInfo"
                override val handlers = values().toList()
            }
        }
    }
    val all = listOf<Context>( // Explicitly type so anything missing companion raises compile/IDE error.
        All.UncivGame,
        All.GameInfo
    )
    val byContextAndName = all.associate { context ->
        context.name to context.handlers.associateWith { handler ->
            handler.name
        }
    }
}
