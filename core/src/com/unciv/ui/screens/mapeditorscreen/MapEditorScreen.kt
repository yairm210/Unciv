package com.unciv.ui.screens.mapeditorscreen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.UncivGame
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.mapeditorscreen.tabs.MapEditorOptionsTab
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.KeyboardPanningListener
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.worldscreen.ZoomButtonPair
import com.unciv.utils.Concurrency
import com.unciv.utils.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job


//todo normalize properly

//todo Remove "Area: [amount] tiles, [amount2] continents/islands = " after 2022-07-01
//todo functional Tab for Units (empty Tab is prepared but commented out in MapEditorEditTab.AllEditSubTabs)
//todo copy/paste tile areas? (As tool tab, brush sized, floodfill forbidden, tab displays copied area)
//todo Synergy with Civilopedia for drawing loose tiles / terrain icons
//todo left-align everything so a half-open drawer is more useful
//todo combined brush
//todo New function `convertTerrains` is auto-run after rivers the right decision for step-wise generation? Will paintRiverFromTo need the same? Will painting manually need the conversion?
//todo Tooltips for Edit items with info on placeability? Place this info as Brush description? In Expander?
//todo Civilopedia links from edit items by right-click/long-tap?
//todo Mod tab change base ruleset - disableAllCheckboxes - instead some intelligence to leave those mods on that stay compatible?
//todo The setSkin call in newMapHolder belongs in ImageGetter.setNewRuleset and should be intelligent as resetFont is expensive and the probability a mod touched a few EmojiIcons is low
//todo new brush: remove natural wonder
//todo Nat Wonder step generator: Needs tweaks to avoid placing duplicates or wonders too close together
//todo Music? Different suffix? Off? 20% Volume?
//todo See #6694 - allow adding tiles to a map (1 cell all around on hex? world-wrapped hex?? all around on rectangular? top bottom only on world-wrapped??)
//todo move map copy&paste to save/load??

class MapEditorScreen(map: TileMap? = null): BaseScreen(), RecreateOnResize {
    /** The map being edited, with mod list for that map */
    var tileMap: TileMap
    /** Flag indicating the map should be saved */
    var isDirty = false

    /** The parameters to use for new maps, and the UI-shown mod list (which can be applied to the active map) */
    val newMapParameters = getDefaultParameters()

    /** RuleSet corresponding to [tileMap]'s mod list */
    var ruleset: Ruleset

    /** Set only by loading a map from file and used only by mods tab */
    var modsTabNeedsRefresh = false
    /** Set by loading a map or changing ruleset and used only by the edit tabs */
    var editTabsNeedRefresh = false
    /** Set on load, generate or paint natural wonder - used to read nat wonders for the view tab */
    var naturalWondersNeedRefresh = false
    /** Copy of same field in [MapEditorOptionsTab] */
    var tileMatchFuzziness = MapEditorOptionsTab.TileMatchFuzziness.CompleteMatch

    // UI
    var mapHolder: EditorMapHolder
    val tabs: MapEditorMainTabs
    var tileClickHandler: ((tile: Tile)->Unit)? = null
    private var zoomController: ZoomButtonPair? = null

    private val highlightedTileGroups = mutableListOf<TileGroup>()

    // Control of background jobs - make them cancel on context changes like exit editor or resize screen
    private val jobs = ArrayDeque<Job>(3)

    init {
        if (map == null) {
            ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
            tileMap = TileMap(MapSize.Tiny.radius, ruleset, false).apply {
                mapParameters.mapSize = MapSizeNew(MapSize.Tiny)
            }
        } else {
            ruleset = map.ruleset ?: RulesetCache.getComplexRuleset(map.mapParameters)
            tileMap = map
        }

        mapHolder = newMapHolder() // will set up ImageGetter and translations, and all dirty flags
        isDirty = false

        tabs = MapEditorMainTabs(this)
        MapEditorToolsDrawer(tabs, stage, mapHolder)

        // The top level pager assigns its own key bindings, but making nested TabbedPagers bind keys
        // so all levels select to show the tab in question is too complex. Sub-Tabs need to maintain
        // the key binding here and the used key in their `addPage`s again for the tooltips.
        fun selectGeneratePage(index: Int) { tabs.run { selectPage(1); generate.selectPage(index) } }
        globalShortcuts.add(KeyCharAndCode.ctrl('n')) { selectGeneratePage(0) }
        globalShortcuts.add(KeyCharAndCode.ctrl('g')) { selectGeneratePage(1) }
        globalShortcuts.add(KeyCharAndCode.BACK) { closeEditor() }
    }

    companion object {
        private fun getDefaultParameters(): MapParameters {
            val lastSetup = UncivGame.Current.settings.lastGameSetup
                ?: return MapParameters()
            return lastSetup.mapParameters.clone().apply {
                reseed()
                mods.removeAll(RulesetCache.getSortedBaseRulesets().toSet())
            }
        }
        fun saveDefaultParameters(parameters: MapParameters) {
            val settings = UncivGame.Current.settings
            val lastSetup = settings.lastGameSetup
                ?: GameSetupInfo().also { settings.lastGameSetup = it }
            lastSetup.mapParameters = parameters.clone()
            settings.save()
        }
    }

