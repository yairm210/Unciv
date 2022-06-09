package com.unciv.ui.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapType
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.toLabel

class MapOptionsTable(private val newGameScreen: NewGameScreen): Table() {

    private val mapParameters = newGameScreen.gameSetupInfo.mapParameters
    private var mapTypeSpecificTable = Table()
    val generatedMapOptionsTable = MapParametersTable(mapParameters)
    private val savedMapOptionsTable = Table()
    lateinit var mapTypeSelectBox: TranslatedSelectBox
    private val mapFileSelectBox = createMapFileSelectBox()

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
    }

    private fun addMapTypeSelection() {
        val mapTypes = arrayListOf("Generated")
        if (mapFilesSequence.any()) mapTypes.add(MapType.custom)
        mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", BaseScreen.skin)

        savedMapOptionsTable.defaults().pad(5f)
        savedMapOptionsTable.add("{Map file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        val columnWidth = newGameScreen.stage.width / (if (newGameScreen.isNarrowerThan4to3()) 1 else 3)
        savedMapOptionsTable.add(mapFileSelectBox)
            .maxWidth((columnWidth - 120f).coerceAtLeast(120f))
            .right().row()


        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            if (mapTypeSelectBox.selected.value == MapType.custom) {
                fillMapFileSelectBox()
                mapParameters.type = MapType.custom
                mapParameters.name = mapFileSelectBox.selected.toString()
                mapTypeSpecificTable.add(savedMapOptionsTable)
                newGameScreen.unlockTables()
            } else { // generated map
                mapParameters.name = ""
                mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                mapTypeSpecificTable.add(generatedMapOptionsTable)
                newGameScreen.unlockTables()
            }
            newGameScreen.gameSetupInfo.gameParameters.godMode = false
            newGameScreen.updateTables()
        }

        // Pre-select custom if any map saved within last 15 minutes
        if (mapFilesSequence.any { it.fileHandle.lastModified() > System.currentTimeMillis() - 900000 })
            mapTypeSelectBox.selected = TranslatedSelectBox.TranslatedString(MapType.custom)

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

    // The SelectBox auto displays the text a object.toString(), which on the FileHandle itself includes the folder path.
    //  So we wrap it in another object with a custom toString()
    class FileHandleWrapper(val fileHandle: FileHandle) {
        override fun toString(): String = fileHandle.name()
    }

}
