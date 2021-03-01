package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ToastPopup
import com.unciv.ui.utils.onChange
import com.unciv.ui.utils.toLabel

class ModCheckboxTable(val mods:LinkedHashSet<String>, val screen: CameraStageBaseScreen, onUpdate: (String) -> Unit): Table(){
    init {
        val modRulesets = RulesetCache.values.filter { it.name != "" }

        val baseRulesetCheckboxes = ArrayList<CheckBox>()
        val extentionRulesetModButtons = ArrayList<CheckBox>()

        for (mod in modRulesets) {
            val checkBox = CheckBox(mod.name.tr(), CameraStageBaseScreen.skin)
            if (mod.name in mods) checkBox.isChecked = true
            checkBox.onChange {
                if (checkBox.isChecked) {
                    val modLinkErrors = mod.checkModLinks()
                    if (modLinkErrors != "") {
                        ToastPopup("The mod you selected is incorrectly defined!\n\n$modLinkErrors", screen)
                        checkBox.isChecked = false
                        return@onChange
                    }

                    val previousMods = mods.toList()

                    if (mod.modOptions.isBaseRuleset)
                        for (oldBaseRuleset in previousMods) // so we don't get concurrent modification excpetions
                            if (modRulesets.firstOrNull { it.name == oldBaseRuleset }?.modOptions?.isBaseRuleset == true)
                                mods.remove(oldBaseRuleset)
                    mods.add(mod.name)

                    var isCompatibleWithCurrentRuleset = true
                    var complexModLinkErrors = ""
                    try {
                        val newRuleset = RulesetCache.getComplexRuleset(mods)
                        newRuleset.modOptions.isBaseRuleset = true // This is so the checkModLinks finds all connections
                        complexModLinkErrors = newRuleset.checkModLinks()
                        if (complexModLinkErrors != "") isCompatibleWithCurrentRuleset = false
                    } catch (x: Exception) {
                        // This happens if a building is dependent on a tech not in the base ruleset
                        //  because newRuleset.updateBuildingCosts() in getComplexRulset() throws an error
                        isCompatibleWithCurrentRuleset = false
                    }

                    if (!isCompatibleWithCurrentRuleset) {
                        ToastPopup("The mod you selected is incompatible with the defined ruleset!\n\n$complexModLinkErrors", screen)
                        checkBox.isChecked = false
                        mods.clear()
                        mods.addAll(previousMods)
                        return@onChange
                    }

                } else {
                    mods.remove(mod.name)
                }

                onUpdate(mod.name)

            }
            if (mod.modOptions.isBaseRuleset) baseRulesetCheckboxes.add(checkBox)
            else extentionRulesetModButtons.add(checkBox)
        }

        if (baseRulesetCheckboxes.any()) {
            add("Base ruleset mods:".toLabel(fontSize = 24)).padTop(16f).colspan(2).row()
            val modCheckboxTable = Table().apply { defaults().pad(5f) }
            for (checkbox in baseRulesetCheckboxes) modCheckboxTable.add(checkbox).row()
            add(modCheckboxTable).colspan(2).row()
        }


        if (extentionRulesetModButtons.any()) {
            add("Extension mods:".toLabel(fontSize = 24)).padTop(16f).colspan(2).row()
            val modCheckboxTable = Table().apply { defaults().pad(5f) }
            for (checkbox in extentionRulesetModButtons) modCheckboxTable.add(checkbox).row()
            add(modCheckboxTable).colspan(2).row()
        }

    }
}