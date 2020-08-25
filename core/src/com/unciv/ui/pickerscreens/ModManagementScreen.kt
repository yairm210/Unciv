package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.unciv.MainMenuScreen
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class ModManagementScreen: PickerScreen() {

    val modTable = Table().apply { defaults().pad(10f) }

    init {
        setDefaultCloseAction(MainMenuScreen())
        refresh()

        topTable.add(modTable)
    }

    fun refresh(){
        modTable.clear()
        val currentMods = RulesetCache.values.filter { it.name != "" }
        for (mod in currentMods) {
            val button = mod.name.toTextButton().onClick {
                rightSideButton.setText("Delete [${mod.name}]".tr())
                rightSideButton.enable()
                descriptionLabel.setText(mod.getSummary())
                rightSideButton.listeners.filter { it != rightSideButton.clickListener }
                        .forEach { rightSideButton.removeListener(it) }
                rightSideButton.onClick {
                    YesNoPopup("Are you SURE you want to delete this mod?",
                            { deleteMod(mod) }, this).open()
                }
            }
            modTable.add(button).row()
        }
    }

    fun deleteMod(mod: Ruleset){
        val modFileHandle = Gdx.files.local("mods").child(mod.name)
        if(modFileHandle.isDirectory) modFileHandle.deleteDirectory()
        else modFileHandle.delete()
        RulesetCache.loadRulesets()
        refresh()
    }
}