package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

/**
 * A widget containing two expanders, one for base mods, one for extension mods.
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
                if (checkBoxChanged(checkBox, mod)) {
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

    private fun checkBoxChanged(checkBox: CheckBox, mod: Ruleset): Boolean {
        if (checkBox.isChecked) {
            // First the quick standalone check
            val modLinkErrors = mod.checkModLinks()
            if (modLinkErrors.isError()) {
                lastToast?.close()
                val toastMessage =
                    "The mod you selected is incorrectly defined!".tr() + "\n\n${modLinkErrors.getErrorText()}"
                lastToast = ToastPopup(toastMessage, screen, 5000L)
                checkBox.isChecked = false
                return false
            }

            mods.add(mod.name)

            // Check over complete combination of selected mods
            val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods, baseRuleset)
            if (complexModLinkCheck.isWarnUser()) {
                lastToast?.close()
                val toastMessage = (
                    if (complexModLinkCheck.isError()) "The mod combination you selected is incorrectly defined!".tr()
                    else "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}".tr()
                ) + 
                    "\n\n${complexModLinkCheck.getErrorText()}"
                
                lastToast = ToastPopup(toastMessage, screen, 5000L)

                if (complexModLinkCheck.isError()) {
                    checkBox.isChecked = false
                    return false
                }
            }

        } else {
            mods.remove(mod.name)
        }

        return true
    }
}
