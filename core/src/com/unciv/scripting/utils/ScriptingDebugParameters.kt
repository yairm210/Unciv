package com.unciv.scripting.utils

object ScriptingDebugParameters {
    // Whether to print out all/most IPC packets for debug.
    var printPacketsForDebug = false
    // Whether to print out all executed script code strings for debug (more readable than printing all packets).
    var printCommandsForDebug = false
    // Whether to print out all/most IPC actions for debug (more readable than printing all packets).
    var printAccessForDebug = false
    // Whether to print out major token count changes and cleaning events in InstanceTokenizer for debug.
    var printTokenizerMilestones = false
    // Whether to print out a warning when reflectively accessing definitions that have been deprecated.
    var printReflectiveDeprecationWarnings = false // TODO
    // Whether to print out when creating and deleting temporary
    var printEnvironmentFolderCreation = false
    // Whether to print out when the lock for stopping simultaneous script run attempts is acquired and released.
    var printLockAcquisition = false
    // Whether to print out when the queue for running mod-controlled scripts in sequence is expanded or consumed.
    var printThreadingStatus =false
    // TODO: Add to gameIsNotRunWithDebugModes unit tests.
}

