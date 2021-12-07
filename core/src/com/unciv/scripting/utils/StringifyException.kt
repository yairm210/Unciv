package com.unciv.scripting.utils


/**
 * @return String of exception preceded by entire stack trace.
 */
fun Exception.stringifyException(): String {
    val causes = arrayListOf<Throwable>()
    var cause: Throwable? = this
    while (cause != null) {
        causes.add(cause)
        cause = cause.cause
        //I swear this is okay to do.
    }
    return listOf(
        "\n",
        *this.stackTrace,
        "\n",
        *causes.asReversed().map { it.toString() }.toTypedArray()
    ).joinToString("\n")
}
// TODO: Move this to ExtensionFunctions.kt.
