package com.unciv.ui.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Ruleset.CheckModLinksResult
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class ModCheckboxTable(
    private val mods:LinkedHashSet<String>,
    private val screen: CameraStageBaseScreen,
    isPortrait: Boolean = false,
    onUpdate: (String) -> Unit
): Table(){
    private val modRulesets = RulesetCache.values.filter { it.name != "" }
    private val baseRulesetCheckboxes = ArrayList<CheckBox>()
    private var lastToast: ToastPopup? = null

    init {
        val extensionRulesetModButtons = ArrayList<CheckBox>()

        for (mod in modRulesets.sortedBy { it.name }) {
            val checkBox = mod.name.toCheckBox(mod.name in mods)
            checkBox.onChange {
                if (checkBoxChanged(checkBox, mod)) {
                    //todo: persist ExpanderTab states here
                    onUpdate(mod.name)
                }
            }
            if (mod.modOptions.isBaseRuleset) baseRulesetCheckboxes.add(checkBox)
            else extensionRulesetModButtons.add(checkBox)
        }

        val padTop = if (isPortrait) 0f else 16f

        if (baseRulesetCheckboxes.any()) {
            add(ExpanderTab("Base ruleset mods:", persistenceID = "NewGameBaseMods") {
                it.defaults().pad(5f,0f)
                for (checkbox in baseRulesetCheckboxes) it.add(checkbox).row()
            }).padTop(padTop).growX().row()
        }

        if (isPortrait && baseRulesetCheckboxes.any() && extensionRulesetModButtons.any())
            addSeparator(Color.DARK_GRAY, height = 1f)

        if (extensionRulesetModButtons.any()) {
            add(ExpanderTab("Extension mods:", persistenceID = "NewGameExpansionMods") {
                it.defaults().pad(5f,0f)
                for (checkbox in extensionRulesetModButtons) it.add(checkbox).row()
            }).padTop(padTop).growX().row()
        }
    }

    private fun checkBoxChanged(checkBox: CheckBox, mod: Ruleset): Boolean {
        if (checkBox.isChecked) {
            // First the quick standalone check
            val modLinkErrors = mod.checkModLinks()
            if (modLinkErrors.isError()) {
                lastToast?.close()
                val toastMessage =
                    "The mod you selected is incorrectly defined!".tr() + "\n\n$modLinkErrors"
                lastToast = ToastPopup(toastMessage, screen, 5000L)
                if (modLinkErrors.isError()) {
                    checkBox.isChecked = false
                    return false
                }
            }

            // Save selection for a rollback
            val previousMods = mods.toList()

            // Ensure only one base can be selected
            if (mod.modOptions.isBaseRuleset) {
                for (oldBaseRuleset in previousMods) // so we don't get concurrent modification exceptions
                    if (RulesetCache[oldBaseRuleset]?.modOptions?.isBaseRuleset == true)
                        mods.remove(oldBaseRuleset)
                baseRulesetCheckboxes.filter { it != checkBox }.forEach { it.isChecked = false }
            }
            mods.add(mod.name)

            // Check over complete combination of selected mods
            val complexModLinkCheck = RulesetCache.checkCombinedModLinks(mods)
            if (complexModLinkCheck.isNotOK()) {
                lastToast?.close()
                val toastMessage = (
                        if (complexModLinkCheck.isError()) "The mod combination you selected is incorrectly defined!"
                        else "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}"
                        ).tr() + "\n\n$complexModLinkCheck"
                lastToast = ToastPopup(toastMessage, screen, 5000L)

                if (complexModLinkCheck.isError()) {
                    checkBox.isChecked = false
                    mods.clear()
                    mods.addAll(previousMods)
                    return false
                }
            }

        } else {
            mods.remove(mod.name)
        }

        return true
    }
}
