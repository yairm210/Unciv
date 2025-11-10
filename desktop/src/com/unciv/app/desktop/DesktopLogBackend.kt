package com.unciv.app.desktop

import com.unciv.utils.DefaultLogBackend
import java.lang.System

class DesktopLogBackend : DefaultLogBackend() {

    // -ea (enable assertions) or kotlin debugging property as marker for a debug run.
    // Can easily be added to IntelliJ/Android Studio launch configuration template for all launches.
    private val release =
        System.getProperty("kotlinx.coroutines.debug") == null
            && System.getProperty("kotlinx.coroutines.debug.enable.creation.stack.trace") == null
            && System.getProperty("noLog") == null
            && System.getProperty("onlyLog") == null
            && assertionsDisabled()

    private fun assertionsDisabled() =
        try {
            assert(false)
            true
        } catch (_: AssertionError) {
            false
        }

    override fun isRelease() = release

    override fun getSystemInfo() = SystemUtils.getSystemInfo()
}
