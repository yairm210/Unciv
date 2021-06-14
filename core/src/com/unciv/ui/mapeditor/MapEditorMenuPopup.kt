package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.newgamescreen.ModCheckboxTable
import com.unciv.ui.utils.*

class MapEditorMenuPopup(var mapEditorScreen: MapEditorScreen): Popup(mapEditorScreen) {

    init {
        defaults().fillX()
        addButton("New map", 'n') { UncivGame.Current.setScreen(NewMapScreen(mapEditorScreen.tileMap.mapParameters)) }
        addButton("Save map", 's') { mapEditorScreen.game.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap, true, mapEditorScreen)); this.close() }
        addButton("Load map", 'l') { mapEditorScreen.game.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap, false, mapEditorScreen)); this.close() }
        addButton("Exit map editor", 'x') { mapEditorScreen.game.setScreen(MainMenuScreen()); mapEditorScreen.dispose() }
        addButton("Change ruleset", 'c') { MapEditorRulesetPopup(mapEditorScreen).open(); close() }
        addCloseButton()
    }

    class MapEditorRulesetPopup(mapEditorScreen: MapEditorScreen) : Popup(mapEditorScreen) {
        var ruleset = mapEditorScreen.ruleset.clone() // don't take the actual one, so w can decide to not make changes

        init {
            val mods = mapEditorScreen.tileMap.mapParameters.mods

            val checkboxTable = ModCheckboxTable(mods, mapEditorScreen) {
                ruleset.clear()
                val newRuleset = RulesetCache.getComplexRuleset(mods)
                ruleset.add(newRuleset)
                ruleset.mods += mods
                ruleset.modOptions = newRuleset.modOptions

                ImageGetter.setNewRuleset(ruleset)
            }

            add(ScrollPane(checkboxTable)).maxHeight(mapEditorScreen.stage.height * 0.8f).colspan(2).row()

            addButtonInRow("Save", KeyCharAndCode.RETURN) {
                val incompatibilities = HashSet<String>()
                for (set in mapEditorScreen.tileMap.values.map { it.getRulesetIncompatibility(ruleset) })
                    incompatibilities.addAll(set)
                incompatibilities.remove("")

                if (incompatibilities.isEmpty()) {
                    mapEditorScreen.tileMap.mapParameters.mods = mods
                    mapEditorScreen.game.setScreen(MapEditorScreen(mapEditorScreen.tileMap)) // reset all images etc.
                    return@addButtonInRow
                }

                val incompatibilityTable = Table()
                for (inc in incompatibilities)
                    incompatibilityTable.add(inc.toLabel()).row()
                Popup(screen).apply {
                    add(ScrollPane(incompatibilityTable)).colspan(2)
                        .maxHeight(screen.stage.height * 0.8f).row()
                    add("Change map to fit selected ruleset?".toLabel()).colspan(2).row()
                    addButtonInRow("Yes", 'y') {
                        for (tile in mapEditorScreen.tileMap.values)
                            tile.normalizeToRuleset(ruleset)
                        mapEditorScreen.tileMap.mapParameters.mods = mods
                        mapEditorScreen.game.setScreen(MapEditorScreen(mapEditorScreen.tileMap))
                    }
                    addButtonInRow("No", 'n') { close() }
                }.open(true)
            }

            // Reset - no changes
            addCloseButton { ImageGetter.setNewRuleset(mapEditorScreen.ruleset) }
        }
    }

}
