package com.unciv.models.modscripting

object ModScriptingDebugParameters { // Enum-ifying this could let tests iterate through them exhaustively.
    // Whether to print when script handlers are triggered.
    var printHandlerRun = true // Const val? Faster. But I like the idea of the scripting API itself being able to change these. // Bah. Premature optimization. There are far slower places to worry about speed.
    //
    var printHandlerRegister = false
    var printModRead = false
    var printModRegister = false
    var typeCheckHandlerParamsAtRuntime = true
}
