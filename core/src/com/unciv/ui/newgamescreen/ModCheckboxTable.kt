package com.unciv.ui.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class ModCheckboxTable(
    private val mods: LinkedHashSet<String>,
    private var baseRuleset: String,
    private val screen: CameraStageBaseScreen,
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
                    //todo: persist ExpanderTab states here
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
