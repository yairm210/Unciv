package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.newgamescreen.ModCheckboxTable
import com.unciv.ui.newgamescreen.TranslatedSelectBox
import com.unciv.ui.utils.*
import kotlin.math.max

class MapEditorMenuPopup(var mapEditorScreen: MapEditorScreen): Popup(mapEditorScreen) {

    init {
        defaults().fillX()
        add(("{RNG Seed} " + mapEditorScreen.tileMap.mapParameters.seed.toString()).toLabel()).row()
        addButton("Copy to clipboard") { Gdx.app.clipboard.contents = mapEditorScreen.tileMap.mapParameters.seed.toString() }
        addSeparator()
        addButton("New map", 'n') {
            mapEditorScreen.tileMap.mapParameters.reseed()
            UncivGame.Current.setScreen(NewMapScreen(mapEditorScreen.tileMap.mapParameters))
        }
        addButton("Save map", 's') { mapEditorScreen.game.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap, true, mapEditorScreen)); close() }
        addButton("Load map", 'l') { mapEditorScreen.game.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap, false, mapEditorScreen)); close() }
        addButton("Exit map editor", 'x') { mapEditorScreen.game.setScreen(MainMenuScreen()); mapEditorScreen.dispose() }
        addButton("Change ruleset", 'c') { MapEditorRulesetPopup(mapEditorScreen).open(); close() }
        addCloseButton()
    }

    class MapEditorRulesetPopup(val mapEditorScreen: MapEditorScreen) : Popup(mapEditorScreen) {
        var ruleset = mapEditorScreen.ruleset.clone() // don't take the actual one, so we can decide to not make changes
        val checkboxTable: ModCheckboxTable
        val mapParameters = mapEditorScreen.tileMap.mapParameters

        init {
            val mods = mapParameters.mods
            val baseRuleset = mapParameters.baseRuleset
            
            checkboxTable = ModCheckboxTable(mods, baseRuleset, mapEditorScreen) {
                ruleset.clear()
                val newRuleset = RulesetCache.getComplexRuleset(mods, baseRuleset)
                ruleset.add(newRuleset)
                ruleset.mods += mods
                ruleset.modOptions = newRuleset.modOptions

                ImageGetter.setNewRuleset(ruleset)
            }
            
            val combinedTable = Table(BaseScreen.skin)
            
            val baseRulesetSelectionBox = getBaseRulesetSelectBox()
            if (baseRulesetSelectionBox != null) {
                // TODO: For some reason I'm unable to get these two tables to be equally wide
                // someone who knows what they're doing should fix this
                val maxWidth = max(baseRulesetSelectionBox.minWidth, checkboxTable.minWidth)
                baseRulesetSelectionBox.width = maxWidth
                checkboxTable.width = maxWidth
                combinedTable.add(baseRulesetSelectionBox).row()
            }
            
            combinedTable.add(checkboxTable)
            
            add(ScrollPane(combinedTable)).maxHeight(mapEditorScreen.stage.height * 0.8f).colspan(2).row()

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
                    addButtonInRow(Constants.yes, 'y') {
                        for (tile in mapEditorScreen.tileMap.values)
                            tile.normalizeToRuleset(ruleset)
                        mapEditorScreen.tileMap.mapParameters.mods = mods
                        mapEditorScreen.game.setScreen(MapEditorScreen(mapEditorScreen.tileMap))
                    }
                    addButtonInRow(Constants.no, 'n') { close() }
                    equalizeLastTwoButtonWidths()
                }.open(true)
            }

            // Reset - no changes
            addCloseButton { ImageGetter.setNewRuleset(mapEditorScreen.ruleset) }
        }

        private fun getBaseRulesetSelectBox(): Table? {
            val rulesetSelectionBox = Table()

            val sortedBaseRulesets = RulesetCache.getSortedBaseRulesets()
            if (sortedBaseRulesets.size < 2) return null

            rulesetSelectionBox.add("{Base Ruleset}:".toLabel()).left()
            val selectBox = TranslatedSelectBox(sortedBaseRulesets, mapParameters.baseRuleset, BaseScreen.skin)

            val onChange = onChange@{ newBaseRuleset: String ->
                val previousSelection = mapParameters.baseRuleset
                if (newBaseRuleset == previousSelection) return@onChange null

                // Check if this mod is well-defined
                val baseRulesetErrors = RulesetCache[newBaseRuleset]!!.checkModLinks()
                if (baseRulesetErrors.isError()) {
                    val toastMessage = "The mod you selected is incorrectly defined!".tr() + "\n\n${baseRulesetErrors.getErrorText()}"
                    ToastPopup(toastMessage, mapEditorScreen, 5000L)
                    return@onChange previousSelection
                }

                // If so, add it to the current ruleset
                mapParameters.baseRuleset = newBaseRuleset
                reloadRuleset()

                // Check if the ruleset in it's entirety is still well-defined
                val modLinkErrors = ruleset.checkModLinks()
                if (modLinkErrors.isError()) {
                    mapParameters.mods.clear()
                    reloadRuleset()
                    // TODO: These can't be shown as this screen is itself a popup, what should be done instead?
                    val toastMessage =
                        "This base ruleset is not compatible with the previously selected\nextension mods. They have been disabled.".tr()
                    ToastPopup(toastMessage, mapEditorScreen, 5000L)

                    checkboxTable.disableAllCheckboxes()
                } else if (modLinkErrors.isWarnUser()) {
                    val toastMessage =
                        "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}".tr() +
                            "\n\n${modLinkErrors.getErrorText()}"
                    ToastPopup(toastMessage, mapEditorScreen, 5000L)
                }


                checkboxTable.setBaseRuleset(newBaseRuleset)

                null
            }


            selectBox.onChange {
                val newValue = onChange(selectBox.selected.value)
                if (newValue != null) selectBox.setSelected(newValue)
            }

            onChange(mapParameters.baseRuleset)

            rulesetSelectionBox.add(selectBox).fillX().row()
            return rulesetSelectionBox
        }

        private fun reloadRuleset() {
            ruleset.clear()
            val newRuleset = RulesetCache.getComplexRuleset(mapParameters.mods, mapParameters.baseRuleset)
            ruleset.add(newRuleset)
            ruleset.mods += mapParameters.baseRuleset
            ruleset.mods += mapParameters.mods
            ruleset.modOptions = newRuleset.modOptions

            ImageGetter.setNewRuleset(ruleset)
        }
        
    }

}
