package com.unciv.app

import android.os.Build
import android.util.Log
import com.unciv.utils.LogBackend
import com.unciv.utils.Tag

private const val TAG_MAX_LENGTH = 23

class AndroidLogBackend : LogBackend {

    override fun debug(tag: Tag, curThreadName: String, msg: String) {
        Log.d(limitTag(tag), "[$curThreadName] $msg")
    }

    override fun error(tag: Tag, curThreadName: String, msg: String) {
        Log.e(limitTag(tag), "[$curThreadName] $msg")
    }

    override fun isRelease(): Boolean {
        return !BuildConfig.DEBUG
    }
}

private fun limitTag(tag: Tag): String {
    // Limit was removed in Nougat
    val finalTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || tag.name.length <= TAG_MAX_LENGTH) {
        tag.name
    } else {
        tag.name.substring(0, TAG_MAX_LENGTH)
    }
    return finalTag
}
