package com.unciv.ui.components

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** An ImageTextButton that captures keyboard keys
 *
 *  Its Label will reflect the pressed key and will be grayed if the [current] key equals [default].
 *  Note this will start with an empty label and [current] == UNKNOWN. You must set an initial value yourself if needed.
 *
 *  @param default The key seen as default (label grayed) state
 *  @param initialStyle Optionally configurable style details
 *  @param onKeyHit Fires when a key was pressed with the cursor over it
 */
class KeyCapturingButton(
    private val default: KeyCharAndCode = KeyCharAndCode.UNKNOWN,
    initialStyle: KeyCapturingButtonStyle = KeyCapturingButtonStyle(),
    private val onKeyHit: ((key: KeyCharAndCode) -> Unit)? = null
) : ImageTextButton("", initialStyle) {

    /** A subclass of [ImageTextButtonStyle][ImageTextButton.ImageTextButtonStyle] that allows setting
     *  the image parts (imageUp and imageOver only as hovering is the only interaction) via ImageGetter.
     * @param imageSize     Size for the image part
     * @param imageName     Name for the imagePart as understood by [ImageGetter.getDrawable]
     * @param imageUpTint   If not Color.CLEAR, this tints the image for its **normal** state
     * @param imageOverTint If not Color.CLEAR, this tints the image for its **hover** state
     * @param minWidth      Overrides background [NinePatchDrawable.minWidth]
     * @param minHeight     Overrides background [NinePatchDrawable.minHeight]
     */
    class KeyCapturingButtonStyle (
        val imageSize: Float = 24f,
        imageName: String = "OtherIcons/Keyboard",
        imageUpTint: Color = Color.CLEAR,
        imageOverTint: Color = Color.LIME,
        minWidth: Float = 150f,
        minHeight: Float = imageSize
    ) : ImageTextButtonStyle() {
        init {
            font = Fonts.font
            fontColor = Color.WHITE
            val image = ImageGetter.getDrawable(imageName)
            imageUp = if (imageUpTint == Color.CLEAR) image else image.tint(imageUpTint)
            imageOver = if (imageOverTint == Color.CLEAR) imageUp else image.tint(imageOverTint)
            up = BaseScreen.skinStrings.run {
                getUiBackground("General/KeyCapturingButton", roundedEdgeRectangleSmallShape, skinConfig.baseColor)
            }
            up.minWidth = minWidth
            up.minHeight = minHeight
        }
    }

    /** Gets/sets the currently assigned [KeyCharAndCode] */
    var current
        get() = currentField
        set(value) = update(value)
    private var currentField: KeyCharAndCode = KeyCharAndCode.UNKNOWN

    private var savedFocus: Actor? = null
    private val normalStyle: ImageTextButtonStyle
    private val defaultStyle: ImageTextButtonStyle

    init {
        imageCell.size((style as KeyCapturingButtonStyle).imageSize)
        imageCell.align(Align.topLeft)
        image.addTooltip("Hit the desired key now", 18f, targetAlign = Align.bottomRight)
        labelCell.expandX()
        normalStyle = style
        defaultStyle = ImageTextButtonStyle(normalStyle)
        defaultStyle.fontColor = Color.GRAY.cpy()
        addListener(ButtonListener(this))
    }

    private fun update(key: KeyCharAndCode) {
        if (key == currentField) return
        currentField = key
        label.setText(if (key == KeyCharAndCode.UNKNOWN) "" else key.toString())
        style = if (currentField == default) defaultStyle else normalStyle
    }
    private fun handleKey(code: Int, control: Boolean) {
        update(if (control) KeyCharAndCode.ctrlFromCode(code) else KeyCharAndCode(code))
        onKeyHit?.invoke(currentField)
    }

    private class ButtonListener(private val myButton: KeyCapturingButton) : ClickListener() {
        private var controlDown = false

        override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            if (myButton.stage == null) return
            myButton.savedFocus = myButton.stage.keyboardFocus
            myButton.stage.keyboardFocus = myButton
        }

        override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            if (myButton.stage == null) return
            myButton.stage.keyboardFocus = myButton.savedFocus
            myButton.savedFocus = null
        }

        override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.UNKNOWN) return false
            if (keycode == Input.Keys.CONTROL_LEFT || keycode == Input.Keys.CONTROL_RIGHT) {
                controlDown = true
            } else {
                myButton.handleKey(keycode, controlDown)
            }
            return true
        }

        override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.UNKNOWN) return false
            if (keycode == Input.Keys.CONTROL_LEFT || keycode == Input.Keys.CONTROL_RIGHT)
                controlDown = false
            return true
        }
    }
}
