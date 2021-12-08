package com.unciv.scripting.utils

object ScriptingLock {
    private var isRunning = false
    // TODO: Prevent UI collision, and also prevent recursive script executions.
}
