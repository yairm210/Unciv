package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.KeyCapturingButton
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.KeyboardBinding
import com.unciv.ui.components.KeyboardBindings
import com.unciv.ui.components.KeysSelectBox
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.toLabel
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
            if (binding.hidden) continue
            keyFields[binding] = KeyboardBindingWidget(binding)
        }
    }

    private fun update() {
        clear()
        add(disclaimer).colspan(2).center().row()

        for (binding in KeyboardBinding.values()) {
            if (binding.hidden) continue
            add(binding.label.toLabel())
            add(keyFields[binding]).row()
            keyFields[binding]!!.update(keyBindings)
        }
    }

    fun save () {
        for (binding in KeyboardBinding.values()) {
            if (binding.hidden) continue
            keyBindings.put(binding, keyFields[binding]!!.text)
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        update()
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        save()
    }

    class KeyboardBindingWidget(
        /** The specific binding to edit */
        private val binding: KeyboardBinding
    ) : Table(BaseScreen.skin) {
        private val selectBox = KeysSelectBox(binding.defaultKey.toString()) {
            validateSelection()
        }

        private val button = KeyCapturingButton { code, control ->
            selectBox.hideScrollPane()
            boundKey = if (control)
                KeyCharAndCode.ctrlFromCode(code)
                else KeyCharAndCode(code)
            resetSelection()
        }

        private var boundKey: KeyCharAndCode = binding.defaultKey

        init {
            pad(0f)
            defaults().pad(0f)
            add(selectBox)
            add(button).size(36f).padLeft(2f)
        }

        /** Get the (untranslated) key name selected by the Widget */
        // we let the KeysSelectBox handle undesired mappings
        val text: String
            get() = selectBox.selected.name

        /** Update control to show current binding */
        fun update(keyBindings: KeyboardBindings) {
            boundKey = keyBindings[binding]
            resetSelection()
        }

        /** Set boundKey from selectBox */
        private fun validateSelection() {
            val value = text
            val parsedKey = KeyCharAndCode.parse(value)
            if (parsedKey == KeyCharAndCode.UNKNOWN) {
                resetSelection()
            } else {
                boundKey = parsedKey
            }
        }

        /** Set selectBox from boundKey */
        private fun resetSelection() {
            selectBox.setSelected(boundKey.toString())
        }
    }
}
