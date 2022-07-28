package com.unciv.app

import android.os.Build
import android.util.Log
import com.unciv.utils.LogBackend
import com.unciv.utils.Tag

private const val TAG_MAX_LENGTH = 23

class AndroidLogBackend : LogBackend {

    override fun debug(tag: Tag, curThreadName: String, msg: String) {
        Log.d(toAndroidTag(tag), "[$curThreadName] $msg")
    }

    override fun error(tag: Tag, curThreadName: String, msg: String) {
        Log.e(toAndroidTag(tag), "[$curThreadName] $msg")
    }

    override fun isRelease(): Boolean {
        return !BuildConfig.DEBUG
    }
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
