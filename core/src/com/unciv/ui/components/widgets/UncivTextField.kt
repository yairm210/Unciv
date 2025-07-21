package com.unciv.ui.components.widgets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getAscendant
import com.unciv.ui.components.extensions.getOverlap
import com.unciv.ui.components.extensions.right
import com.unciv.ui.components.extensions.stageBoundingBox
import com.unciv.ui.components.extensions.top
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.CursorHoverInputListener
import com.badlogic.gdx.graphics.Cursor
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.UncivStage
import com.unciv.utils.Concurrency
import com.unciv.utils.withGLContext
import java.text.ParseException
import kotlinx.coroutines.delay

/**
 * Creates a text field that has nicer platform-specific input added compared to the default Gdx [TextField].
 *
 * - On Android, manages on-screen keyboard visibility and reacts to how the on-screen keyboard reduces screen space.
 * - Tries to scroll the field into view when receiving focus using an ascendant ScrollPane.
 * - If view of the field is still obscured, show am "emergency" Popup instead (can help when on Android the on-screen keyboard is large and the screen small).
 * - If this TextField handles the Tab key (see [keyShortcuts]), its [focus navigation feature][TextField.next] is disabled (otherwise it would search for another TextField in the same parent to give focus to, and remove the on-screen keyboard if it finds none).
 * - All parameters are supported on all platforms.
 *
 * @param hint The text that should be displayed in the text field when no text is entered and it does not have focus, will automatically be translated. Also shown as Label in the "emergency" Popup.
 * @param preEnteredText the text initially entered within this text field.
 * @param onFocusChange a callback that will be notified when this TextField loses or gains the [keyboardFocus][com.badlogic.gdx.scenes.scene2d.Stage.keyboardFocus].
 *        Receiver is the field, so you can simply use its elements. Parameter `it` is a Boolean indicating focus was received.
 */
open class UncivTextField(
    hint: String,
    preEnteredText: String = "",
    private val onFocusChange: (TextField.(Boolean) -> Unit)? = null
) : TextFieldWithFixes(preEnteredText, BaseScreen.skin) {
    private val isAndroid = false // disabled for now since it's not actually working // Gdx.app.type == Application.ApplicationType.Android
    private val hideKeyboard = { Gdx.input.setOnscreenKeyboardVisible(false) }

    init {
        messageText = hint.tr()

        this.addListener(UncivTextFieldFocusListener())
        this.addListener(CursorHoverInputListener(Cursor.SystemCursor.Ibeam))

        if (isAndroid) this.addListener(VisibleAreaChangedListener())
    }

    private inner class UncivTextFieldFocusListener : FocusListener() {
        override fun keyboardFocusChanged(event: FocusEvent, actor: Actor, focused: Boolean) {
            if (focused) {
                scrollAscendantToTextField()
                if (isAndroid) {
                    addPopupCloseListener()
                    Gdx.input.setOnscreenKeyboardVisible(true)
                }
            }
            onFocusChange?.invoke(this@UncivTextField, focused)
        }
    }

    private inner class VisibleAreaChangedListener : InputListener() {
        private val events = EventBus.EventReceiver()
        init {
            events.receive(UncivStage.VisibleAreaChanged::class) {
                if (stage == null || !hasKeyboardFocus()) return@receive
                Concurrency.run {
                    // If anything resizes, it also does so with this event. So we need to wait for that to finish to update the scroll position.
                    delay(100)
                    withGLContext {
                        if (stage == null) return@withGLContext

                        if (scrollAscendantToTextField()) {
                            val scrollPane = getAscendant<ScrollPane>()
                            // when screen dimensions change, we don't want an animation for scrolling, just show the textfield immediately
                            scrollPane?.updateVisualScroll()
                        } else {
                            // We can't scroll the text field into view, so we need to show a popup
                            TextfieldPopup().open()
                        }
                    }
                }
            }
        }
        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            addPopupCloseListener()
            return false
        }
    }

    private fun addPopupCloseListener() {
        val popup = getAscendant<Popup>()
        if (popup != null && !popup.closeListeners.contains(hideKeyboard)) {
            popup.closeListeners.add(hideKeyboard)
        }
    }

    /**
     * Tries to scroll a [ScrollPane] ascendant of the text field so that this text field is in the middle of the visible area.
     *
     * @return true if the text field is visible after this operation
     */
    private fun scrollAscendantToTextField(): Boolean {
        val stage = this.stage as? UncivStage ?: return false

        val scrollPane = this.getAscendant<ScrollPane>()
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

    private inner class TextfieldPopup : Popup(stage) {
        val popupTextfield = TextFieldWithFixes(this@UncivTextField)
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
            addOKButton { this@UncivTextField.copyTextAndSelection(popupTextfield) }
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
    }

    /**
     *  Specialization of [UncivTextField] for numbers. See its documentation for improvements over [TextField].
     *  - Note: It uses the generic [Number] class and thus supports both floating-point and integer as input.
     *          You might need a conversion when retrieving the result.
     *  - Note: [maxLength] is set to 26, but you can change it later.
     *  - Limitation: Depending on Java's decisions for your locale, the displayed string will likely contain thousands separators.
     *                These are ignored when parsing!
     *                However, user input will not update them, fix misplaced ones, or add them to pasted numbers.
     *
     *  @property value Gets/sets the [text], using localized formatting according to the language chosen in the settings, in both directions.
     *                  Note null is allowed and represents an empty text field.
     *  @property setText Forbidden, use [value] instead.
     *  @param hint See [UncivTextField].
     *  @param initialValue Sets the initial [value] without triggering a `ChangeEvent.`
     *  @param integerOnly Limits allowed characters and tells the parser to look for integers only.
     *  @param onFocusChange See [UncivTextField].
     */
    open class Numeric(
        hint: String,
        initialValue: Number?,
        integerOnly: Boolean = false,
        onFocusChange: (TextField.(Boolean) -> Unit)? = null
    ) : UncivTextField(hint, initialValue?.tr() ?: "", onFocusChange) {
        private val formatter = UncivGame.Current.settings.getCurrentNumberFormat()
        private val symbols = formatter.format(if (integerOnly) -9999 else -9999.9).filter { !it.isDigit() }
        init {
            textFieldFilter = TextFieldFilter { _, c -> c.isDigit() || c in symbols }
            formatter.isParseIntegerOnly = integerOnly
            maxLength = 26 // enough for signed int64 including thousands separators - floating point shouldn't need more.
        }
        open var value: Number?
            get() = try {
                formatter.parse(text)
            } catch (_: ParseException) {
                null
            }
            set(value) { super.setText(value?.tr()) }

        // Enlist compiler to make sure no-one calls this *from our project*
        @Deprecated("Don't assign `text` on a numeric UncivTextField!", ReplaceWith("value"), DeprecationLevel.ERROR)
        // But: Gdx is leaking `this` and calls this override from its constructor, therefore don't throw.
        override fun setText(str: String?) = super.setText(str)
    }

    /**
     *  Specialization of [UncivTextField.Numeric] for 32-bit integers. Do read its Kdoc.
     *  - maxLength is reduced to 14 accommodating the largest negative 32-bit integer with thousands separators.
     *  @property intValue please prefer this over [value], it's easier!
     */
    class Integer (
        hint: String,
        initialValue: Int?,
        onFocusChange: (TextField.(Boolean) -> Unit)? = null
    ) : Numeric(hint, initialValue, integerOnly = true, onFocusChange) {
        init {
            maxLength = 14
        }
        var intValue: Int?
            get() = value?.toInt()
            set(value) { this.value = value }
    }
}
