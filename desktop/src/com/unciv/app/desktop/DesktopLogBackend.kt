package com.unciv.app.desktop

import com.unciv.utils.DefaultLogBackend

class DesktopLogBackend : DefaultLogBackend() {

    // -ea (enable assertions) or kotlin debugging property as marker for a debug run.
    // Can easily be added to IntelliJ/Android Studio launch configuration template for all launches.
    private val release: Boolean

    init {
        release = !(assertionsEnabled() || anyDebugPropertySet())
    }

    private fun assertionsEnabled()=
        try {
            assert(false)
            false
        } catch (_: AssertionError) {
            true
        }

    private fun anyDebugPropertySet(): Boolean {
        /* in case you need to look at them:
        val properties = System.getProperties()
            .map { entry -> entry.key.toString() to entry.value.toString() }
            .toMap()
        for ((key, value) in properties.toSortedMap()) {
            println("$key=$value")
        }
        */

        val properties = System.getProperties()
            .mapTo(mutableSetOf()) { it.key.toString() }
        val debugProperties = setOf("kotlinx.coroutines.debug", "kotlinx.coroutines.debug.enable.creation.stack.trace", "onlyLog", "noLog")
        return debugProperties.any { it in properties }
    }

    override fun isRelease(): Boolean {
        return release
    }

    override fun getSystemInfo(): String {
        return SystemUtils.getSystemInfo()
    }
}
