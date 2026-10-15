package com.unciv.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.unciv.utils.LogBackend
import com.unciv.utils.Tag
import java.security.MessageDigest

private const val TAG_MAX_LENGTH = 23

/**
 *  Unciv's logger implementation for Android
 *
 *  Will log depending on whether the build was a debug build, with a manual override.
 *  - An APK built from Studio's "Build APK's" menu is still a debug build
 *  - A store-installed APK should be a release build - UNTESTED
 *  - The override can be set in Studio's Run Configuration: "General" Tab, "Launch Flags" field: `--ez debugLogging true` or `--ez debugLogging false`
 *  - Setting the override without Studio - from the device itself or simple adb - is possible, it's an activity manager command line option.
 *    Terminal or adb shell: `am start com.unciv.app/com.unciv.app.AndroidLauncher --ez debugLogging true` (Tested)
 *
 *  * Note: Gets and keeps a reference to [AndroidLauncher] as [activity] only to get memory info for [CrashScreen][com.unciv.ui.crashhandling.CrashScreen].
 *
 *  @see com.unciv.utils.Log
 */
class AndroidLogBackend(private val activity: Activity) : LogBackend {
    private val isRelease: Boolean

    init {
        // BuildConfig.DEBUG is **NOT** helpful as it always imports com.badlogic.gdx.backends.android.BuildConfig

        /** This is controlled by the buildTypes -> isDebuggable flag in android -> build.gradle.kts */
        val isDebuggable = (activity.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val debugLogging = activity.intent.extras?.getBoolean("debugLogging")

        // Log.d(toAndroidTag(Tag("Log init")), "isDebuggable=$isDebuggable, debugLogging=$debugLogging")
        this.isRelease = !(debugLogging ?: isDebuggable) // cache as isRelease() is called all the time
    }

    override fun debug(tag: Tag, curThreadName: String, msg: String) {
        Log.d(toAndroidTag(tag), "[$curThreadName] $msg")
    }

    override fun error(tag: Tag, curThreadName: String, msg: String) {
        Log.e(toAndroidTag(tag), "[$curThreadName] $msg")
    }

    override fun isRelease(): Boolean {
        return isRelease
    }

    private enum class KnownSigningFingerprints(val label: String, val hex: String) {
        FDroid("F-Droid", "cd16e386469f23988fc9cdeca01bfefbd0deaed13a4907bce76e91acc9662c68"),
        PlayStore("Play Store", "3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d"),
        ;
        val digest: ByteArray = hex.toByteArray()
        companion object {
            fun of(digest: ByteArray?) =
                entries.firstOrNull { it.digest.contentEquals(digest) }?.label
                    ?: if (digest == null) "not found" else "unknown(${digest.toHexString()})"
        }
    }

    /**
     * @see com.unciv.app.desktop.SystemUtils.getSystemInfo
     */
    override fun getSystemInfo(): String {
        return try {
            val memoryInfo = getMemoryInfo()
            val javaRuntime = Runtime.getRuntime()
            val pm = activity.packageManager
            val pkgName = activity.packageName

            val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val buildType = if (isDebuggable) "debug" else "release"

            val installer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    pm.getInstallSourceInfo(pkgName).installingPackageName ?: "unknown/sideloaded"
                else "unknown"
            } catch (_: Throwable) {
                "unknown"
            }

            val signerFingerprint =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) null else {
                    try {
                        val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull()
                        cert?.let {
                            MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                        }
                    } catch (_: Throwable) {
                        null
                    }
                }
            val signedBy = try {
                KnownSigningFingerprints.of(signerFingerprint)
            } catch (_: Throwable) {
                "?"
            }

            buildString(512) {
                append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).appendLine()
                append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(')').appendLine()
                append("ABI: ")
                Build.SUPPORTED_ABIS.forEachIndexed { i, abi -> if (i > 0) append(", "); append(abi) }
                appendLine()
                append("Build type: ").appendLine(buildType)
                append("Installer: ").appendLine(installer)
                append("Signature: ").appendLine(signedBy)
                append("System Memory: ").appendLine(memoryInfo.totalMem.formatMB())
                append("    Available (used by Kernel): ").appendLine(memoryInfo.availMem.formatMB())
                append("    System Low Memory state: ").appendLine(memoryInfo.lowMemory)
                append("    Java heap limit: ").appendLine(javaRuntime.maxMemory().formatMB())
                append("    Java heap free: ").append(javaRuntime.freeMemory().formatMB())
            }
        } catch (ex: Throwable) {
            "System info unavailable: ${ex.javaClass.simpleName}"
        }
    }

    private fun getMemoryInfo() = ActivityManager.MemoryInfo().apply {
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(this)  // API writes into a structure we must supply
    }

    private fun Long.formatMB() = "${(this + 524288L) / 1048576L} MB"
}

private fun toAndroidTag(tag: Tag): String {
    // This allows easy filtering of logcat by tag "Unciv"
    val withUncivPrefix = if (tag.name.contains("unciv", true)) tag.name else "Unciv ${tag.name}"

    // Limit was removed in Nougat
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || tag.name.length <= TAG_MAX_LENGTH) {
        withUncivPrefix
    } else {
        withUncivPrefix.substring(0, TAG_MAX_LENGTH)
    }
}
