package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.newgamescreen.MapParametersTable
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

class MapEditorGenerateTab(
    private val editorScreen: MapEditorScreenV2
): TabbedPager(capacity = 2) {
    private val newTab = MapEditorNewMapTab(this)
    private val partialTab = MapEditorGenerateStepsTab(this)

    // Since we allow generation components to be run repeatedly, it might surprise the user that
    // the outcome stays the same when repeated - due to them operating on the same seed.
    // So we change the seed behind the scenes if already used for a certain step...
    private val seedUsedForStep = mutableSetOf<MapGeneratorSteps>()

    init {
        name = "Generate"
        top()
        addPage("New map", newTab,
            ImageGetter.getImage("OtherIcons/New"), 20f,
            shortcutKey = KeyCharAndCode.ctrl('n'))
        addPage("Partial", partialTab,
            ImageGetter.getImage("OtherIcons/Settings"), 20f,
            shortcutKey = KeyCharAndCode.ctrl('g'))
        selectPage(0)
        setButtonsEnabled(true)
        partialTab.generateButton.disable()  // Starts with choice "None"
    }

    private fun setButtonsEnabled(enable: Boolean) {
        newTab.generateButton.isEnabled = enable
        newTab.generateButton.setText( (if(enable) "Create" else "Working...").tr())
        partialTab.generateButton.isEnabled = enable
        partialTab.generateButton.setText( (if(enable) "Generate" else "Working...").tr())
    }

    private fun generate(step: MapGeneratorSteps) {
        val mapParameters = editorScreen.newMapParameters
        val message = mapParameters.mapSize.fixUndesiredSizes(mapParameters.worldWrap)
        if (message != null) {
            Gdx.app.postRunnable {
                ToastPopup( message, editorScreen, 4000 )
                newTab.mapParametersTable.run { mapParameters.mapSize.also {
                    customMapSizeRadius.text = it.radius.toString()
                    customMapWidth.text = it.width.toString()
                    customMapHeight.text = it.height.toString()
                } }
            }
            return
        }

        if (step == MapGeneratorSteps.Landmass && mapParameters.type == MapType.empty) {
            ToastPopup("Please don't use step 'Landmass' with map type 'Empty', create a new empty map instead.", editorScreen)
            return
        }

        if (step in seedUsedForStep) {
            mapParameters.reseed()
        } else {
            seedUsedForStep += step
        }

        Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
        setButtonsEnabled(false)

        thread(name = "MapGenerator") {
            try {
                // Map generation can take a while and we don't want ANRs
                if (step == MapGeneratorSteps.All) {
                    val newRuleset = RulesetCache.getComplexRuleset(mapParameters.mods, mapParameters.baseRuleset)
                    val generatedMap = MapGenerator(newRuleset).generateMap(mapParameters)

                    Gdx.app.postRunnable {
                        MapEditorScreenV2.saveDefaultParameters(mapParameters)
                        editorScreen.loadMap(generatedMap, newRuleset)
                        editorScreen.isDirty = true
                        setButtonsEnabled(true)
                        Gdx.input.inputProcessor = editorScreen.stage
                    }
                } else {
                    MapGenerator(editorScreen.ruleset).generateSingleStep(editorScreen.tileMap, step)

                    Gdx.app.postRunnable {
                        if (step == MapGeneratorSteps.NaturalWonders) editorScreen.naturalWondersNeedRefresh = true
                        editorScreen.mapHolder.updateTileGroups()
                        editorScreen.isDirty = true
                        setButtonsEnabled(true)
                        Gdx.input.inputProcessor = editorScreen.stage
                    }
                }
            } catch (exception: Exception) {
                println("Map generator exception: ${exception.message}")
                Gdx.app.postRunnable {
                    setButtonsEnabled(true)
                    Gdx.input.inputProcessor = editorScreen.stage
                    Popup(editorScreen).apply {
                        addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!".tr())
                        row()
                        addCloseButton()
                    }.open()
                }
            }
        }
    }

    class MapEditorNewMapTab(
        private val parent: MapEditorGenerateTab
    ): Table(BaseScreen.skin) {
        val generateButton = "".toTextButton()
        val mapParametersTable = MapParametersTable(parent.editorScreen.newMapParameters, isEmptyMapAllowed = true)

        init {
            top()
            pad(10f)
            add("Map Options".toLabel(fontSize = 24)).row()
            add(mapParametersTable).row()
            add(generateButton).padTop(15f).row()
            generateButton.onClick { parent.generate(MapGeneratorSteps.All) }
        }
    }

    class MapEditorGenerateStepsTab(
        private val parent: MapEditorGenerateTab
    ): Table(BaseScreen.skin) {
        private val optionGroup = ButtonGroup<CheckBox>()
        val generateButton = "".toTextButton()
        private var choice = MapGeneratorSteps.None
        private val newMapParameters = parent.editorScreen.newMapParameters
        private val tileMap = parent.editorScreen.tileMap
        private val actualMapParameters = tileMap.mapParameters

        init {
            top()
            pad(10f)
            defaults().pad(2.5f)
            add("Generator steps".toLabel(fontSize = 24)).row()
            optionGroup.setMinCheckCount(0)
            for (option in MapGeneratorSteps.values()) {
                if (option <= MapGeneratorSteps.All) continue
                val checkBox = option.label.toCheckBox {
                        choice = option
                        generateButton.enable()
                    }
                add(checkBox).row()
                optionGroup.add(checkBox)
            }
            add(generateButton).padTop(15f).row()
            generateButton.onClick {
                parent.generate(choice)
                choice.copyParameters?.invoke(newMapParameters, actualMapParameters)
            }
        }
    }
}
