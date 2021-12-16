package com.unciv.ui.utils

import kotlin.concurrent.thread

/** Wrapped version of [kotlin.concurrent.thread], that brings the main game loop to a [com.unciv.CrashScreen] if an exception happens. */
fun crashHandlingThread(
    start: Boolean = true,
    isDaemon: Boolean = false,
    contextClassLoader: ClassLoader? = null,
    name: String? = null,
    priority: Int = -1,
    block: () -> Unit
) = thread(
        start = start,
        isDaemon = isDaemon,
        contextClassLoader = contextClassLoader,
        name = name,
        priority = priority,
        block = block.wrapCrashHandlingUnit(true)
    )

