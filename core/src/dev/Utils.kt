package dev

import com.unciv.utils.Log

inline fun time(function: () -> Any) : Long {
    val start = System.currentTimeMillis()
    function()
    val duration = System.currentTimeMillis() - start
    return duration
}

fun logSourceHint() {
    Log.debug("${Throwable().stackTrace[1].fileName}:${Throwable().stackTrace[1].lineNumber}")
}
