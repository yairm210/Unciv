package com.unciv.app.desktop

import com.unciv.utils.DefaultLogBackend
import java.lang.management.ManagementFactory

class DesktopLogBackend : DefaultLogBackend() {

    // -ea (enable assertions) or kotlin debugging property as marker for a debug run.
    // Can easily be added to IntelliJ/Android Studio launch configuration template for all launches.
    private val release = System.getProperty("kotlinx.coroutines.debug") == null

    override fun isRelease(): Boolean {
        return release
    }

    override fun getSystemInfo(): String {
        return SystemUtils.getSystemInfo()
    }
}
