package com.unciv.scripting.utils


fun stringifyException(exception: Exception): String = listOf(*exception.getStackTrace(), exception.toString()).joinToString("\n")
