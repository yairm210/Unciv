package com.unciv.models.modscripting

import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingBackendType

// For organizing and associating script handlers with specific mods.
// Uses ModScriptingHandlerTypes and ModScriptingRunManager.
object ModScriptingRegistrationManager {
    private fun register(mod: ScriptedMod, backend: ScriptingBackend, handler: Handler, code: String) {

    }

    fun registerMod(mod: ScriptedMod) {
    }

    private fun unregister(registeredHandler: RegisteredHandler) {
    }

    fun unregisterMod(mod: ScriptedMod) {
    }

    val languagesToBackends = mapOf(
        "py3" to ScriptingBackendType.SystemPython,
        "js" to ScriptingBackendType.SystemQuickJS
    ) // …Yeah. Keep language awareness only in the mod loader. The actual backend interfaces and classes don't have to understand anything other than "text in, result out". (But then again, I'm already keeping .engine in the companions of the EnvironmentedScriptingBackends, and use those to instantiate their libraries…)

    class ModScriptingBackends(
        var py3: ScriptingBackend? = null,
        var js: ScriptingBackend? = null
    )
}
