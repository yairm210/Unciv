package com.unciv.app.desktop

import com.badlogic.gdx.files.FileHandle
import com.unciv.ui.utils.CrashReportSysInfo

class CrashReportSysInfoDesktop : CrashReportSysInfo {

    override fun getInfo(): String {
        val builder = StringBuilder()

        // Operating system
        builder.append("OS: " + (System.getProperty("os.name") ?: "Unknown"))
        val osInfo = listOfNotNull(System.getProperty("os.arch"), System.getProperty("os.version")).joinToString()
        if (osInfo.isNotEmpty()) builder.append(" ($osInfo)")
        builder.appendLine()

        // Linux distro
        val osRelease: Map<String,String> = try {
            FileHandle("/etc/os-release")
                .readString()
                .split('\n')
                .map { it.split('=') }
                .filter { it.size == 2 }
                .associate { it[0] to it[1].removeSuffix("\"").removePrefix("\"") }
        } catch (ex: Throwable) { mapOf() }
        if ("NAME" in osRelease) {
            builder.append('\t')
            builder.appendLine(
                osRelease["PRETTY_NAME"] ?: "${osRelease["NAME"]} ${osRelease["VERSION"]}"
            )
        }

        // Java runtime version
        val javaVendor: String? = System.getProperty("java.vendor")
        if (javaVendor != null) {
            val javaVersion: String = System.getProperty("java.vendor.version") ?: System.getProperty("java.vm.version") ?: ""
            builder.appendLine("Java: $javaVendor $javaVersion")
        }

        // Java VM memory limit as set by -Xmx
        val maxMemory = try {
            Runtime.getRuntime().maxMemory() / 1024 / 1024
        } catch (ex: Throwable) { -1L }
        if (maxMemory > 0) {
            builder.append('\t')
            builder.appendLine("Max Memory: $maxMemory MB")
        }

        return builder.toString()
    }
}
