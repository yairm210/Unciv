package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.SmallButtonStyle
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.victoryscreen.LoadMapPreview
import com.unciv.utils.Concurrency
import com.unciv.utils.toGdxArray
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive

private inline fun <T> SelectBox<T>.withoutChangeEvents(block: SelectBox<T>.() -> Unit) {
    selection.setProgrammaticChangeEvents(false)
    block()
    selection.setProgrammaticChangeEvents(true)
}

class MapFileSelectTable(
    private val newGameScreen: NewGameScreen,
    private val mapParameters: MapParameters
) : Table() {
    private val mapCategorySelectBox = SelectBox<String>(BaseScreen.skin)
    private val mapFileSelectBox = SelectBox<MapWrapper>(BaseScreen.skin)
    private val loadingIcon = LoadingImage(30f, LoadingImage.Style(loadingColor = Color.SCARLET))
    private val useNationsFromMapButton = "Select players from starting locations".toTextButton(SmallButtonStyle())
    private val useNationsButtonCell: Cell<Actor?>
    private var mapNations = emptyList<Nation>()
    private var mapHumanPick: String? = null
    private val miniMapWrapper = Container<Group?>()
    private var mapPreviewJob: Job? = null
    private var preselectedName = mapParameters.name
    private val descriptionLabel = "".toLabel()
    private val descriptionLabelCell: Cell<Label>

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

    private var isActivated = false // Ensure we get activated only once

    init {
        // Take care that this class can be instantiated cheaply and does not do heavy lifting right away - it may not be displayed at all.
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

        add(loadingIcon).padRight(5f).padLeft(0f).row()

        useNationsButtonCell = add().pad(0f)
        row()

        descriptionLabelCell = add(descriptionLabel)
        descriptionLabelCell.height(0f).row()

        add(miniMapWrapper)
            .pad(15f)
            .maxWidth(columnWidth - 20f)
            .colspan(2).center().row()

        useNationsFromMapButton.onActivation { onUseNationsFromMap() }

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

        loadingIcon.show()
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
                loadingIcon.hide {
                    loadingIcon.remove()
                }
                onCategorySelectBoxChange() // re-sort lower SelectBox, and trigger map selection (mod select, description and preview)
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
            // keep Ruleset SelectBox sorted while async is running - few entries
            val newItems = (mapCategorySelectBox.items.asSequence() + categoryName)
                .sortedWith(
                    compareBy<String?> { it != sortToTop }
                        .thenBy(collator) { it }
                ).toGdxArray()
            mapCategorySelectBox.withoutChangeEvents {
                items = newItems
                selected = select
            }
        }

        if (mapCategorySelectBox.selected != categoryName) return

        // .. but add the maps themselves as they come
        mapFileSelectBox.withoutChangeEvents {
            items.add(mapWrapper)
            setItems(items) // Call setItems so SelectBox sees the change
        }
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

    private fun FileHandle.isRecentlyModified() = lastModified() > System.currentTimeMillis() - 900000 // 900s = quarter hour
    fun isNotEmpty() = firstMap != null

    fun activateCustomMaps() {
        if (isActivated) {
            if (loadingIcon.isShowing()) return // Default map selection will be handled when background loading finishes
            onCategorySelectBoxChange() // Coming back to this after fully loading make sure selections are OK
        }
        // Code to only run once per NewGameScreen lifetime, after user switches from
        // generated map to custom map, no matter how many times they repeat that
        isActivated = true
        preselectedName = mapParameters.name
        mapCategorySelectBox.onChange { onCategorySelectBoxChange() }
        mapFileSelectBox.onChange { onFileSelectBoxChange() }
        addMapWrappersAsync()
    }

    private fun onCategorySelectBoxChange() {
        val selectedRuleset: String? = mapCategorySelectBox.selected
        val mapFiles = mapWrappers.asSequence()
            .filter { it.getCategoryName() == selectedRuleset }
            .sortedWith(compareBy(collator) { it.toString() })
            .toGdxArray()
        fun getPreselect(): MapWrapper? {
            if (mapFiles.isEmpty) return null
            val modOptionPreselect = RulesetCache[selectedRuleset]
                ?.modOptions?.getMatchingUniques(UniqueType.ModMapPreselection)
                ?.firstOrNull()?.params?.get(0)
            if (modOptionPreselect != null) {
                val preselectFile = mapFiles.firstOrNull { it.fileHandle.name() == modOptionPreselect }
                if (preselectFile != null)
                    return preselectFile
            } else {
                val recent = mapFiles.asSequence()
                    .filter { it.fileHandle.isRecentlyModified() }
                    .maxByOrNull { it.fileHandle.lastModified() }
                val oldestTimestamp = mapFiles.minOfOrNull { it.fileHandle.lastModified() } ?: 0L
                // Do not use most recent if all maps in the category have the same time within a tenth of a second (like a mod unzip does)
                if (recent != null && (recent.fileHandle.lastModified() - oldestTimestamp) > 100 || mapFiles.size == 1)
                    return recent
            }
            val named = mapFiles.firstOrNull { it.fileHandle.name() == preselectedName }
            if (named != null)
                return named
            return mapFiles.first()
        }
        val selectedItem = getPreselect()

        // avoid the overhead of doing a full updateRuleset + updateTables + startMapPreview
        // (all expensive) for something that will be overthrown momentarily
        mapFileSelectBox.withoutChangeEvents {
            items = mapFiles
            selected = selectedItem
        }

        // Now, we want this to *always* run even when setting mapFileSelectBox.selected would
        // not have fired the event (which it won't if the selection is already as asked).
        // Reasons:
        // * Mods that trip validation in updateRuleset
        // * Update description and minimap preview
        onFileSelectBoxChange()
        // In the event described above, we now have: mapFileSelectBox.selected != selectedItem
        // Do NOT try to put back the "bad" preselection!
    }

    private fun onFileSelectBoxChange() {
        cancelBackgroundJobs()
        if (mapFileSelectBox.selection.isEmpty) return
        val selection = mapFileSelectBox.selected

        val mapMods = selection.mapPreview.mapParameters.mods
            .partition { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
        newGameScreen.gameSetupInfo.gameParameters.mods = LinkedHashSet(mapMods.second)
        newGameScreen.gameSetupInfo.gameParameters.baseRuleset = mapMods.first.firstOrNull()
            ?: selection.mapPreview.mapParameters.baseRuleset
        val success = newGameScreen.tryUpdateRuleset(updateUI = true)

        if (success) {
            mapNations = selection.mapPreview.getDeclaredNations()
                .mapNotNull { newGameScreen.ruleset.nations[it] }
                .filter { it.isMajorCiv }
                .toList()
            mapHumanPick = selection.mapPreview.getNationsForHumanPlayer()
                .filter { newGameScreen.ruleset.nations[it]?.isMajorCiv == true }
                .toList().randomOrNull()
        } else {
            mapNations = emptyList()
            mapHumanPick = null
        }

        if (mapNations.isEmpty()) {
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
                val description = map.description
                if (!isActive) return@run
                Concurrency.runOnGLThread {
                    showDescription(description)
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

    private fun showDescription(text: String) {
        descriptionLabel.setText(text)
        descriptionLabelCell
            .height(if (text.isEmpty()) 0f else descriptionLabel.prefHeight)
            .padTop(if (text.isEmpty()) 0f else 10f)
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
        mapNations.asSequence()
            .map { it.name to it.name.tr(hideIcons = true) } // Sort by translation but keep untranslated name
            .sortedWith(
                compareBy<Pair<String, String>>{ it.first != mapHumanPick }
                .thenBy(collator) { it.second }
            ).map { Player(it.first, if (it.first == mapHumanPick) PlayerType.Human else PlayerType.AI) }
            .toCollection(players)
        newGameScreen.playerPickerTable.update()
    }
}
