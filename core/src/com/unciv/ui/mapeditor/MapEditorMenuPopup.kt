package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.newgamescreen.ModCheckboxTable
import com.unciv.ui.utils.*

class MapEditorMenuPopup(var mapEditorScreen: MapEditorScreen): Popup(mapEditorScreen) {

    init {
        addButton("New map") { UncivGame.Current.setScreen(NewMapScreen(mapEditorScreen.tileMap.mapParameters)) }
        addButton("Save map") { mapEditorScreen.game.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap, true)) }
        addButton("Load map") { mapEditorScreen.game.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap)) }
        addButton("Exit map editor") { mapEditorScreen.game.setScreen(MainMenuScreen()); mapEditorScreen.dispose() }
        addButton("Change ruleset") { MapEditorRulesetPopup(mapEditorScreen).open(); close() }
        addButton(Constants.close) { close() }
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

            add(ScrollPane(checkboxTable)).maxHeight(mapEditorScreen.stage.height * 0.8f).row()

            addButton("Save") {
                val incompatibilities = mapEditorScreen.tileMap.values.map { it.getRulesetIncompatability(ruleset) }.toHashSet()
                incompatibilities.remove("")

                if (incompatibilities.isEmpty()) {
                    mapEditorScreen.tileMap.mapParameters.mods = mods
                    mapEditorScreen.game.setScreen(MapEditorScreen(mapEditorScreen.tileMap)) // reset all images etc.
                    return@addButton
                }

                val incompatTable = Table()
                for (inc in incompatibilities)
                    incompatTable.add(inc.toLabel()).row()
                val incompatPopup = Popup(screen)
                incompatPopup.add(ScrollPane(incompatTable)).maxHeight(stage.height * 0.8f).row()
                incompatPopup.add("Change map to fit selected ruleset?").row()
                incompatPopup.addButton("Yes") {
                    for (tile in mapEditorScreen.tileMap.values)
                        tile.normalizeToRuleset(ruleset)
                    mapEditorScreen.tileMap.mapParameters.mods = mods
                    mapEditorScreen.game.setScreen(MapEditorScreen(mapEditorScreen.tileMap))
                }
                incompatPopup.addButton("No") { incompatPopup.close() }
                incompatPopup.open(true)

            }

            // Reset - no changes
            addCloseButton { ImageGetter.setNewRuleset(mapEditorScreen.ruleset) }
        }
    }

}