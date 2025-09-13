package com.unciv.utils

import yairm210.purity.annotations.Immutable
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import java.time.Instant
import java.util.regex.Pattern


/**
 * If you wonder why something isn't logged, it's probably because it's in the [disableLogsFrom] field.
 *
 * To stop logging/start logging classes, you have these options:
 *
 * 1. Edit the set [disableLogsFrom] here in the source code
 * 2. Use a Java system property `-DnoLog=<comma-separated-list-of-class-names>` to overwrite [disableLogsFrom] completely
 *    (potentially copy/pasting the default class list from here and adjusting to your liking)
 * 3. While the application is running, set a breakpoint somewhere and do a "Watch"/"Evaluate expression" with `Log.disableLogsFrom.add/remove("Something")`
 */
object Log {

    /**
     * Add -DnoLog=<comma-separated-list-of-partial-class-names> to not log these classes.
     * Log tags (= class names) **containing** these Strings will not be logged.
     * You _can_ disable the default exclusions with an empty `-DnoLog=` argument.
     */
    @Immutable val disableLogsFrom = (
            System.getProperty("noLog")
            ?: "Battle,Music,Sounds,Translations,WorkerAutomation,assignRegions,RoadBetweenCitiesAutomation"
        ).split(',').filterNot { it.isEmpty() }.toSet()

    /**
     * Add -DonlyLog=<comma-separated-list-of-partial-class-names> to only log specific classes.
     * Only Log tags (= class names) **containing** these Strings will be logged (Not set means all)
     * [disableLogsFrom] will still be respected if this is set.
     * Note you cannot disable all logging with `-DonlyLog=`, use `-DonlyLog=~~~` instead.
     */
    @Immutable 
    val enableLogsFrom = (
            System.getProperty("onlyLog")
            ?: ""
        ).split(',').filterNot { it.isEmpty() }.toSet()

    var backend: LogBackend = DefaultLogBackend()

    @Readonly
    fun shouldLog(tag: Tag = getTag()): Boolean {
        return !backend.isRelease() && !isTagDisabled(tag)
    }

    /**
     * Logs the given message by [String.format][java.lang.String.format]ting them with an optional list of [params]. Since this uses
     * [format specifiers][java.util.Formatter], make sure your message only contains `%` characters correctly used as [format specifiers][java.util.Formatter]
     * and never do manual string concatenation, only use [params] to log arbitrary text that could contain `%` characters.
     *
     * Only actually does something when logging is enabled.
     *
     * A tag will be added equal to the name of the calling class.
     *
     * The [params] can contain value-producing lambdas, which will be called and their value used as parameter for the message instead.
     */
    @Pure @Suppress("purity") // good suppression - log considered pure everywhere
    fun debug(msg: String, vararg params: Any?) {
        if (backend.isRelease()) return
        debug(getTag(), msg, *params)
    }

    /**
     * Logs the given message by [String.format][java.lang.String.format]ting them with an optional list of [params]. Since this uses
     * [format specifiers][java.util.Formatter], make sure your message only contains `%` characters correctly used as [format specifiers][java.util.Formatter]
     * and never do manual string concatenation, only use [params] to log arbitrary text that could contain `%` characters.
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
     * Logs the given [throwable] by appending it to [msg].
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
     * Logs the given [throwable] by appending it to [msg].
     *
     * Only actually does something when logging is enabled.
     */
    fun debug(tag: Tag, msg: String, throwable: Throwable) {
        if (!shouldLog(tag)) return
        doLog(backend::debug, tag, buildThrowableMessage(msg, throwable))
    }

    /**
     * Logs the given message by [String.format][java.lang.String.format]ting them with an optional list of [params]. Since this uses
     * [format specifiers][java.util.Formatter], make sure your message only contains `%` characters correctly used as [format specifiers][java.util.Formatter]
     * and never do manual string concatenation, only use [params] to log arbitrary text that could contain `%` characters.
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
     * Logs the given message by [String.format][java.lang.String.format]ting them with an optional list of [params]. Since this uses
     * [format specifiers][java.util.Formatter], make sure your message only contains `%` characters correctly used as [format specifiers][java.util.Formatter]
     * and never do manual string concatenation, only use [params] to log arbitrary text that could contain `%` characters.
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
     * Logs the given [throwable] by appending it to [msg].
     *
     * Always logs, even in release builds.
     *
     * A tag will be added equal to the name of the calling class.
     */
    fun error(msg: String, throwable: Throwable) {
        error(getTag(), msg, throwable)
    }
    /**
     * Logs the given [throwable] by appending it to [msg].
     *
     * Always logs, even in release builds.
     */
    fun error(tag: Tag, msg: String, throwable: Throwable) {
        doLog(backend::error, tag, buildThrowableMessage(msg, throwable))
    }

    /**
     * Get string information about operation system
     */
    fun getSystemInfo(): String {
        return backend.getSystemInfo()
    }
}

class Tag(val name: String)

interface LogBackend {
    fun debug(tag: Tag, curThreadName: String, msg: String)
    fun error(tag: Tag, curThreadName: String, msg: String)

    /** Do not log on release builds for performance reasons. */
    @Readonly fun isRelease(): Boolean

    /** Get string information about operation system */
    fun getSystemInfo(): String
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

    override fun getSystemInfo(): String {
        return ""
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
    val formattedMessage = if (params.isEmpty()) msg else msg.format(*params)
    logger(tag, Thread.currentThread().name, formattedMessage)
}

@Pure
private fun isTagDisabled(tag: Tag): Boolean {
    return Log.disableLogsFrom.any { it in tag.name } ||
            (Log.enableLogsFrom.isNotEmpty() && Log.enableLogsFrom.none { it in tag.name })
}

private fun buildThrowableMessage(msg: String, throwable: Throwable): String {
    return "$msg | ${throwable.stackTraceToString()}"
}

private fun replaceLambdasWithValues(params: Array<out Any?>): Array<out Any?> {
    var out: Array<Any?>? = null
    for (i in params.indices) {
        val param = params[i]
        if (param is Function0<*>) {
            if (out == null) out = arrayOf(*params)
            out[i] = param.invoke()
        }
    }
    return out ?: params
}


@Readonly
private fun getTag(): Tag {
    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    val firstOutsideStacktrace = Throwable().stackTrace.first { "com.unciv.utils.Log" !in it.className }
    val simpleClassName = firstOutsideStacktrace.className.substringAfterLast('.')
    return Tag(removeAnonymousSuffix(simpleClassName))
}

private val ANONYMOUS_CLASS_PATTERN = Pattern.compile("(\\$\\d+)+$") // all "$123" at the end of the class name
@Pure
private fun removeAnonymousSuffix(tag: String): String {
    val matcher = ANONYMOUS_CLASS_PATTERN.matcher(tag)
    return if (matcher.find()) {
        matcher.replaceAll("")
    } else {
        tag
    }
}
