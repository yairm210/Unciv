package com.unciv.scripting.api

import com.unciv.scripting.ScriptingBackend

object ScriptingApiExecutionContext {
    var handlerParameters: Map<String, Any?>? = null
    // Collection that gets replaced with any contextual parameters when running script handlers. E.G. The Unit moved, the city founded, the tech researched, the construction finished.
    var scriptingBackend: ScriptingBackend? = null
}
