package com.unciv.ui.screens.mapeditorscreen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.unciv.UncivGame
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto
import com.unciv.ui.components.input.KeyboardPanningListener
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.ImageWithCustomSize
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.mapeditorscreen.tabs.MapEditorOptionsTab
import com.unciv.ui.screens.worldscreen.ZoomButtonPair
import com.unciv.utils.Concurrency
import com.unciv.utils.Dispatcher
import com.unciv.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job


//todo normalize properly

//todo functional Tab for Units (empty Tab is prepared but commented out in MapEditorEditTab.AllEditSubTabs)
//todo copy/paste tile areas? (As tool tab, brush sized, floodfill forbidden, tab displays copied area)
//todo Synergy with Civilopedia for drawing loose tiles / terrain icons
//todo left-align everything so a half-open drawer is more useful
//todo combined brush
//todo Tooltips for Edit items with info on placeability? Place this info as Brush description? In Expander?
//todo Civilopedia links from edit items by right-click/long-tap?
//todo Mod tab change base ruleset - disableAllCheckboxes - instead some intelligence to leave those mods on that stay compatible?
//todo The setSkin call in newMapHolder belongs in ImageGetter.setNewRuleset and should be intelligent as resetFont is expensive and the probability a mod touched a few EmojiIcons is low
//todo new brush: remove natural wonder
//todo Nat Wonder step generator: Needs tweaks to avoid placing duplicates or wonders too close together
//todo Music? Different suffix? Off? 20% Volume?
//todo See #6694 - allow adding tiles to a map (1 cell all around on hex? world-wrapped hex?? all around on rectangular? top bottom only on world-wrapped??)
//todo move map copy&paste to save/load??

class MapEditorScreen(map: TileMap? = null) : BaseScreen(), RecreateOnResize {
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
    val descriptionTextField = UncivTextField("Enter a description for the users of this map")

    private val highlightedTileGroups = mutableListOf<TileGroup>()

    // Control of background jobs - make them cancel on context changes like exit editor or resize screen
    private val jobs = ArrayDeque<Job>(3)

