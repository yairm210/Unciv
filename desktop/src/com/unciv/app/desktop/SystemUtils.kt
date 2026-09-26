package com.unciv.app.desktop

import com.badlogic.gdx.files.FileHandle
import com.unciv.utils.isRunFromJar
import java.nio.charset.Charset

/** Helper for CrashScreen, desktop only */
object SystemUtils {

    /** desktop implementation for [LogBackend.getSystemInfo][com.unciv.utils.LogBackend.getSystemInfo]. */
    fun getSystemInfo() = buildString(256) {
        // Operating system
        val osName = System.getProperty("os.name") ?: "Unknown"
        val isWindows = osName.startsWith("Windows", ignoreCase = true)
        append("OS: ").append(osName)
        if (!isWindows) {
            val arch = System.getProperty("os.arch")
            val ver = System.getProperty("os.version")
            if (arch != null || ver != null) {
                append(" (")
                if (arch != null) append(arch)
                if (arch != null && ver != null) append(", ")
                if (ver != null) append(ver)
                append(')')
            }
        }
        appendLine()

        // Specific release info
        val osRelease = if (isWindows) getWinVer() else getLinuxDistro()
        if (osRelease.isNotEmpty())
            append('\t').appendLine(osRelease)

        // Java runtime version
        val javaVendor = System.getProperty("java.vendor")
        if (javaVendor != null) {
            val specVersion = System.getProperty("java.specification.version") ?: "?"
            val detailVersion = System.getProperty("java.vendor.version")
                ?: System.getProperty("java.vm.version")
                ?: System.getProperty("java.runtime.version")
                ?: "unknown"
            append("Java: ").append(javaVendor).append(' ').append(specVersion).append(" (").append(detailVersion).appendLine(")")
        }

        // Packaging type
        val packageType = try {
            when {
                !isRunFromJar(SystemUtils) -> "source"
                System.getProperty("unciv.packr") != null -> "packr"
                else -> "jar"
            }
        } catch (_: Throwable) { "unknown" }
        append("\tRunning from: ").appendLine(packageType)

        // Java VM memory limit as set by -Xmx
        val maxMemory = try {
            Runtime.getRuntime().maxMemory() / 1024 / 1024
        } catch (_: Throwable) { -1L }
        if (maxMemory > 0)
            append("\tMax Memory: ").append(maxMemory).appendLine(" MB")

        // Encoding used by Java when not explicitly specified/-able (such as atlas loader)
        append("System default encoding: ").appendLine(Charset.defaultCharset().name())
    }

    /** Kludge to get the important Windows version info (no easier way than the registry AFAIK)
     *  using a subprocess running reg query. Other methods would involve nasty reflection
     *  to break java.util.prefs.Preferences out of its Sandbox, or JNA requiring new bindings.
     */
    private fun getWinVer(): String {
        val winVerCommand = arrayOf(
            "cmd", "/c",
            """reg query "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion" /v ProductName && """ +
            """reg query "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion" /v ReleaseId && """ +
            """reg query "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion" /v CurrentBuild && """ +
            """reg query "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion" /v DisplayVersion"""
        )

        val entries: Map<String, String> = try {
            val process = Runtime.getRuntime().exec(winVerCommand)
            process.waitFor()
            val output = process.inputStream.readAllBytes().toString(Charset.defaultCharset())

            val goodLines = output.split('\n').mapNotNull {
                it.removeSuffix("\r").run {
                    if (startsWith("    ") || startsWith("\t")) trim() else null
                }
            }

            goodLines.map { it.split("REG_SZ") }
                .filter { it.size == 2 }
                .associate { it[0].trim() to it[1].trim() }
        } catch (_: Throwable) { return "" }

        if ("ProductName" !in entries) return ""

        return entries["ProductName"]!! +
                ((entries["DisplayVersion"] ?: entries["ReleaseId"])?.run { " Version $this" } ?: "") +
                (entries["CurrentBuild"]?.run { " (Build $this)" } ?: "")
    }

    /** Get linux Distribution out of the /etc/os-release file (ini-style)
     *  Should be safely silent on systems not supporting that file.
     */
    private fun getLinuxDistro(): String {
        val osRelease: Map<String,String> = try {
            FileHandle("/etc/os-release")
                .readString()
                .split('\n')
                .map { it.split('=') }
                .filter { it.size == 2 }
                .associate { it[0] to it[1].removeSuffix("\"").removePrefix("\"") }
        } catch (_: Throwable) { return "" }
        if ("NAME" !in osRelease) return ""
        return osRelease["PRETTY_NAME"] ?: "${osRelease["NAME"]} ${osRelease["VERSION"]}"
    }
}
