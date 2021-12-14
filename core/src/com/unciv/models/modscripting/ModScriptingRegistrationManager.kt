package com.unciv.models.modscripting

import com.unciv.scripting.ScriptingBackendType

// For organizing and associating script handlerTypes with specific mods.
// Uses ModScriptingRunManager and ModScriptingHandlerTypes.
object ModScriptingRegistrationManager {

    val activeMods = mutableSetOf<ScriptedModRules>()

    init {
        registerMod(
            ScriptedModRules.fromJsonString(
                """
                    {
                        "name": "Test Mod",
                        "language": "pathcode",
                        "handlers": {
                            "ConsoleScreen": {
                                "after_open": {
                                    "background": ["examples", "get apiExecutionContext.handlerParameters", "get apiExecutionContext.scriptingBackend", "faef"]
                                }
                            }
                        }
                    }
                    """
            )
        )
        registerMod(
            ScriptedModRules.fromJsonString(
                """
                    {
                        "name": "Test Mod2",
                        "language": "python",
                        "handlers": {
                            "ConsoleScreen": {
                                "before_close": {
                                    "background": ["from unciv_scripting_examples.MapEditingMacros import *; (makeMandelbrot() if unciv.apiHelpers.isInGame else None)"],
                                    "foreground": ["from unciv_scripting_examples.EventPopup import *; (showEventPopup(**EVENT_POPUP_DEMOARGS()) if apiHelpers.isInGame else None)"]
                                }
                            }
                        }
                    }
                    """
            )
        )
    }

    fun registerMod(modRules: ScriptedModRules) {
        if (ModScriptingDebugParameters.printModRegister) {
            println("Registering ${modRules.handlersByType.values.map { it.background.size + it.foreground.size }.sum() } script handlers for mod ${modRules.name}.")
        }
        if (modRules in activeMods) {
            throw IllegalArgumentException("Mod ${modRules.name} is already registered.")
        }
        val backend = modRules.backend
        if (backend != null) {
            backend.apply {
                userTerminable = false
                displayNote = modRules.name
            }
            activeMods.add(modRules)
            for ((handlerType, modHandlers) in modRules.handlersByType) {
                for (command in modHandlers.background) {
                    ModScriptingRunManager.registerHandler(
                        handlerType,
                        RegisteredHandler(
                            backend,
                            command,
                            modRules,
                            mainThread = false
                        )
                    )
                }
                for (command in modHandlers.foreground) {
                    ModScriptingRunManager.registerHandler(
                        handlerType,
                        RegisteredHandler(
                            backend,
                            command,
                            modRules,
                            mainThread = true
                        )
                    )
                }
            }
        }
    }


    fun unregisterMod(modRules: ScriptedModRules) {
    }
    // …Yeah. Keep language awareness only in the mod loader. The actual backend interfaces and classes don't have to understand anything other than "text in, result out". (But then again, I'm already keeping .engine in the companions of the EnvironmentedScriptingBackends, and use those to instantiate their libraries…)
}

@Suppress("EnumEntryName")
enum class ModScriptingLanguage(val backendType: ScriptingBackendType) {
    pathcode(ScriptingBackendType.Reflective),
    python(ScriptingBackendType.SystemPython),
    javascript(ScriptingBackendType.SystemQuickJS)
}