    init {
        if (map == null) {
            ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
            tileMap = TileMap(MapSize.Tiny.radius, ruleset, false).apply {
                mapParameters.mapSize = MapSize.Tiny
            }
        } else {
            ruleset = map.ruleset ?: RulesetCache.getComplexRuleset(map.mapParameters)
            tileMap = map
            descriptionTextField.text = map.description
        }

        mapHolder = newMapHolder() // will set up ImageGetter and translations, and all dirty flags
        isDirty = false
        descriptionTextField.onChange { isDirty = true }

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

    override fun getCivilopediaRuleset() = ruleset

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
            val gameParameters = settings.lastGameSetup?.gameParameters ?: GameParameters()
            settings.lastGameSetup = GameSetupInfo(gameParameters, parameters)
            settings.save()
        }
    }

    fun getToolsWidth() = stage.width * 0.4f

    fun setWorldWrap(newValue: Boolean) {
        if (newValue == tileMap.mapParameters.worldWrap) return
        setWorldWrapFixOddWidth(newValue)
        if (newValue && overlayFile != null) {
            overlayFile = null
            ToastPopup("An overlay image is incompatible with world wrap and was deactivated.", stage, 4000)
            tabs.options.update()
        }
        recreateMapHolder()
    }

    private fun setWorldWrapFixOddWidth(newValue: Boolean) = tileMap.mapParameters.run {
        // Turning *off* WW and finding an odd width means it must have been rounded
        // down by the TileMap constructor - fix so we can turn it back on later
        if (worldWrap && mapSize.width % 2 != 0 && shape == MapShape.rectangular)
            mapSize.width--
        worldWrap = newValue
    }

    private fun recreateMapHolder(actionWhileRemoved: ()->Unit = {}) {
        val savedScale = mapHolder.scaleX
        clearOverlayImages()
        mapHolder.remove()
        actionWhileRemoved()
        mapHolder = newMapHolder()
        mapHolder.zoom(savedScale)
    }

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

        val newHolder = EditorMapHolder(this, tileMap) {
            tileClickHandler?.invoke(it)
        }
        newHolder.mapPanningSpeed = UncivGame.Current.settings.mapPanningSpeed
        enableKeyboardPanningListener(newHolder, true)
        if (Gdx.app.type == Application.ApplicationType.Desktop)
            newHolder.isAutoScrollEnabled = UncivGame.Current.settings.mapAutoScroll

        addOverlayToMapHolder(newHolder.actor as Group)  // That's the initially empty Group ZoomableScrollPane allocated

        stage.root.addActorAt(0, newHolder)
        stage.scrollFocus = newHolder

        isDirty = true
        modsTabNeedsRefresh = true
        editTabsNeedRefresh = true
        naturalWondersNeedRefresh = true

        if (UncivGame.Current.settings.showZoomButtons) {
            zoomController = ZoomButtonPair(newHolder)
            zoomController!!.setPosition(10f, 10f)
            stage.addActor(zoomController)
        }

        return newHolder
    }

    internal fun enableKeyboardPanningListener(holder: EditorMapHolder? = null, enable: Boolean) {
        for (oldPanningListener in stage.root.listeners.filterIsInstance<KeyboardPanningListener>()) {
            stage.removeListener(oldPanningListener)  // otherwise they accumulate
            oldPanningListener.dispose()
        }
        if (!enable) return
        stage.addListener(KeyboardPanningListener(holder ?: mapHolder, allowWASD = false))
    }

    // We contain a map...
    override fun getShortcutDispatcherVetoer() = KeyShortcutDispatcherVeto.createTileGroupMapDispatcherVetoer()

    fun loadMap(map: TileMap, newRuleset: Ruleset? = null, selectPage: Int = 0) {
        clearOverlayImages()
        mapHolder.remove()
        tileMap = map
        descriptionTextField.text = map.description
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
        recreateMapHolder {
            tileMap.mapParameters.baseRuleset = newBaseRuleset
            tileMap.mapParameters.mods = mods
            tileMap.ruleset = newRuleset
            ruleset = newRuleset
        }
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

    private fun askIfDirty(question: String, confirmText: String, isConfirmPositive: Boolean = false, action: ()->Unit) {
        if (!isDirty) return action()
        ConfirmPopup(screen = this, question, confirmText, isConfirmPositive, action = action).open()
    }
    fun askIfDirtyForLoad(action: ()->Unit) = askIfDirty(
        "Do you want to load another map without saving the recent changes?",
        "Load map", action = action)

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

    //region Overlay Image

    // To support world wrap with an overlay, one could maybe do up to tree versions of the same
    // Image tiled side by side (therefore "clearOverlayImages"), they _could_ use the same Texture
    // instance - that part works. But how to position and clip them properly escapes me - better
    // coders are welcome to try. To work around, we simply turn world wrap off when an overlay is
    // loaded, and allow to freely turn WW on and off. After all, the distinction becomes relevant
    // *only* when a game is started, units move, and tile neighbors get a meaning.

    private var imageOverlay: Image? = null

    internal var overlayFile: FileHandle? = null
        set(value) {
            field = value
            overlayFileChanged(value)
        }

    internal var overlayAlpha = 0.33f
        set(value) {
            field = value
            overlayAlphaChanged(value)
        }

    private fun clearOverlayImages() {
        val oldImage = imageOverlay ?: return
        imageOverlay = null
        oldImage.remove()
        (oldImage.drawable as? TextureRegionDrawable)?.region?.texture?.dispose()
    }

    private fun overlayFileChanged(value: FileHandle?) {
        clearOverlayImages()
        if (value == null) return
        if (tileMap.mapParameters.worldWrap) {
            setWorldWrapFixOddWidth(false)
            ToastPopup("World wrap is incompatible with an overlay and was deactivated.", stage, 4000)
            tabs.options.update()
        }
        recreateMapHolder()
    }

    private fun overlayAlphaChanged(value: Float) {
        imageOverlay?.color?.a = value
    }

    private fun addOverlayToMapHolder(newHolderContent: Group) {
        clearOverlayImages()
        if (overlayFile == null) return

        try {
            val texture = Texture(overlayFile)
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            imageOverlay = ImageWithCustomSize(TextureRegion(texture))
        } catch (ex: Throwable) {
            Log.error("Invalid overlay image", ex)
            overlayFile = null
            ToastPopup("Invalid overlay image", stage, 3000)
            tabs.options.update()
            return
        }

        imageOverlay?.apply {
            touchable = Touchable.disabled
            setFillParent(true)
            color.a = overlayAlpha
            newHolderContent.addActor(this)
        }
    }
    //endregion
}
