package com.unciv.ui.components

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener
import com.unciv.Constants
import com.unciv.logic.event.EventBus
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getAscendant
import com.unciv.ui.components.extensions.getOverlap
import com.unciv.ui.components.extensions.right
import com.unciv.ui.components.extensions.stageBoundingBox
import com.unciv.ui.components.extensions.top
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.UncivStage
import com.unciv.utils.Concurrency
import com.unciv.utils.withGLContext
import kotlinx.coroutines.delay

object UncivTextField {
    /**
     * Creates a text field that has nicer platform-specific input added compared to the default gdx [TextField].
     * @param hint The text that should be displayed in the text field when no text is entered, will automatically be translated
     * @param preEnteredText The text already entered within this text field. Supported on all platforms.
     * @param onFocusChange This will be called every time the field receives or loses focus. Receiver is the field, so you can simply use its elements. Parameter `it` is a Boolean indicating focus was received.
     */
    fun create(hint: String, preEnteredText: String = "", onFocusChange: (TextField.(Boolean) -> Unit)? = null): TextField {
        @Suppress("UNCIV_RAW_TEXTFIELD")
        val textField = TextField(preEnteredText, BaseScreen.skin)
        val translatedHint = hint.tr()
        textField.messageText = translatedHint
        textField.addListener(object : FocusListener() {
            override fun keyboardFocusChanged(event: FocusEvent, actor: Actor, focused: Boolean) {
                if (focused) {
                    textField.scrollAscendantToTextField()
                }
                onFocusChange?.invoke(textField, focused)
            }
        })

        if (Gdx.app.type == Application.ApplicationType.Android)
            TextfieldImprovements.add(textField)
        return textField
    }
}

/**
 * Tries to scroll a [ScrollPane] ascendant of the text field so that this text field is in the middle of the visible area.
 *
 * @return true if the text field is visible after this operation
 */
fun TextField.scrollAscendantToTextField(): Boolean {
    val stage = this.stage
    if (stage !is UncivStage) return false

    val scrollPane = this.getAscendant { it is ScrollPane } as ScrollPane?
    val visibleArea = stage.lastKnownVisibleArea
    val textFieldStageBoundingBox = this.stageBoundingBox
    if (scrollPane == null) return visibleArea.contains(textFieldStageBoundingBox)

    val scrollPaneBounds = scrollPane.stageBoundingBox
    val visibleScrollPaneArea = scrollPaneBounds.getOverlap(visibleArea)
    if (visibleScrollPaneArea == null) {
        return false
    } else if (visibleScrollPaneArea.contains(textFieldStageBoundingBox)) {
        return true
    }

    val scrollContent = scrollPane.actor
    val textFieldScrollContentCoords = localToAscendantCoordinates(scrollContent, Vector2(0f, 0f))

    // It's possible that our textField can't be (fully) scrolled to be within the visible scrollPane area
    val pixelsNotVisibleOnLeftSide = (visibleScrollPaneArea.x - scrollPaneBounds.x).coerceAtLeast(0f)
    val textFieldDistanceFromLeftSide = textFieldScrollContentCoords.x
    val pixelsNotVisibleOnRightSide = (scrollPaneBounds.right - visibleScrollPaneArea.right).coerceAtLeast(0f)
    val textFieldDistanceFromRightSide = scrollContent.width - (textFieldScrollContentCoords.x + this.width)
    val pixelsNotVisibleOnTop = (scrollPaneBounds.top - visibleScrollPaneArea.top).coerceAtLeast(0f)
    val textFieldDistanceFromTop = scrollContent.height - (textFieldScrollContentCoords.y + this.height)
    val pixelsNotVisibleOnBottom = (visibleScrollPaneArea.y - scrollPaneBounds.y).coerceAtLeast(0f)
    val textFieldDistanceFromBottom = textFieldScrollContentCoords.y
    // If the visible scroll pane area is smaller than our text field, it will always be partly obscured
    if (visibleScrollPaneArea.width < this.width || visibleScrollPaneArea.height < this.height
            // If the amount of pixels obscured near a scrollContent edge is larger than the distance of the text field to that edge, it will always be (partly) obscured
            || pixelsNotVisibleOnLeftSide > textFieldDistanceFromLeftSide
            || pixelsNotVisibleOnRightSide > textFieldDistanceFromRightSide
            || pixelsNotVisibleOnTop > textFieldDistanceFromTop
            || pixelsNotVisibleOnBottom > textFieldDistanceFromBottom) {
        return false
    }

    // We want to put the text field in the middle of the visible area
    val scrollXMiddle = textFieldScrollContentCoords.x - this.width / 2 + visibleScrollPaneArea.width / 2
    // If the visible area is to the right of the left edge of the scroll pane, we need to scroll that much farther to get to the real visible middle
    scrollPane.scrollX = pixelsNotVisibleOnLeftSide + scrollXMiddle

    // ScrollPane.scrollY has the origin at the top instead of at the bottom, so + for height / 2 instead of -
    // We want to put the text field in the middle of the visible area
    val scrollYMiddleGdxOrigin = textFieldScrollContentCoords.y + this.height / 2 + visibleScrollPaneArea.height / 2
    // If the visible area is below the top edge of the scroll pane, we need to scroll that much farther to get to the real visible middle
    // Also, convert to scroll pane origin (0 is on top instead of bottom)
    scrollPane.scrollY = pixelsNotVisibleOnTop + scrollContent.height - scrollYMiddleGdxOrigin

    return true
}

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

        addCloseButton(Constants.cancel)
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
