package com.unciv.scripting.utils


/**
 * @param exception Any Exception.
 * @return String of exception preceded by entire stack trace.
 */
fun stringifyException(exception: Exception): String = listOf(*exception.getStackTrace(), exception.toString()).joinToString("\n")
