package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.newgamescreen.MapParametersTable
import com.unciv.ui.newgamescreen.ModCheckboxTable
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

/** New map generation screen */
class NewMapScreen(val mapParameters: MapParameters = MapParameters()) : PickerScreen() {

    private val ruleset = RulesetCache.getBaseRuleset()
    private var generatedMap: TileMap? = null
    private val mapParametersTable: MapParametersTable

    init {
        setDefaultCloseAction(MainMenuScreen())

        mapParametersTable = MapParametersTable(mapParameters, isEmptyMapAllowed = true)
        val newMapScreenOptionsTable = Table(skin).apply {
            pad(10f)
            add("Map Options".toLabel(fontSize = 24)).row()
            add(mapParametersTable).row()
            add(ModCheckboxTable(mapParameters.mods, this@NewMapScreen) {
                ruleset.clear()
                val newRuleset = RulesetCache.getComplexRuleset(mapParameters.mods)
                ruleset.add(newRuleset)
                ruleset.mods += mapParameters.mods
                ruleset.modOptions = newRuleset.modOptions

                ImageGetter.setNewRuleset(ruleset)
            })
            pack()
        }


        topTable.apply {
            add(ScrollPane(newMapScreenOptionsTable).apply { setOverscroll(false, false) })
            pack()
//            setFillParent(true)
        }

        rightButtonSetEnabled(true)
        rightSideButton.onClick {
            val message = mapParameters.mapSize.fixUndesiredSizes(mapParameters.worldWrap)
            if (message != null) {
                Gdx.app.postRunnable {
                    ToastPopup( message, UncivGame.Current.screen as CameraStageBaseScreen, 4000 )
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
                        val mapEditorScreen = MapEditorScreen(generatedMap!!)
                        mapEditorScreen.ruleset = ruleset
                        game.setScreen(mapEditorScreen)
                    }

                } catch (exception: Exception) {
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
}
