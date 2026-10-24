
package com.unciv.ui.screens.devconsole

import com.badlogic.gdx.graphics.Color

@ConsistentCopyVisibility  // See https://youtrack.jetbrains.com/issue/KT-11914
internal data class DevConsoleResponse private constructor (
    val color: Color,
    val message: String? = null,
    val isOK: Boolean = false
) {
    companion object {
        val OK = DevConsoleResponse(Color.GREEN, isOK = true)
        fun ok(message: String) = DevConsoleResponse(Color.GREEN, message, true)
        fun error(message: String) = DevConsoleResponse(Color.RED, message)
        fun hint(message: String) = DevConsoleResponse(Color.GOLD, message)
    }
}
