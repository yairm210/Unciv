package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.newgamescreen.MapParametersTable
import com.unciv.ui.newgamescreen.ModCheckboxTable
import com.unciv.ui.newgamescreen.TranslatedSelectBox
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread
import kotlin.math.max
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

/** New map generation screen */
class NewMapScreen(val mapParameters: MapParameters = getDefaultParameters()) : PickerScreen() {

    private val ruleset = RulesetCache.getBaseRuleset()
    private var generatedMap: TileMap? = null
    private val mapParametersTable: MapParametersTable
    private val modCheckBoxes: ModCheckboxTable

    companion object {
        private fun getDefaultParameters(): MapParameters {
            val lastSetup = UncivGame.Current.settings.lastGameSetup
                ?: return MapParameters()
            return lastSetup.mapParameters.clone().apply { reseed() }
        }
        private fun saveDefaultParameters(parameters: MapParameters) {
            val settings = UncivGame.Current.settings
            val lastSetup = settings.lastGameSetup
                ?: GameSetupInfo().also { settings.lastGameSetup = it }
            lastSetup.mapParameters = parameters.clone()
            settings.save()
        }
    }

    init {
        setDefaultCloseAction(MainMenuScreen())

        // To load in the mods selected last time this screen was exited
        reloadRuleset()

        mapParametersTable = MapParametersTable(mapParameters, isEmptyMapAllowed = true)
        val newMapScreenOptionsTable = Table(skin).apply {
            pad(10f)
            add("Map Options".toLabel(fontSize = 24)).row()

            // Add the selector for the base ruleset
            val baseRulesetBox = getBaseRulesetSelectBox()
            if (baseRulesetBox != null) {
                // TODO: For some reason I'm unable to get these two tables to be equally wide
                // someone who knows what they're doing should fix this
                val maxWidth = max(baseRulesetBox.minWidth, mapParametersTable.minWidth)
                baseRulesetBox.width = maxWidth
                mapParametersTable.width = maxWidth
                add(getBaseRulesetSelectBox()).row()
            }

            add(mapParametersTable).row()

            modCheckBoxes = ModCheckboxTable(mapParameters.mods, mapParameters.baseRuleset, this@NewMapScreen) {
                reloadRuleset()
            }
            add(modCheckBoxes)
            pack()
        }


        topTable.apply {
            add(ScrollPane(newMapScreenOptionsTable).apply { setOverscroll(false, false) })
            pack()
        }

        rightButtonSetEnabled(true)
        rightSideButton.onClick {
            val message = mapParameters.mapSize.fixUndesiredSizes(mapParameters.worldWrap)
            if (message != null) {
                Gdx.app.postRunnable {
                    ToastPopup( message, UncivGame.Current.screen as BaseScreen, 4000 )
                    with (mapParameters.mapSize) {
                        mapParametersTable.customMapSizeRadius.text = radius.toString()
                        mapParametersTable.customMapWidth.text = width.toString()
                        mapParametersTable.customMapHeight.text = height.toString()
                    }
                }
                return@onClick
            }
            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            rightButtonSetEnabled(false)

            thread(name = "MapGenerator") {
                try {
                    // Map generation can take a while and we don't want ANRs
                    generatedMap = MapGenerator(ruleset).generateMap(mapParameters)

                    Gdx.app.postRunnable {
                        saveDefaultParameters(mapParameters)
                        val mapEditorScreen = MapEditorScreen(generatedMap!!)
                        mapEditorScreen.ruleset = ruleset
                        game.setScreen(mapEditorScreen)
                    }

                } catch (exception: Exception) {
                    println("Map generator exception: ${exception.message}")
                    Gdx.app.postRunnable {
                        rightButtonSetEnabled(true)
                        val cantMakeThatMapPopup = Popup(this)
                        cantMakeThatMapPopup.addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!".tr())
                                .row()
                        cantMakeThatMapPopup.addCloseButton()
                        cantMakeThatMapPopup.open()
                        Gdx.input.inputProcessor = stage
                    }
                }
            }

        }
    }

    /** Changes the state and the text of the [rightSideButton] */
    private fun rightButtonSetEnabled(enabled: Boolean) {
        if (enabled) {
            rightSideButton.enable()
            rightSideButton.setText("Create".tr())
        } else {
            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())
        }
    }

    private fun getBaseRulesetSelectBox(): Table? {
        val rulesetSelectionBox = Table()

        val sortedBaseRulesets = RulesetCache.getSortedBaseRulesets()
        if (sortedBaseRulesets.size < 2) return null

        rulesetSelectionBox.add("{Base Ruleset}:".toLabel()).left()
        val selectBox = TranslatedSelectBox(sortedBaseRulesets, mapParameters.baseRuleset, skin)

        val onChange = onChange@{ newBaseRuleset: String ->
            val previousSelection = mapParameters.baseRuleset
            if (newBaseRuleset == previousSelection) return@onChange null

            // Check if this mod is well-defined
            val baseRulesetErrors = RulesetCache[newBaseRuleset]!!.checkModLinks()
            if (baseRulesetErrors.isError()) {
                val toastMessage = "The mod you selected is incorrectly defined!".tr() + "\n\n${baseRulesetErrors.getErrorText()}"
                ToastPopup(toastMessage, this@NewMapScreen, 5000L)
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
                val toastMessage =
                    "This base ruleset is not compatible with the previously selected\nextension mods. They have been disabled.".tr()
                ToastPopup(toastMessage, this@NewMapScreen, 5000L)

                modCheckBoxes.disableAllCheckboxes()
            } else if (modLinkErrors.isWarnUser()) {
                val toastMessage =
                    "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}".tr() +
                        "\n\n${modLinkErrors.getErrorText()}"
                ToastPopup(toastMessage, this@NewMapScreen, 5000L)
            }


            modCheckBoxes.setBaseRuleset(newBaseRuleset)

            null
        }


        selectBox.onChange {
            val changedValue = onChange(selectBox.selected.value)
            if (changedValue != null) selectBox.setSelected(changedValue)
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
