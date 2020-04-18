package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onChange
import com.unciv.ui.utils.toLabel

/** Table for editing [mods]
 *
 *  This is a separate class, because it should be in use both in the New Game screen and the Map Editor screen
 *
 *  @param  modList:            the set of selected mods, input / output (remember, reference passed by value)
 *  @param  notifyChangedMods:  callback when mods have changed, used by new game but not map editor new map
 *          @param  modList:    For convenience the modlist being edited right back
 *          @param  activatedMod:   Which mod was checked or "" if one was unchecked
 * */
class ModPickerTable(var modList: LinkedHashSet<String>, val notifyChangedMods:((modList: LinkedHashSet<String>, activatedMod:String)->Unit)?):
    Table(CameraStageBaseScreen.skin) {

    val ruleset = RulesetCache.getComplexRuleset(modList)

    init {

        val modRulesets = RulesetCache.filter {
                it.key!="" && (notifyChangedMods!=null || it.value.containsMapEditorObjects())
            }.values

        if (modRulesets.isNotEmpty()) {
            defaults().pad(5f)
            add("Mods:".toLabel(fontSize = 24)).padTop(16f).colspan(2).row()
            val modCheckboxTable = Table().apply { defaults().pad(5f) }
            for (mod in modRulesets) {
                val checkBox = CheckBox(mod.name, CameraStageBaseScreen.skin)
                if (mod.name in modList) checkBox.isChecked = true
                checkBox.onChange {
                    if (checkBox.isChecked) modList.add(mod.name)
                    else modList.remove(mod.name)
                    notifyChangedMods?.invoke(modList, if (checkBox.isChecked) mod.name else "")
                }
                modCheckboxTable.add(checkBox).row()
            }
            add(modCheckboxTable).colspan(2).row()
        }
    }

    fun updateLockedMods(lockMods: HashSet<String>) {
        this.children.filter { it is Table }.forEach {
            (it as Table).cells.forEach {
                val actor = it.actor as? CheckBox
                if (actor!=null && actor.toString().startsWith("CheckBox: ")) {
                    if (lockMods.contains(actor.toString().drop(10))) {
                        actor.isChecked = true
                        actor.isDisabled = true
                    } else {
                        actor.isDisabled = false
                    }
                }
            }
        }
    }
}