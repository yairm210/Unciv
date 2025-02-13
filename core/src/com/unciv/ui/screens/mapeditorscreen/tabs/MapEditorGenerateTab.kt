package com.unciv.ui.screens.mapeditorscreen.tabs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps
import com.unciv.ui.screens.newgamescreen.MapParametersTable
import com.unciv.utils.Concurrency
import com.unciv.utils.Log

class MapEditorGenerateTab(
    private val editorScreen: MapEditorScreen,
    headerHeight: Float
): TabbedPager(capacity = 2, maximumHeight = headerHeight) {
    private val newTab = MapEditorNewMapTab(this)
    private val partialTab = MapEditorGenerateStepsTab(this)

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
        newTab.generateButton.setText( (if(enable) "Create" else Constants.working).tr())
        partialTab.generateButton.isEnabled = enable
        partialTab.generateButton.setText( (if(enable) "Generate" else Constants.working).tr())
    }

    private fun generate(step: MapGeneratorSteps) {
        if (newTab.mapParametersTable.randomizeSeed) {
            // reseed visibly if the "Randomize seed" checkbox is checked
            newTab.mapParametersTable.reseed()
        }

        val mapParameters = editorScreen.newMapParameters.clone()  // this clone is very important here
        val message = mapParameters.mapSize.fixUndesiredSizes(mapParameters.worldWrap)
        if (message != null) {
            Concurrency.runOnGLThread {
                ToastPopup( message, editorScreen, 4000 )
                newTab.mapParametersTable.run { mapParameters.mapSize.also {
                    customMapSizeRadius.text = it.radius.tr()
                    customMapWidth.text = it.width.tr()
                    customMapHeight.text = it.height.tr()
                } }
            }
            return
        }

        if (step == MapGeneratorSteps.Landmass && mapParameters.type == MapType.empty) {
            ToastPopup("Please don't use step 'Landmass' with map type 'Empty', create a new empty map instead.", editorScreen)
            return
        }


        Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
        setButtonsEnabled(false)

        fun freshMapCompleted(generatedMap: TileMap, mapParameters: MapParameters, newRuleset: Ruleset, selectPage: Int) {
            MapEditorScreen.saveDefaultParameters(mapParameters)
            editorScreen.loadMap(generatedMap, newRuleset, selectPage) // also reactivates inputProcessor
            editorScreen.isDirty = true
            setButtonsEnabled(true)
        }
        fun stepCompleted(step: MapGeneratorSteps) {
            if (step == MapGeneratorSteps.NaturalWonders) editorScreen.naturalWondersNeedRefresh = true
            editorScreen.mapHolder.updateTileGroups()
            editorScreen.isDirty = true
            setButtonsEnabled(true)
            Gdx.input.inputProcessor = editorScreen.stage
        }

        // Map generation can take a while and we don't want ANRs
        editorScreen.startBackgroundJob("MapEditor.MapGenerator") {
            try {
                val (newRuleset, generator) = if (step > MapGeneratorSteps.Landmass) null to null
                    else {
                        val newRuleset = RulesetCache.getComplexRuleset(mapParameters)
                        newRuleset to MapGenerator(newRuleset)
                    }
                when (step) {
                    MapGeneratorSteps.All -> {
                        val generatedMap = generator!!.generateMap(mapParameters)
                        val savedScale = editorScreen.mapHolder.scaleX
                        Concurrency.runOnGLThread {
                            freshMapCompleted(generatedMap, mapParameters, newRuleset!!, selectPage = 0)
                            editorScreen.mapHolder.zoom(savedScale)
                        }
                    }
                    MapGeneratorSteps.Landmass -> {
                        // This step _could_ run on an existing tileMap, but that opens a loophole where you get hills on water - fixing that is more expensive than always recreating
                        mapParameters.type = MapType.empty
                        val generatedMap = generator!!.generateMap(mapParameters)
                        mapParameters.type = editorScreen.newMapParameters.type
                        generator.generateSingleStep(generatedMap, step)
                        val savedScale = editorScreen.mapHolder.scaleX
                        Concurrency.runOnGLThread {
                            freshMapCompleted(generatedMap, mapParameters, newRuleset!!, selectPage = 1)
                            editorScreen.mapHolder.zoom(savedScale)
                        }
                    }
                    else -> {
                        editorScreen.tileMap.mapParameters.seed = mapParameters.seed
                        MapGenerator(editorScreen.ruleset).generateSingleStep(editorScreen.tileMap, step)
                        Concurrency.runOnGLThread {
                            stepCompleted(step)
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.error("Exception while generating map", exception)
                Concurrency.runOnGLThread {
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
        val mapParametersTable = MapParametersTable(null, parent.editorScreen.newMapParameters, MapGeneratedMainType.generated, forMapEditor = true) {
            parent.replacePage(0, this)  // A kludge to get the ScrollPanes to recognize changes in vertical layout??
        }

        init {
            top()
            pad(10f)
            add("Map Options".toLabel(fontSize = 24)).row()
            add(mapParametersTable).row()
            add(generateButton).padTop(15f).row()
            generateButton.onClick { parent.generate(MapGeneratorSteps.All) }
            mapParametersTable.resourceSelectBox.onChange {
                parent.editorScreen.run {
                    // normally the 'new map' parameters are independent, this needs to be an exception so strategic resource painting will use it
                    tileMap.mapParameters.mapResources = newMapParameters.mapResources
                }
            }
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
