package com.unciv.utils

import java.time.Instant
import java.util.regex.Pattern


object Log {

    /** Add -DnoLog=<comma-separated-list-of-class-names> to not log these classes. */
    private val disabledLogsFromProperty = System.getProperty("noLog")?.split(',')?.toMutableSet() ?: mutableSetOf()

    /** Log tags (= class names) **containing** the following Strings will not be logged.  */
    val disableLogsFrom = if (disabledLogsFromProperty.isEmpty()) {
        mutableSetOf(
            "Battle",
            "KeyPressDispatcher",
            "Music",
            "Sounds",
            "Translations",
            "WorkerAutomation",
        )
    } else {
        disabledLogsFromProperty
    }

    var backend: LogBackend = DefaultLogBackend()

    fun shouldLog(tag: Tag = getTag()): Boolean {
        return !backend.isRelease() && !isTagDisabled(tag)
    }

    /**
     * Logs the given message by [java.lang.String.format]ting them with an optional list of [params].
     *
     * Only actually does something when logging is enabled.
     *
     * A tag will be added equal to the name of the calling class.
     *
     * The [params] can contain value-producing lambdas, which will be called and their value used as parameter for the message instead.
     */
    fun debug(msg: String, vararg params: Any?) {
        if (backend.isRelease()) return
        debug(getTag(), msg, *params)
    }

    /**
     * Logs the given message by [java.lang.String.format]ting them with an optional list of [params].
     *
     * Only actually does something when logging is enabled.
     *
     * The [params] can contain value-producing lambdas, which will be called and their value used as parameter for the message instead.
     */
    fun debug(tag: Tag, msg: String, vararg params: Any?) {
        if (!shouldLog(tag)) return
        val formatArgs = replaceLambdasWithValues(params)
        doLog(backend::debug, tag, msg, *formatArgs)
    }

    /**
     * Logs the given [throwable] by appending it to [msg]
     *
     * Only actually does something when logging is enabled.
     *
     * A tag will be added equal to the name of the calling class.
     */
    fun debug(msg: String, throwable: Throwable) {
        if (backend.isRelease()) return
        debug(getTag(), msg, throwable)
    }
    /**
     * Logs the given [throwable] by appending it to [msg]
     *
     * Only actually does something when logging is enabled.
     */
    fun debug(tag: Tag, msg: String, throwable: Throwable) {
        if (!shouldLog(tag)) return
        doLog(backend::debug, tag, buildThrowableMessage(msg, throwable))
    }

    /**
     * Logs the given error message by [java.lang.String.format]ting them with an optional list of [params].
     *
     * Always logs, even in release builds.
     *
     * A tag will be added equal to the name of the calling class.
     *
     * The [params] can contain value-producing lambdas, which will be called and their value used as parameter for the message instead.
     */
    fun error(msg: String, vararg params: Any?) {
        error(getTag(), msg, *params)
    }


    /**
     * Logs the given error message by [java.lang.String.format]ting them with an optional list of [params].
     *
     * Always logs, even in release builds.
     *
     * The [params] can contain value-producing lambdas, which will be called and their value used as parameter for the message instead.
     */
    fun error(tag: Tag, msg: String, vararg params: Any?) {
        val formatArgs = replaceLambdasWithValues(params)
        doLog(backend::error, tag, msg, *formatArgs)
    }

    /**
     * Logs the given [throwable] by appending it to [msg]
     *
     * Always logs, even in release builds.
     *
     * A tag will be added equal to the name of the calling class.
     */
    fun error(msg: String, throwable: Throwable) {
        error(getTag(), msg, throwable)
    }
    /**
     * Logs the given [throwable] by appending it to [msg]
     *
     * Always logs, even in release builds.
     */
    fun error(tag: Tag, msg: String, throwable: Throwable) {
        doLog(backend::error, tag, buildThrowableMessage(msg, throwable))
    }
}

class Tag(val name: String)

interface LogBackend {
    fun debug(tag: Tag, curThreadName: String, msg: String)
    fun error(tag: Tag, curThreadName: String, msg: String)

    /** Do not log on release builds for performance reasons. */
    fun isRelease(): Boolean
}

/** Only for tests, or temporary main() functions */
open class DefaultLogBackend : LogBackend {
    override fun debug(tag: Tag, curThreadName: String, msg: String) {
        println("${Instant.now()} [${curThreadName}] [${tag.name}] $msg")
    }
    override fun error(tag: Tag, curThreadName: String, msg: String) {
        println("${Instant.now()} [${curThreadName}] [${tag.name}] [ERROR] $msg")
    }

    override fun isRelease(): Boolean {
        return false
    }
}

/** Shortcut for [Log.debug] */
fun debug(msg: String, vararg params: Any?) {
    Log.debug(msg, *params)
}

/** Shortcut for [Log.debug] */
fun debug(tag: Tag, msg: String, vararg params: Any?) {
    Log.debug(tag, msg, *params)
}

/** Shortcut for [Log.debug] */
fun debug(msg: String, throwable: Throwable) {
    Log.debug(msg, throwable)
}

/** Shortcut for [Log.debug] */
fun debug(tag: Tag, msg: String, throwable: Throwable) {
    Log.debug(tag, msg, throwable)
}

private fun doLog(logger: (Tag, String, String) -> Unit, tag: Tag, msg: String, vararg params: Any?) {
    logger(tag, Thread.currentThread().name, msg.format(*params))
}

private fun isTagDisabled(tag: Tag): Boolean {
    return Log.disableLogsFrom.any { it in tag.name }
}

private fun buildThrowableMessage(msg: String, throwable: Throwable): String {
    return "$msg | ${throwable.stackTraceToString()}"
}

private fun replaceLambdasWithValues(params: Array<out Any?>): Array<out Any?> {
    var out: Array<Any?>? = null
    for (i in 0 until params.size) {
        val param = params[i]
        if (param is Function0<*>) {
            if (out == null) out = arrayOf(*params)
            out[i] = param.invoke()
        }
    }
    return out ?: params
}


private fun getTag(): Tag {
    val firstOutsideStacktrace = Throwable().stackTrace.filter { "com.unciv.utils.Log" !in it.className }.first()
    val simpleClassName = firstOutsideStacktrace.className.substringAfterLast('.')
    return Tag(removeAnonymousSuffix(simpleClassName))
}

private val ANONYMOUS_CLASS_PATTERN = Pattern.compile("(\\$\\d+)+$") // all "$123" at the end of the class name
private fun removeAnonymousSuffix(tag: String): String {
    val matcher = ANONYMOUS_CLASS_PATTERN.matcher(tag)
    return if (matcher.find()) {
        matcher.replaceAll("")
    } else {
        tag
    }
}
