package com.unciv.ui.components.widgets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.OnscreenKeyboardType
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts

/**
 * Creates a text field with two deviations from the default Gdx [TextField]:
 * - Turns off Gdx color markup support while drawing, so [] in the text display properly
 * - If this TextField handles the Tab key (see [keyShortcuts]), its [focus navigation feature][TextField.next] is disabled (otherwise it would search for another TextField in the same parent to give focus to, and remove the on-screen keyboard if it finds none).
 * - Tells Android's on screen keyboard when this TextField is in password mode
 * - Shows/hides Android's on screen keyboard automatically whith keyboard focus gain/loss
 * - Fixes Gdx's password char from a cp-1252 Bullet to Unicode
 * - constructors same as standard TextField, plus a cloning constructor.
 */
open class TextFieldWithFixes private constructor(text: String, style: TextFieldStyle) : TextField(text, style) {
    internal constructor(text: String, skin: Skin) : this(text, skin.get(TextFieldStyle::class.java))
    internal constructor(textField: TextFieldWithFixes) : this(textField.text, textField.style) {
        textFieldFilter = textField.textFieldFilter
        messageText = textField.messageText
        hasSelection = textField.hasSelection
        cursor = textField.cursor
        selectionStart = textField.selectionStart
        alignment = textField.alignment
        isPasswordMode = textField.isPasswordMode
    }

    init {
        onscreenKeyboard = OnscreenKeyboard { visible ->
            Gdx.input.setOnscreenKeyboardVisible(visible, if (isPasswordMode) OnscreenKeyboardType.Password else OnscreenKeyboardType.Default)
        }
        addListener(object : FocusListener() {
            override fun keyboardFocusChanged(event: FocusEvent, actor: Actor?, focused: Boolean) {
                if (!focused && event.relatedActor is TextFieldWithFixes) return // Will be reactivated anyway
                onscreenKeyboard.show(focused)
            }
        })
        setPasswordCharacter('\u2022') // Gdx bug: They use 149, which is a bullet in cp-1252, but not in a sensible encoding
    }

    // Without this, the DeveloperConsole can't catch the Tab key for Autocomplete reliably
    override fun next(up: Boolean) {
        if (KeyCharAndCode.TAB in keyShortcuts) return
        super.next(up)
    }

    override fun setDisabled(disabled: Boolean) {
        onscreenKeyboard.show(!disabled)
        super.setDisabled(disabled)
    }

    // Note - this way to force TextField to display `[]` characters normally is an incomplete hack.
    // The complete way would either require overriding `updateDisplayText` which is private, or all methods calling it,
    // which are many including the keyboard listener, or keep a separate font without markup enabled around and put that
    // into the default style, including its own NativeBitmapFontData instance and texture - involves quite some redesign.
    // That said, observing the deficiency is hard - the internal `glyphPositions` could theoretically get out of sync, affecting selection and caret display.
    override fun layout() {
        val oldEnable = style.font.data.markupEnabled
        style.font.data.markupEnabled = false
        super.layout()
        style.font.data.markupEnabled = oldEnable
    }

    override fun drawText(batch: Batch, font: BitmapFont, x: Float, y: Float) {
        val oldEnable = font.data.markupEnabled
        font.data.markupEnabled = false
        super.drawText(batch, font, x, y)
        font.data.markupEnabled = oldEnable
    }

    fun copyTextAndSelection(textField: TextFieldWithFixes) {
        text = textField.text
        hasSelection = textField.hasSelection
        cursor = textField.cursor
        selectionStart = textField.selectionStart
    }
}
