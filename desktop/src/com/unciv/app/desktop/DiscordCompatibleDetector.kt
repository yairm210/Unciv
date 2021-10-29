package com.unciv.app.desktop

/**
 * Discord compatibility detector helper class
 * @author asda488
 * Based on Raspberry PI helper class by wf
 */
object DiscordCompatibleDetector {
    var debug = false
    /**
     * check if this java vm runs on an incompatible system - that is ARM systems
     *
     * @return true if this is running on ARM
     */
    fun isARM(): Boolean {
        val osArch = osArch()
        return osArch != null && (osArch.contains("armv7") || osArch.contains("armv8") || osArch.contains("armhf") || osArch.contains("aarch64"))
    }

    /**
     * get the operating System architecture
     *
     * @return the os.arch System property or null
     */
    private fun osArch(): String? {
        val os = System.getProperty("os.name")
        if (os.startsWith("Linux")) {
            val osArch = System.getProperty("os.arch")
            return osArch
        }
        return null
    }

}