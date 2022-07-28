package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.toCheckBox

/**
 * A widget containing one expander for extension mods.
 * Manages compatibility checks, warns or prevents incompatibilities.
 *
 * @param mods In/out set of active mods, modified in place
 * @param baseRuleset The selected base Ruleset //todo clarify
 * @param screen Parent screen, used only to show [ToastPopup]s
 * @param isPortrait Used only for minor layout tweaks, arrangement is always vertical
 * @param onUpdate Callback, parameter is the mod name, called after any checks that may prevent mod selection succeed.
 */
class ModCheckboxTable(
    private val mods: LinkedHashSet<String>,
    private var baseRuleset: String,
    private val screen: BaseScreen,
    isPortrait: Boolean = false,
    onUpdate: (String) -> Unit
): Table(){
    private val modRulesets = RulesetCache.values.filter { it.name != "" && !it.modOptions.isBaseRuleset}
    private var lastToast: ToastPopup? = null
    private val extensionRulesetModButtons = ArrayList<CheckBox>()

    init {

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
            add(ExpanderTab("Extension mods:", persistenceID = "NewGameExpansionMods") {
                it.defaults().pad(5f,0f)
                for (checkbox in extensionRulesetModButtons) it.add(checkbox).row()
            }).padTop(padTop).growX().row()
        }
    }

    fun setBaseRuleset(newBaseRuleset: String) { baseRuleset = newBaseRuleset }
    fun disableAllCheckboxes() {
        for (checkBox in extensionRulesetModButtons) {
            checkBox.isChecked = false
        }
    }


    private fun popupToastError(rulesetErrorList:Ruleset.RulesetErrorList) {
        val initialText =
            if (rulesetErrorList.isError()) "The mod combination you selected is incorrectly defined!".tr()
            else "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}".tr()
        val toastMessage =  "$initialText\n\n${rulesetErrorList.getErrorText()}"

        lastToast?.close()
        lastToast = ToastPopup(toastMessage, screen, 5000L)
    }

    private fun checkBoxChanged(
        checkBox: CheckBox,
        changeEvent: ChangeListener.ChangeEvent,
        mod: Ruleset
    ): Boolean {
        if (checkBox.isChecked) {
            // First the quick standalone check
            val modLinkErrors = mod.checkModLinks()
            if (modLinkErrors.isError()) {
                lastToast?.close()
                val toastMessage =
                    "The mod you selected is incorrectly defined!".tr() + "\n\n${modLinkErrors.getErrorText()}"
                lastToast = ToastPopup(toastMessage, screen, 5000L)
                changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                return false
            }

            mods.add(mod.name)

            // Check over complete combination of selected mods
            val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods, baseRuleset)
            if (complexModLinkCheck.isWarnUser()) {
                popupToastError(complexModLinkCheck)
                if (complexModLinkCheck.isError()) {
                    changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                    mods.remove(mod.name)
                    return false
                }
            }

        } else {
            /**
             * Turns out we need to check ruleset when REMOVING a mod as well, since if mod A references something in mod B (like a promotion),
             *   and then we remove mod B, then the entire ruleset is now broken!
             */

            mods.remove(mod.name)
            val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods, baseRuleset)
            if (complexModLinkCheck.isWarnUser()) {
                popupToastError(complexModLinkCheck)
                if (complexModLinkCheck.isError()) {
                    changeEvent.cancel() // Cancel event to reset to previous state - see Button.setChecked()
                    mods.add(mod.name)
                    return false
                }
            }

        }

        return true
    }
}
