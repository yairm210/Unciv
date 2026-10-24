package com.unciv.ui.components.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.unciv.ui.components.extensions.GdxKeyCodeFixes
import com.unciv.ui.screens.basescreen.UncivStage

/**
 *  Used to enable mapping unused mouse buttons to virtual keys, and allowing users to assign them as keyboard bindings
 *
 *  * [UncivStage] implements mapping to virtual keystrokes
 *  * [GdxKeyCodeFixes] handles visualization for the key bindings options page
 */
enum class VirtualMouseButtonKeys(val button: Int, val keyCode: Int, val label: String) {
    Middle(Input.Buttons.MIDDLE, 200, "Mouse middle button"),
    Back(Input.Buttons.BACK, 201, "Mouse back button"),
    Forward(Input.Buttons.FORWARD, 202, "Mouse forward button"),
    ;

    fun keyDown() = Gdx.input.inputProcessor?.keyDown(keyCode)
    fun keyUp() = Gdx.input.inputProcessor?.keyUp(keyCode)

    companion object {
        fun fromButton(button: Int) = entries.firstOrNull { it.button == button }
        fun fromKey(keyCode: Int) = entries.firstOrNull { it.keyCode == keyCode }
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label }
    }
}
