package com.unciv.models.modscripting

import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingBackendType

// For organizing and associating script handlerTypes with specific mods.
// Uses ModScriptingRunManager and ModScriptingHandlerTypes.
object ModScriptingRegistrationManager {
    private fun register(mod: ScriptedMod, backend: ScriptingBackend, handlerType: HandlerType, code: String) {

    }

    fun registerMod(mod: ScriptedMod) {
    }

    private fun unregister(registeredHandler: RegisteredHandler) {
    }

    fun unregisterMod(mod: ScriptedMod) {
    }

    @Suppress("EnumEntryName")
    enum class ModScriptingLanguages(val backendType: ScriptingBackendType) {
        pathcode(ScriptingBackendType.Reflective),
        python(ScriptingBackendType.SystemPython),
        javascript(ScriptingBackendType.SystemQuickJS)
    }
    // …Yeah. Keep language awareness only in the mod loader. The actual backend interfaces and classes don't have to understand anything other than "text in, result out". (But then again, I'm already keeping .engine in the companions of the EnvironmentedScriptingBackends, and use those to instantiate their libraries…)

    class ModScriptingBackends(
        var pathcode: ScriptingBackend? = null,
        var python: ScriptingBackend? = null,
        var javascript: ScriptingBackend? = null
    )
}
