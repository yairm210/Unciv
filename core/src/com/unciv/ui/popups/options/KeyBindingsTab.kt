package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.ui.components.KeyboardBinding
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

class KeyBindingsTab(
    optionsPopup: OptionsPopup
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val keyBindings = optionsPopup.settings.keyBindings
    private val keyFields = HashMap<KeyboardBinding, TextField>(KeyboardBinding.values().size)

    init {
        pad(10f)
        defaults().pad(5f)

        for (binding in KeyboardBinding.values()) {
            keyFields[binding] = UncivTextField.create(binding.defaultKey.toString())
        }
    }

    private fun update() {
        clear()
        for (binding in KeyboardBinding.values()) {
            add(binding.label.toLabel())
            keyFields[binding]!!.text = if (binding !in keyBindings) ""  // show default = hint grayed
                else keyBindings[binding].toString()
            add(keyFields[binding]).row()
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
}
