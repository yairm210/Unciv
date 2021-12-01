package com.unciv.scripting.utils


enum class ScriptingApiExposure {
    All, Player, Cheats, Mods
} // Mods should have more restrictive access to system-facing members than everything else, since they're the only untrusted code.

@Retention(AnnotationRetention.RUNTIME)
annotation class ScriptingApiAccessible(
    val readableBy: Array<ScriptingApiExposure> = arrayOf(ScriptingApiExposure.All),
    val settableBy: Array<ScriptingApiExposure> = arrayOf(ScriptingApiExposure.All)
)

// TODO (Later): Eventually use this to whitelist safe API accessible members for security/permission control.
// Probably keep a debug flag to expose everything. Or just reuse godmode flag?
// Anything that can be called directly by the GUI should probably have Player-level exposure. If it lets rules be broken, that should probably be fixed in Kotlin-side.
// Something will have to be done/decided about members of built-in types too.
