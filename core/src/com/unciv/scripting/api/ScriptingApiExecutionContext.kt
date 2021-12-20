package com.unciv.scripting.api

import com.unciv.scripting.ScriptingBackend

object ScriptingApiExecutionContext {
    // Map that gets replaced with any contextual parameters when running script handlers.

    // E.G. The Unit that was moved, the city that was founded, the tech that was researched, the construction that was finished, the instance that was initialized.

    // To prevent scripts from interfering with each other by trying to mutate this, it is reset by ScriptingState before every script execution. As such, scripted handlers should re-access it when run to get valid values.
    var handlerParameters: Map<String, Any?>? = null

    var scriptingBackend: ScriptingBackend? = null // TODO: Actually just use ScriptingState.activeBackend? …Execution state shouldn't be kept completely under ScriptingScope at all now that it's all singletons.
}