    fun getToolsWidth() = stage.width * 0.4f

    private fun newMapHolder(): EditorMapHolder {
        ImageGetter.setNewRuleset(ruleset)
        // setNewRuleset is missing some graphics - those "EmojiIcons"&co already rendered as font characters
        // so to get the "Water" vs "Gold" icons when switching between Deciv and Vanilla to render properly,
        // we will need to ditch the already rendered font glyphs. Fonts.resetFont is not sufficient,
        // the skin seems to clone a separate copy of the Fonts singleton, proving that kotlin 'object'
        // are not really guaranteed to exist in one instance only.
        setSkin()

        tileMap.setTransients(ruleset,false)
        tileMap.setStartingLocationsTransients()
        UncivGame.Current.translations.translationActiveMods = ruleset.mods

        val result = EditorMapHolder(this, tileMap) {
            tileClickHandler?.invoke(it)
        }
        for (oldPanningListener in stage.root.listeners.filterIsInstance<KeyboardPanningListener>())
            stage.removeListener(oldPanningListener)  // otherwise they accumulate
        result.mapPanningSpeed = UncivGame.Current.settings.mapPanningSpeed
        stage.addListener(KeyboardPanningListener(result, allowWASD = false))
        if (Gdx.app.type == Application.ApplicationType.Desktop)
            result.isAutoScrollEnabled = UncivGame.Current.settings.mapAutoScroll

        stage.root.addActorAt(0, result)
        stage.scrollFocus = result

        isDirty = true
        modsTabNeedsRefresh = true
        editTabsNeedRefresh = true
        naturalWondersNeedRefresh = true

        if (UncivGame.Current.settings.showZoomButtons) {
            zoomController = ZoomButtonPair(result)
            zoomController!!.setPosition(10f, 10f)
            stage.addActor(zoomController)
        }

        return result
    }

    fun loadMap(map: TileMap, newRuleset: Ruleset? = null, selectPage: Int = 0) {
        mapHolder.remove()
        tileMap = map
        ruleset = newRuleset ?: RulesetCache.getComplexRuleset(map.mapParameters)
        mapHolder = newMapHolder()
        isDirty = false
        Gdx.input.inputProcessor = stage
        tabs.selectPage(selectPage)  // must be done _after_ resetting inputProcessor!
    }

    fun getMapCloneForSave() =
        tileMap.clone().apply {
            setTransients(setUnitCivTransients = false)
        }

    fun applyRuleset(newRuleset: Ruleset, newBaseRuleset: String, mods: LinkedHashSet<String>) {
        mapHolder.remove()
        tileMap.mapParameters.baseRuleset = newBaseRuleset
        tileMap.mapParameters.mods = mods
        tileMap.ruleset = newRuleset
        ruleset = newRuleset
        mapHolder = newMapHolder()
        modsTabNeedsRefresh = false
    }

    internal fun closeEditor() {
        askIfDirty(
            "Do you want to leave without saving the recent changes?",
            "Leave"
        ) {
            cancelJobs()
            game.popScreen()
        }
    }

    fun askIfDirty(question: String, confirmText: String, isConfirmPositive: Boolean = false, action: ()->Unit) {
        if (!isDirty) return action()
        ConfirmPopup(screen = this, question, confirmText, isConfirmPositive, action = action).open()
    }

    fun hideSelection() {
        for (group in highlightedTileGroups)
            group.layerOverlay.hideHighlight()
        highlightedTileGroups.clear()
    }
    fun highlightTile(tile: Tile, color: Color = Color.WHITE) {
        val group = mapHolder.tileGroups[tile] ?: return
        group.layerOverlay.showHighlight(color)
        highlightedTileGroups.add(group)
    }
    fun updateTile(tile: Tile) {
        mapHolder.tileGroups[tile]!!.update()
    }
    fun updateAndHighlight(tile: Tile, color: Color = Color.WHITE) {
        updateTile(tile)
        highlightTile(tile, color)
    }

    override fun recreate(): BaseScreen {
        cancelJobs()
        return MapEditorScreen(tileMap)
    }

    override fun dispose() {
        cancelJobs()
        super.dispose()
    }

    fun startBackgroundJob(
        name: String,
        isDaemon: Boolean = true,
        block: suspend CoroutineScope.() -> Unit
    ) {
        val scope = CoroutineScope(if (isDaemon) Dispatcher.DAEMON else Dispatcher.NON_DAEMON)
        val newJob = Concurrency.run(name, scope, block)
        jobs.add(newJob)
        newJob.invokeOnCompletion {
            jobs.remove(newJob)
        }
    }

    private fun cancelJobs() {
        for (job in jobs)
            job.cancel()
        jobs.clear()
    }
}
