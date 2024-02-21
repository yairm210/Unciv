package com.unciv.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.badlogic.gdx.backends.android.BuildConfig
import com.unciv.utils.LogBackend
import com.unciv.utils.Tag

private const val TAG_MAX_LENGTH = 23

/**
 *  Unciv's logger implementation for Android
 *
 *  * Note: Gets and keeps a reference to [AndroidLauncher] as [activity] only to get memory info for [CrashScreen][com.unciv.ui.crashhandling.CrashScreen].
 *
 *  @see com.unciv.utils.Log
 */
class AndroidLogBackend(private val activity: Activity) : LogBackend {

    override fun debug(tag: Tag, curThreadName: String, msg: String) {
        Log.d(toAndroidTag(tag), "[$curThreadName] $msg")
    }

    override fun error(tag: Tag, curThreadName: String, msg: String) {
        Log.e(toAndroidTag(tag), "[$curThreadName] $msg")
    }

    override fun isRelease(): Boolean {
        return !BuildConfig.DEBUG
    }

    /**
     * @see com.unciv.app.desktop.SystemUtils.getSystemInfo
     */
    override fun getSystemInfo(): String {
        val memoryInfo = getMemoryInfo()
        val javaRuntime = Runtime.getRuntime()
        return """
        Device Model: ${Build.MODEL}
        API Level: ${Build.VERSION.SDK_INT}
        System Memory: ${memoryInfo.totalMem.formatMB()}
            Available (used by Kernel): ${memoryInfo.availMem.formatMB()}
            System Low Memory state: ${memoryInfo.lowMemory}
            Java heap limit: ${javaRuntime.maxMemory().formatMB()}
            Java heap free: ${javaRuntime.freeMemory().formatMB()}
        """.trimIndent()
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
