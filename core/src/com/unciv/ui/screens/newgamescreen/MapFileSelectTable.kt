package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.MapParameters
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.extensions.onChange
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.victoryscreen.LoadMapPreview
import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import com.badlogic.gdx.utils.Array as GdxArray

class MapFileSelectTable(
    private val newGameScreen: NewGameScreen,
    private val mapParameters: MapParameters
) : Table() {

    private val mapFileSelectBox = SelectBox<MapWrapper>(BaseScreen.skin)
    private val miniMapWrapper = Container<Group?>()
    private var mapPreviewJob: Job? = null

    // The SelectBox auto displays the text a object.toString(), which on the FileHandle itself includes the folder path.
    //  So we wrap it in another object with a custom toString()
    private class MapWrapper(val fileHandle: FileHandle, val mapParameters: MapParameters) {
        override fun toString(): String = mapParameters.baseRuleset + " | "+ fileHandle.name()
    }
    private val mapWrappers= ArrayList<MapWrapper>()

    private val columnWidth = newGameScreen.getColumnWidth()

    init {
        defaults().pad(5f, 10f)  // Must stay same as in MapParametersTable
        val mapFileLabel = "{Map file}:".toLabel()
        add(mapFileLabel).left()
        add(mapFileSelectBox)
            // because SOME people gotta give the hugest names to their maps
            .width(columnWidth * 2 / 3)
            .right().row()
        add(miniMapWrapper)
            .pad(15f)
            .colspan(2).center().row()

        mapFileSelectBox.onChange { onSelectBoxChange() }

        val mapFilesSequence = sequence<FileHandle> {
            yieldAll(MapSaver.getMaps().asSequence())
            for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
                val mapsFolder = modFolder.child(MapSaver.mapsFolder)
                if (mapsFolder.exists())
                    yieldAll(mapsFolder.list().asSequence())
            }
        }


        for (mapFile in mapFilesSequence) {
            val mapParameters = try {
                MapSaver.loadMapParameters(mapFile)
            } catch (_: Exception) {
                continue
            }
            mapWrappers.add(MapWrapper(mapFile, mapParameters))
        }
    }


    fun isNotEmpty() = mapWrappers.isNotEmpty()
    fun recentlySavedMapExists() = mapWrappers.any {
        it.fileHandle.lastModified() > System.currentTimeMillis() - 900000
    }

    fun fillMapFileSelectBox() {
        if (!mapFileSelectBox.items.isEmpty) return

        val mapFiles = GdxArray<MapWrapper>()
        mapWrappers
            .sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.toString() })
            .sortedByDescending { it.mapParameters.baseRuleset == newGameScreen.gameSetupInfo.gameParameters.baseRuleset }
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
        mapParameters.name = selectedItem.toString()
        newGameScreen.gameSetupInfo.mapFile = selectedItem.fileHandle
    }

    private fun onSelectBoxChange() {
        cancelBackgroundJobs()
        val mapFile = mapFileSelectBox.selected.fileHandle
        mapParameters.name = mapFile.name()
        newGameScreen.gameSetupInfo.mapFile = mapFile
        val mapMods = mapFileSelectBox.selected.mapParameters.mods.partition { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
        newGameScreen.gameSetupInfo.gameParameters.mods = LinkedHashSet(mapMods.second)
        newGameScreen.gameSetupInfo.gameParameters.baseRuleset = mapMods.first.firstOrNull()
            ?: mapFileSelectBox.selected.mapParameters.baseRuleset
        newGameScreen.updateRuleset()
        newGameScreen.updateTables()
        hideMiniMap()
        startMapPreview(mapFile)
    }

    private fun startMapPreview(mapFile: FileHandle) {
        mapPreviewJob = Concurrency.run {
            try {
                val map = MapSaver.loadMap(mapFile)
                if (!isActive) return@run
                map.setTransients(newGameScreen.ruleset, false)
                if (!isActive) return@run
                // ReplyMap still paints outside its bounds - so we subtract padding and a little extra
                val size = (columnWidth - 40f).coerceAtMost(500f)
                val miniMap = LoadMapPreview(map, size, size)
                if (!isActive) return@run
                Concurrency.runOnGLThread {
                    showMinimap(miniMap)
                }
            } catch (_: Throwable) {}
        }.apply {
            invokeOnCompletion {
                mapPreviewJob = null
            }
        }
    }

    internal fun cancelBackgroundJobs() {
        mapPreviewJob?.cancel()
        mapPreviewJob = null
        miniMapWrapper.clearActions()
    }

    private fun showMinimap(miniMap: LoadMapPreview) {
        if (miniMapWrapper.actor == miniMap) return
        miniMapWrapper.clearActions()
        miniMapWrapper.color.a = 0f
        miniMapWrapper.actor = miniMap
        miniMapWrapper.invalidateHierarchy()
        miniMapWrapper.addAction(Actions.fadeIn(0.2f))
    }

    private fun hideMiniMap() {
        if (miniMapWrapper.actor !is LoadMapPreview) return
        miniMapWrapper.clearActions()
        miniMapWrapper.addAction(
            Actions.sequence(
                Actions.fadeOut(0.4f),
                Actions.run {
                    // in portrait, simply removing the map preview will cause the layout to "jump".
                    // with a dummy holding the empty space, it jumps later and not as far.
                    val dummy = Group().apply {
                        setSize(miniMapWrapper.actor!!.width, miniMapWrapper.actor!!.height)
                    }
                    miniMapWrapper.actor = dummy
                }
            )
        )
    }
}
