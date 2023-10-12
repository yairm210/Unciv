package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toGdxArray
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.victoryscreen.LoadMapPreview
import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive

class MapFileSelectTable(
    private val newGameScreen: NewGameScreen,
    private val mapParameters: MapParameters
) : Table() {
    private val mapCategorySelectBox = SelectBox<String>(BaseScreen.skin)
    private val mapFileSelectBox = SelectBox<MapWrapper>(BaseScreen.skin)
    private val useNationsFromMapButton = "Select players from starting locations".toTextButton(AnimatedMenuPopup.SmallButtonStyle())
    private val useNationsButtonCell: Cell<Actor?>
    private var mapNations = emptySequence<String>()
    private val miniMapWrapper = Container<Group?>()
    private var mapPreviewJob: Job? = null

    // The SelectBox auto displays the text a object.toString(), which on the FileHandle itself includes the folder path.
    //  So we wrap it in another object with a custom toString()
    private class MapWrapper(val fileHandle: FileHandle, val mapPreview: TileMap.Preview) {
        override fun toString(): String = fileHandle.name()
        fun getCategoryName(): String = fileHandle.parent().parent().name()
            .ifEmpty { mapPreview.mapParameters.baseRuleset }
    }
    private val mapWrappers = ArrayList<MapWrapper>()

    private val columnWidth = newGameScreen.getColumnWidth()

    private val collator = UncivGame.Current.settings.getCollatorFromLocale()

    init {
        add(Table().apply {
            defaults().pad(5f, 10f)  // Must stay same as in MapParametersTable
            val mapCategoryLabel = "{Map Mod}:".toLabel()
            val mapFileLabel = "{Map file}:".toLabel()
            // because SOME people gotta give the hugest names to their maps
            val selectBoxWidth = columnWidth - 80f -
                mapFileLabel.prefWidth.coerceAtLeast(mapCategoryLabel.prefWidth)
            add(mapCategoryLabel).left()
            add(mapCategorySelectBox).width(selectBoxWidth).right().row()
            add(mapFileLabel).left()
            add(mapFileSelectBox).width(selectBoxWidth).right().row()
        }).growX()

        useNationsButtonCell = add().pad(0f)
        row()

        add(miniMapWrapper)
            .pad(15f)
            .maxWidth(columnWidth - 20f)
            .colspan(2).center().row()

        mapCategorySelectBox.onChange { onCategorySelectBoxChange() }
        mapFileSelectBox.onChange { onFileSelectBoxChange() }
        useNationsFromMapButton.onActivation { onUseNationsFromMap() }

        addMapWrappersAsync()
    }

    private fun getMapFilesSequence() = sequence<FileHandle> {
        yieldAll(MapSaver.getMaps().asSequence())
        for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
            val mapsFolder = modFolder.child(MapSaver.mapsFolder)
            if (mapsFolder.exists())
                yieldAll(mapsFolder.list().asSequence())
        }
    }.sortedByDescending { it.lastModified() }

    private fun addMapWrappersAsync() {
        val mapFilesFlow = getMapFilesSequence().asFlow().map {
            MapWrapper(it, MapSaver.loadMapPreview(it))
        }

        Concurrency.run {
            mapFilesFlow
                .onEach {
                    mapWrappers.add(it)
                    Concurrency.runOnGLThread {
                        addAsyncEntryToSelectBoxes(it)
                    }
                }
                .catch {}
                .collect()
            Concurrency.runOnGLThread {
                onCategorySelectBoxChange() // re-sort lower SelectBox
            }
        }
    }

    private fun addAsyncEntryToSelectBoxes(mapWrapper: MapWrapper) {
        // Take the mod name where the map is stored, or if it's not a mod map, the base ruleset it's saved for
        val categoryName = mapWrapper.getCategoryName()

        if (!mapCategorySelectBox.items.contains(categoryName, false)) {
            val sortToTop = newGameScreen.gameSetupInfo.gameParameters.baseRuleset
            val select = if (mapCategorySelectBox.selection.isEmpty) categoryName
                    else mapCategorySelectBox.selected
            // keep Ruleset Selectbox sorted while async is running - few entries
            val newItems = (mapCategorySelectBox.items.asSequence() + categoryName)
                .sortedWith(
                    compareBy<String?> { it != sortToTop }
                        .thenBy(collator) { it }
                ).toGdxArray()
            mapCategorySelectBox.items = newItems
            mapCategorySelectBox.selected = select
        }

        if (mapCategorySelectBox.selected != categoryName) return

        // .. but add the maps themselves as they come
        mapFileSelectBox.items.add(mapWrapper)
        mapFileSelectBox.items = mapFileSelectBox.items  // Call setItems so SelectBox sees the change
    }

    private val firstMap: FileHandle? by lazy {
        getMapFilesSequence().firstOrNull {
            try {
                MapSaver.loadMapParameters(it)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun FileHandle.isRecentlyModified() = lastModified() > System.currentTimeMillis() - 900000
    fun isNotEmpty() = firstMap != null
    fun recentlySavedMapExists() = firstMap != null && firstMap!!.isRecentlyModified()

    private fun onCategorySelectBoxChange() {
        val selectedRuleset: String? = mapCategorySelectBox.selected
        val mapFiles = mapWrappers.asSequence()
            .filter { it.getCategoryName() == selectedRuleset }
            .sortedWith(compareBy(collator) { it.toString() })
            .toGdxArray()
        fun getPreselect(): MapWrapper? {
            if (mapFiles.isEmpty) return null
            if (!mapFileSelectBox.selection.isEmpty) return mapFileSelectBox.selected
            val recent = mapFiles.asSequence()
                .filter { it.fileHandle.isRecentlyModified() }
                .maxByOrNull { it.fileHandle.lastModified() }
            if (recent != null) return recent
            val named = mapFiles.firstOrNull { it.fileHandle.name() == mapParameters.name }
            if (named != null) return named
            return mapFiles.first()
        }
        val selectedItem = getPreselect()

        // since mapFileSelectBox.selection.setProgrammaticChangeEvents() defaults to true, this would
        // kill the mapParameters.name we would like to look for when determining what to pre-select -
        // so do it ***after*** getting selectedItem - but control programmaticChangeEvents anyway
        // to avoid the overhead of doing a full updateRuleset + updateTables + startMapPreview
        // (all expensive) for something that will be overthrown momentarily
        mapFileSelectBox.selection.setProgrammaticChangeEvents(false)
        mapFileSelectBox.items = mapFiles
        // Now, we want this ON because the event removes map selections which are pulling mods
        // that trip updateRuleset - so that code should still be active for the pre-selection
        mapFileSelectBox.selection.setProgrammaticChangeEvents(true)
        mapFileSelectBox.selected = selectedItem
        // In the event described above, we now have: mapFileSelectBox.selected != selectedItem
        // Do NOT try to put back the "bad" preselection!
    }

    fun onSelectBoxChange() {
        cancelBackgroundJobs()
        if (mapFileSelectBox.selection.isEmpty) return
        val selection = mapFileSelectBox.selected

        mapNations = selection.mapPreview.getDeclaredNations()
        if (mapNations.none()) {
            useNationsButtonCell.setActor(null)
            useNationsButtonCell.height(0f).pad(0f)
        } else {
            useNationsFromMapButton.enable()
            useNationsButtonCell.setActor(useNationsFromMapButton)
            useNationsButtonCell.height(useNationsFromMapButton.prefHeight).padLeft(5f).padTop(10f)
        }

        val mapFile = selection.fileHandle
        mapParameters.name = mapFile.name()
        newGameScreen.gameSetupInfo.mapFile = mapFile
        val mapMods = selection.mapPreview.mapParameters.mods
            .partition { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
        newGameScreen.gameSetupInfo.gameParameters.mods = LinkedHashSet(mapMods.second)
        newGameScreen.gameSetupInfo.gameParameters.baseRuleset = mapMods.first.firstOrNull()
            ?: selection.mapPreview.mapParameters.baseRuleset
        val success = newGameScreen.tryUpdateRuleset()
        newGameScreen.updateTables()
        hideMiniMap()
        if (success) {
            startMapPreview(mapFile)
        } else {
            // Mod error - the options have been reset by updateRuleset
            // Note SelectBox doesn't react sensibly to _any_ clear - Group, Selection or items
            val items = mapFileSelectBox.items
            items.removeIndex(mapFileSelectBox.selectedIndex)
            // Changing the array itself is not enough, SelectBox gets out of sync, need to call setItems()
            mapFileSelectBox.items = items
            // Note - this will have triggered a nested onFileSelectBoxChange()!
        }
    }

    private fun startMapPreview(mapFile: FileHandle) {
        mapPreviewJob = Concurrency.run {
            try {
                val map = MapSaver.loadMap(mapFile)
                if (!isActive) return@run
                map.setTransients(newGameScreen.ruleset, false)
                if (!isActive) return@run
                // ReplayMap still paints outside its bounds - so we subtract padding and a little extra
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

    private fun onUseNationsFromMap() {
        useNationsFromMapButton.disable()
        val players = newGameScreen.playerPickerTable.gameParameters.players
        players.clear()
        mapNations
            .map { it.tr() }
            .sortedWith(collator)
            .withIndex()
            .map { Player(it.value, if (it.index == 0) PlayerType.Human else PlayerType.AI) }
            .toCollection(players)
        newGameScreen.playerPickerTable.update()
    }
}
