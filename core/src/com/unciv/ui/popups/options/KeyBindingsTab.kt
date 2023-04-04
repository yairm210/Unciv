package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.KeyCapturingButton
import com.unciv.ui.components.KeyboardBinding
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
    private val keyFields = HashMap<KeyboardBinding, KeyCapturingButton>(KeyboardBinding.values().size)
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
            keyFields[binding] = KeyCapturingButton(binding.defaultKey)
        }
    }

    private fun update() {
        clear()
        add(disclaimer).colspan(2).center().row()

        for (binding in KeyboardBinding.values()) {
            if (binding.hidden) continue
            add(binding.label.toLabel())
            add(keyFields[binding]).row()
            keyFields[binding]!!.current = keyBindings[binding]
        }
    }

    fun save () {
        for (binding in KeyboardBinding.values()) {
            if (binding.hidden) continue
            keyBindings[binding] = keyFields[binding]!!.current
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        update()
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        save()
    }
}
