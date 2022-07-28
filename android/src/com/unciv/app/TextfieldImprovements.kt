package com.unciv.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener
import com.unciv.logic.event.EventBus
import com.unciv.models.translations.tr
import com.unciv.ui.UncivStage
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.extensions.getAscendant
import com.unciv.ui.utils.scrollAscendantToTextField
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.withGLContext
import kotlinx.coroutines.delay

object TextfieldImprovements {
    private val hideKeyboard = { Gdx.input.setOnscreenKeyboardVisible(false) }
    fun add(textField: TextField): TextField {
        textField.addListener(object : InputListener() {
            private val events = EventBus.EventReceiver()
            init {
                events.receive(UncivStage.VisibleAreaChanged::class) {
                    if (textField.stage == null || !textField.hasKeyboardFocus()) return@receive
                    Concurrency.run {
                        // If anything resizes, it also does so with this event. So we need to wait for that to finish to update the scroll position.
                        delay(100)
                        withGLContext {
                            if (textField.stage == null) return@withGLContext

                            if (textField.scrollAscendantToTextField()) {
                                val scrollPane = textField.getAscendant { it is ScrollPane } as ScrollPane?
                                // when screen dimensions change, we don't want an animation for scrolling, just show, just show the textfield immediately
                                scrollPane?.updateVisualScroll()
                            } else {
                                // We can't scroll the text field into view, so we need to show a popup
                                TextfieldPopup(textField).open()
                            }
                        }
                    }
                }
            }
            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                addPopupCloseListener(textField)
                return false
            }
        })
        textField.addListener(object : FocusListener() {
            override fun keyboardFocusChanged(event: FocusEvent?, actor: Actor?, focused: Boolean) {
                if (focused) {
                    addPopupCloseListener(textField)
                    Gdx.input.setOnscreenKeyboardVisible(true)
                }
            }
        })

        return textField
    }

    private fun addPopupCloseListener(textField: TextField) {
        val popup = textField.getAscendant { it is Popup } as Popup?
        if (popup != null && !popup.closeListeners.contains(hideKeyboard)) {
            popup.closeListeners.add(hideKeyboard)
        }
    }
}

class TextfieldPopup(
    textField: TextField
) : Popup(textField.stage) {
    val popupTextfield = clone(textField)
    init {
        addGoodSizedLabel(popupTextfield.messageText)
            .colspan(2)
            .row()

        add(popupTextfield)
            .width(stageToShowOn.width / 2)
            .colspan(2)
            .row()

        addCloseButton("Cancel")
            .left()
        addOKButton { textField.text = popupTextfield.text }
            .right()
            .row()

        showListeners.add {
            stageToShowOn.keyboardFocus = popupTextfield
        }
        closeListeners.add {
            stageToShowOn.keyboardFocus = null
            Gdx.input.setOnscreenKeyboardVisible(false)
        }
    }

    private fun clone(textField: TextField): TextField {
        @Suppress("UNCIV_RAW_TEXTFIELD") // we are copying the existing text field
        val copy = TextField(textField.text, textField.style)
        copy.textFieldFilter = textField.textFieldFilter
        copy.messageText = textField.messageText
        copy.setSelection(textField.selectionStart, textField.selection.length)
        copy.cursorPosition = textField.cursorPosition
        copy.alignment = textField.alignment
        copy.isPasswordMode = textField.isPasswordMode
        copy.onscreenKeyboard = textField.onscreenKeyboard
        return copy
    }
}
