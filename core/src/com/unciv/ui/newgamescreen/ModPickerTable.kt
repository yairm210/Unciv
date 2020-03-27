package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

/** Table for editing [mods]
 *
 *  This is a separate class, because it should be in use both in the New Game screen and the Map Editor screen
 *
 *  @param isForMapEditor whether mods with empty terrain and resources tables should be shown
 * */
class ModPickerTable(var modList: HashSet<String>, val updatePlayerPickerTable:((desiredCiv:String)->Unit)?, val isForMapEditor: Boolean = false):
    Table(CameraStageBaseScreen.skin) {

    val ruleset = RulesetCache.getComplexRuleset(modList)

    init {

        fun reloadMods(){
            ruleset.clear()
            ruleset.add(RulesetCache.getComplexRuleset(modList))
            ruleset.mods += modList

            ImageGetter.ruleset=ruleset
            ImageGetter.setTextureRegionDrawables()
        }

        val modRulesets = RulesetCache.filter {
                it.key!="" && (!isForMapEditor || it.value.containsMapEditorObjects())
            }.values

        if (modRulesets.isNotEmpty()) {
            defaults().pad(5f)
            add("{Mods}:".tr().toLabel(fontSize = 24)).padTop(16f).colspan(2).row()
            val modCheckboxTable = Table().apply { defaults().pad(5f) }
            for (mod in modRulesets) {
                val checkBox = CheckBox(mod.name, CameraStageBaseScreen.skin)
                if (mod.name in modList) checkBox.isChecked = true
                checkBox.addListener(object : ChangeListener() {
                    override fun changed(event: ChangeEvent?, actor: Actor?) {
                        if (checkBox.isChecked) modList.add(mod.name)
                        else modList.remove(mod.name)
                        reloadMods()
                        var desiredCiv = ""
                        if (checkBox.isChecked) {
                            val modNations = RulesetCache[mod.name]?.nations
                            if (modNations != null && modNations.size > 0) {
                                desiredCiv = modNations.keys.first()
                            }
                        }
                        if (desiredCiv.isNotEmpty()) updatePlayerPickerTable?.invoke(desiredCiv)
                    }
                })
                modCheckboxTable.add(checkBox).row()
            }
            add(modCheckboxTable).colspan(2).row()
        }
    }
}