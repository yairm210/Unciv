package com.unciv.ui.screens.devconsole

@Suppress("EnumEntryName")
enum class DevConsoleCommand {
    unit {
        override fun handle(console: DevConsolePopup, params: List<String>) =
            DevConsoleCommandUnit.handle(console, params)
    },
    city {
        override fun handle(console: DevConsolePopup, params: List<String>) =
            DevConsoleCommandCity.handle(console, params)
    },
    help {
        override fun handle(console: DevConsolePopup, params: List<String>) =
            "Available commands: " + DevConsoleCommand.values().joinToString { it.name }
    }
    ;

    abstract fun handle(console: DevConsolePopup, params: List<String>): String?

    companion object {
        fun handle(console: DevConsolePopup, params: List<String>): String? {
            if (params.isEmpty())
                return help.handle(console, params)
            val handler = values().firstOrNull { it.name == params[0] }
                ?: return "Invalid command"
            return handler.handle(console, params.drop(1))
        }

        internal fun String.toCliInput() = this.lowercase().replace(" ","-")
    }
}
