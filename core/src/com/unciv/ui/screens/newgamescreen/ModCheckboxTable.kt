package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.input.onChange
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * A widget containing one expander for extension mods.
 * Manages compatibility checks, warns or prevents incompatibilities.
 *
 * @param mods In/out set of active mods, modified in place
 * @param baseRuleset The selected base Ruleset, only for running mod checks against. Use [setBaseRuleset] to change on the fly.
 * @param screen Parent screen, used only to show [ToastPopup]s
 * @param isPortrait Used only for minor layout tweaks, arrangement is always vertical
 * @param onUpdate Callback, parameter is the mod name, called after any checks that may prevent mod selection succeed.
 */
class ModCheckboxTable(
    private val mods: LinkedHashSet<String>,
    private var baseRuleset: String,
    private val screen: BaseScreen,
    isPortrait: Boolean = false,
    private val onUpdate: (String) -> Unit
): Table() {
    private val extensionRulesetModButtons = ArrayList<CheckBox>()

    /** Saved result from any complex mod check unless the causing selection has already been reverted.
     *  In other words, this can contain the text for an "Error" level check only if the Widget was
     *  initialized with such an invalid mod combination.
     *  This Widget reverts User changes that cause an Error severity immediately and this field is nulled.
     */
    var savedModcheckResult: String? = null

    private var disableChangeEvents = false

    init {
        val modRulesets = RulesetCache.values.filter { it.name != "" && !it.modOptions.isBaseRuleset}
        for (mod in modRulesets.sortedBy { it.name }) {
            val checkBox = mod.name.toCheckBox(mod.name in mods)
            checkBox.onChange {
                if (checkBoxChanged(checkBox, it!!, mod)) {
                    onUpdate(mod.name)
                }
            }
            extensionRulesetModButtons.add(checkBox)
        }

        val padTop = if (isPortrait) 0f else 16f

        if (extensionRulesetModButtons.any()) {
            add(ExpanderTab("Extension mods", persistenceID = "NewGameExpansionMods") {
                it.defaults().pad(5f,0f)
                for (checkbox in extensionRulesetModButtons) {
                    checkbox.left()
                    it.add(checkbox).row()
                }
            }).pad(10f).padTop(padTop).growX().row()

            runComplexModCheck()
        }
    }

    fun setBaseRuleset(newBaseRuleset: String) { baseRuleset = newBaseRuleset }
    fun disableAllCheckboxes() {
        disableChangeEvents = true
        for (checkBox in extensionRulesetModButtons) {
            checkBox.isChecked = false
        }
        mods.clear()
        disableChangeEvents = false
        onUpdate("-")  // should match no mod
    }

    private fun runComplexModCheck(): Boolean {
        // Check over complete combination of selected mods
        val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods, baseRuleset)
        if (!complexModLinkCheck.isWarnUser()) return false
        savedModcheckResult = complexModLinkCheck.getErrorText()
        complexModLinkCheck.showWarnOrErrorToast(screen)
        return complexModLinkCheck.isError()
    }

    private fun checkBoxChanged(
        checkBox: CheckBox,
        changeEvent: ChangeListener.ChangeEvent,
        mod: Ruleset
    ): Boolean {
        if (disableChangeEvents) return false

        if (checkBox.isChecked) {
            // First the quick standalone check
            val modLinkErrors = mod.checkModLinks()
            if (modLinkErrors.isError()) {
                modLinkErrors.showWarnOrErrorToast(screen)
                changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                return false
            }

            mods.add(mod.name)

            // Check over complete combination of selected mods
            if (runComplexModCheck()) {
                changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                mods.remove(mod.name)
                savedModcheckResult = null  // we just fixed it
                return false
            }

        } else {
            /**
             * Turns out we need to check ruleset when REMOVING a mod as well, since if mod A references something in mod B (like a promotion),
             *   and then we remove mod B, then the entire ruleset is now broken!
             */

            mods.remove(mod.name)

            if (runComplexModCheck()) {
                changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                mods.add(mod.name)
                savedModcheckResult = null  // we just fixed it
                return false
            }

        }

        return true
    }
}
