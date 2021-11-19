package com.unciv.scripting.utils


/**
 * @param exception Any Exception.
 * @return String of exception preceded by entire stack trace.
 */
fun stringifyException(exception: Exception): String {
    val causes = arrayListOf<Throwable>()
    var cause: Throwable? = exception
    while (cause != null) {
        causes.add(cause)
        cause = cause.cause
        //I swear this is okay to do.
    }
    return listOf(
        "\n",
        *exception.getStackTrace(),
        "\n",
        *causes.asReversed().map{ it.toString() }.toTypedArray()
    ).joinToString("\n")
}
// TODO: Recursively add .getcause too.
// TODO: Move this to ExtensionFunctions.kt.
