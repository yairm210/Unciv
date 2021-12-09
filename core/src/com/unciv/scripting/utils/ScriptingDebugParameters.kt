package com.unciv.scripting.utils

object ScriptingDebugParameters {
    // Whether to print out all/most IPC packets for debug.
    var printPacketsForDebug = false
    // Whether to print out all/most IPC actions for debug (more readable than printing all packets).
    var printAccessForDebug = false
    // Whether to print out major token count changes and cleaning events in InstanceTokenizer for debug.
    var printTokenizerMilestones = false
}
