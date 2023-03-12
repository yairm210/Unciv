package com.unciv.ui.popups.options

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.KeyboardBinding
import com.unciv.ui.components.KeyboardBindings
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

class KeyBindingsTab(
    optionsPopup: OptionsPopup,
    labelWidth: Float
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val keyBindings = optionsPopup.settings.keyBindings
    private val keyFields = HashMap<KeyboardBinding, KeyboardBindingWidget>(KeyboardBinding.values().size)
    private val disclaimer = MarkupRenderer.render(listOf(
        FormattedLine("This is a work in progress.", color = "#b22222", centered = true),  // FIREBRICK
        FormattedLine(),
        // FormattedLine("Do not pester the developers for missing entries!"),  // little joke
        FormattedLine("Please see the Tutorial.", link = "Tutorial/Keyboard Bindings"),
        FormattedLine(separator = true),
    ), labelWidth) {
        // This ruleset is a kludge - but since OptionPopup can be called from anywhere, getting the relevant one is a chore
        //TODO better pedia call architecture, or a tutorial render method once that has markup capability
        GUI.pushScreen(CivilopediaScreen(RulesetCache.getVanillaRuleset(), link = it))
    }

    init {
        pad(10f)
        defaults().pad(5f)

        for (binding in KeyboardBinding.values()) {
            keyFields[binding] = KeyboardBindingWidget(binding)
        }
    }

    private fun update() {
        clear()
        add(disclaimer).colspan(2).center().row()

        for (binding in KeyboardBinding.values()) {
            add(binding.label.toLabel())
            add(keyFields[binding]).row()
            keyFields[binding]!!.update(keyBindings)
        }
    }

    fun save () {
        for (binding in KeyboardBinding.values()) {
            keyBindings.put(binding, keyFields[binding]!!.text)
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        update()
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        save()
    }

    /** A button that captures keyboard keys and reports them through [onKeyHit] */
    class KeyCapturingButton(
        private val onKeyHit: (keyCode: Int, control: Boolean) -> Unit
    ) : ImageButton(getStyle()) {
        companion object {
            private const val buttonSize = 36f
            private const val buttonImage = "OtherIcons/Keyboard"
            private val controlKeys = setOf(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT)

            private fun getStyle() = ImageButtonStyle().apply {
                val image = ImageGetter.getDrawable(buttonImage)
                imageUp = image
                imageOver = image.tint(Color.LIME)
            }
        }

        private var savedFocus: Actor? = null

        init {
            setSize(buttonSize, buttonSize)
            addTooltip("Hit the desired key now", 18f, targetAlign = Align.bottomRight)
            addListener(ButtonListener(this))
        }

        class ButtonListener(private val myButton: KeyCapturingButton) : ClickListener() {
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
                if (keycode == Input.Keys.ESCAPE) return false
                if (keycode in controlKeys) {
                    controlDown = true
                } else {
                    myButton.onKeyHit(keycode, controlDown)
                }
                return true
            }

            override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
                if (keycode == Input.Keys.ESCAPE) return false
                if (keycode in controlKeys)
                    controlDown = false
                return true
            }
        }
    }

    class KeyboardBindingWidget(
        /** The specific binding to edit */
        private val binding: KeyboardBinding
    ) : Table(BaseScreen.skin) {
        private val textField: TextField =
                UncivTextField.create(binding.defaultKey.toString()) { focused ->
                    if (!focused) validateText()
                }

        private val button = KeyCapturingButton { code, control ->
            boundKey = if (control)
                KeyCharAndCode.ctrlFromCode(code)
                else KeyCharAndCode(code)
            resetText()
        }

        private var boundKey: KeyCharAndCode? = null

        init {
            pad(0f)
            defaults().pad(0f)
            textField.setScale(0.1f)
            add(textField)
            addActor(button)
        }

        val text: String
            get() = textField.text

        fun update(keyBindings: KeyboardBindings) {
            boundKey = keyBindings[binding]
            resetText()

            // Since the TextField itself is temporary, this is only quick & dirty
            button.setPosition(textField.width - (textField.height - button.height) / 2, textField.height / 2, Align.right)
        }

        private fun validateText() {
            val value = text
            val parsedKey = KeyCharAndCode.parse(value)
            if (parsedKey == KeyCharAndCode.UNKNOWN) {
                resetText()
            } else {
                boundKey = parsedKey
            }
        }

        private fun resetText() {
            if (boundKey == binding.defaultKey) boundKey = null
            textField.text = boundKey?.toString() ?: ""
        }
    }
}
