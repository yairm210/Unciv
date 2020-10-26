package com.unciv.models

data class CrashReport(
        val gameInfo: String,
        val mods: LinkedHashSet<String>,
        val version: String
)