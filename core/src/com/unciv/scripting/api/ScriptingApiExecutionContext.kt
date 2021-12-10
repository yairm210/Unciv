package com.unciv.scripting.api

import com.unciv.scripting.ScriptingBackend

class ScriptingApiExecutionContext {
    var handlerParameters: Map<String, Any?>? = null
    // Why not just use a map? String keys will be clearer in scripts than integers anyway.
    // Collection that gets replaced with any contextual parameters when running script handlers. E.G. Unit moved, city founded, tech researched, construction finished.
    var scriptingBackend: ScriptingBackend? = null // TODO: Currently executing backend.
}
