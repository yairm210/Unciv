package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.logic.files.MapSaver
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.extensions.onChange
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.victoryscreen.ReplayMap
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

class MapOptionsTable(private val newGameScreen: NewGameScreen): Table() {

    private val mapParameters = newGameScreen.gameSetupInfo.mapParameters
    private var mapTypeSpecificTable = Table()
    val generatedMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.generated)
    private val randomMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.randomGenerated)
    private val savedMapOptionsTable = Table()
    private val loadMapMiniMap = Container<ReplayMap?>()
    lateinit var mapTypeSelectBox: TranslatedSelectBox
    private val mapFileSelectBox = createMapFileSelectBox()
    private var mapPreviewJob: Job? = null

    private val mapFilesSequence = sequence<FileHandle> {
        yieldAll(MapSaver.getMaps().asSequence())
        for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
            val mapsFolder = modFolder.child(MapSaver.mapsFolder)
            if (mapsFolder.exists())
                yieldAll(mapsFolder.list().asSequence())
        }
    }.map { FileHandleWrapper(it) }

    init {
        //defaults().pad(5f) - each nested table having the same can give 'stairs' effects,
        // better control directly. Besides, the first Labels/Buttons should have 10f to look nice
        addMapTypeSelection()
        background = BaseScreen.skinStrings.getUiBackground("NewGameScreen/MapOptionsTable", tintColor = BaseScreen.skinStrings.skinConfig.clearColor)
    }

    private fun addMapTypeSelection() {
        val mapTypes = arrayListOf(MapGeneratedMainType.generated, MapGeneratedMainType.randomGenerated)
        if (mapFilesSequence.any()) mapTypes.add(MapGeneratedMainType.custom)
        mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", BaseScreen.skin)

        savedMapOptionsTable.defaults().pad(5f)
        savedMapOptionsTable.add("{Map file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        val columnWidth = newGameScreen.stage.width / (if (newGameScreen.isNarrowerThan4to3()) 1 else 3)
        savedMapOptionsTable.add(mapFileSelectBox)
            .maxWidth((columnWidth - 120f).coerceAtLeast(120f))
            .right().row()
        savedMapOptionsTable.add(loadMapMiniMap)
            .colspan(2).center().size(columnWidth, columnWidth * 0.75f).row()

        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            when (mapTypeSelectBox.selected.value) {
                MapGeneratedMainType.custom -> {
                    fillMapFileSelectBox()
                    mapParameters.type = MapGeneratedMainType.custom
                    mapParameters.name = mapFileSelectBox.selected.toString()
                    mapTypeSpecificTable.add(savedMapOptionsTable)
                    newGameScreen.unlockTables()
                }
                MapGeneratedMainType.generated -> {
                    mapParameters.name = ""
                    mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                    mapTypeSpecificTable.add(generatedMapOptionsTable)
                    newGameScreen.unlockTables()

                }
                MapGeneratedMainType.randomGenerated -> {
                    mapParameters.name = ""
                    mapTypeSpecificTable.add(randomMapOptionsTable)
                    newGameScreen.unlockTables()
                }
            }
            newGameScreen.gameSetupInfo.gameParameters.godMode = false
            newGameScreen.updateTables()
        }

        // Pre-select custom if any map saved within last 15 minutes
        if (mapFilesSequence.any { it.fileHandle.lastModified() > System.currentTimeMillis() - 900000 })
            mapTypeSelectBox.selected =
                    TranslatedSelectBox.TranslatedString(MapGeneratedMainType.custom)

        // activate once, so when we had a file map before we'll have the right things set for another one
        updateOnMapTypeChange()

        mapTypeSelectBox.onChange { updateOnMapTypeChange() }

        val mapTypeSelectWrapper = Table()  // wrap to center-align Label and SelectBox easier
        mapTypeSelectWrapper.add("{Map Type}:".toLabel()).left().expandX()
        mapTypeSelectWrapper.add(mapTypeSelectBox).right()
        add(mapTypeSelectWrapper).pad(10f).fillX().row()
        add(mapTypeSpecificTable).row()
    }

    private fun createMapFileSelectBox(): SelectBox<FileHandleWrapper> {
        val mapFileSelectBox = SelectBox<FileHandleWrapper>(BaseScreen.skin)
        mapFileSelectBox.onChange {
            cancelBackgroundJobs()
            val mapFile = mapFileSelectBox.selected.fileHandle
            val mapParams = try {
                MapSaver.loadMapParameters(mapFile)
            } catch (ex:Exception){
                ex.printStackTrace()
                Popup(newGameScreen).apply {
                    addGoodSizedLabel("Could not load map!").row()
                    if (ex is UncivShowableException)
                        addGoodSizedLabel(ex.message).row()
                    addCloseButton()
                    open()
                }
                return@onChange
            }
            mapParameters.name = mapFile.name()
            newGameScreen.gameSetupInfo.mapFile = mapFile
            val mapMods = mapParams.mods.partition { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
            newGameScreen.gameSetupInfo.gameParameters.mods = LinkedHashSet(mapMods.second)
            newGameScreen.gameSetupInfo.gameParameters.baseRuleset = mapMods.first.firstOrNull() ?: mapParams.baseRuleset
            newGameScreen.updateRuleset()
            newGameScreen.updateTables()
            startMapPreview(mapFile)
        }
        return mapFileSelectBox
    }

    private fun fillMapFileSelectBox() {
        if (!mapFileSelectBox.items.isEmpty) return
        val mapFiles = Array<FileHandleWrapper>()
        mapFilesSequence
            .sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.toString() })
            .forEach { mapFiles.add(it) }
        mapFileSelectBox.items = mapFiles

        // Pre-select: a) map saved within last 15min or b) map named in mapParameters or c) alphabetically first
        // This is a kludge - the better way would be to have a "play this map now" menu button in the editor
        // (which would ideally not even require a save file - which makes implementation non-trivial)
        val selectedItem =
            mapFiles.maxByOrNull { it.fileHandle.lastModified() }
                ?.takeIf { it.fileHandle.lastModified() > System.currentTimeMillis() - 900000 }
            ?: mapFiles.firstOrNull { it.fileHandle.name() == mapParameters.name }
            ?: mapFiles.firstOrNull()
            ?: return
        mapFileSelectBox.selected = selectedItem
        newGameScreen.gameSetupInfo.mapFile = selectedItem.fileHandle
    }

    private fun startMapPreview(mapFile: FileHandle) {
        mapPreviewJob = Concurrency.run {
            try {
                val map = MapSaver.loadMap(mapFile)
                if (!isActive) return@run
                map.setTransients(newGameScreen.ruleset, false)
                if (!isActive) return@run
                val miniMap = ReplayMap(map, null, 300f, 200f)
                if (!isActive) return@run
                miniMap.update(0)
                if (!isActive) return@run
                Concurrency.runOnGLThread {
                    loadMapMiniMap.actor = miniMap
                }
            } catch (_: Throwable) {}
        }.apply {
            invokeOnCompletion {
                mapPreviewJob = null
            }
        }
    }

    fun cancelBackgroundJobs() {
        mapPreviewJob?.cancel()
        mapPreviewJob = null
    }

    // The SelectBox auto displays the text a object.toString(), which on the FileHandle itself includes the folder path.
    //  So we wrap it in another object with a custom toString()
    class FileHandleWrapper(val fileHandle: FileHandle) {
        override fun toString(): String = fileHandle.name()
    }

}
