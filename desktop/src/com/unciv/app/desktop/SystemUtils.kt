package com.unciv.app.desktop

import com.badlogic.gdx.files.FileHandle
import java.nio.charset.Charset

object SystemUtils {

    fun getSystemInfo(): String {
        val builder = StringBuilder()

        // Operating system
        val osName = System.getProperty("os.name") ?: "Unknown"
        val isWindows = osName.startsWith("Windows", ignoreCase = true)
        builder.append("OS: $osName")
        if (!isWindows) {
            val osInfo = listOfNotNull(System.getProperty("os.arch"), System.getProperty("os.version")).joinToString()
            if (osInfo.isNotEmpty()) builder.append(" ($osInfo)")
        }
        builder.appendLine()

        // Specific release info
        val osRelease = if (isWindows) getWinVer() else getLinuxDistro()
        if (osRelease.isNotEmpty())
            builder.appendLine("\t$osRelease")

        // Java runtime version
        val javaVendor: String? = System.getProperty("java.vendor")
        if (javaVendor != null) {
            val javaVersion: String = System.getProperty("java.vendor.version") ?: System.getProperty("java.vm.version") ?: ""
            builder.appendLine("Java: $javaVendor $javaVersion")
        }

        // Java VM memory limit as set by -Xmx
        val maxMemory = try {
            Runtime.getRuntime().maxMemory() / 1024 / 1024
        } catch (_: Throwable) { -1L }
        if (maxMemory > 0) {
            builder.append('\t')
            builder.appendLine("Max Memory: $maxMemory MB")
        }

        // Encoding used by Java when not explicitly specified/-able (such as atlas loader)
        builder.appendLine("System default encoding: " + Charset.defaultCharset().name())

        return builder.toString()
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

        val entries: Map<String,String> = try {
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
        } catch (_: Throwable) { mapOf() }

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
        } catch (_: Throwable) { mapOf() }
        if ("NAME" !in osRelease) return ""
        return osRelease["PRETTY_NAME"] ?: "${osRelease["NAME"]} ${osRelease["VERSION"]}"
    }

}
