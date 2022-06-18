package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.RecreateOnResize
import com.unciv.ui.worldscreen.ZoomButtonPair


//todo normalize properly

//todo Remove "Area: [amount] tiles, [amount2] continents/islands = " after 2022-07-01
//todo Direct Strategic Resource abundance control
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
//todo "random nation" starting location (maybe no new internal representation = all major nations)
//todo Nat Wonder step generator: Needs tweaks to avoid placing duplicates or wonders too close together
//todo Music? Different suffix? Off? 20% Volume?
//todo See #6694 - allow placing Barbarian encampments (problem: dead on game start - BarbarianManager.encampments)
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
    var tileClickHandler: ((tile: TileInfo)->Unit)? = null
    private var zoomController: ZoomButtonPair? = null

    private val highlightedTileGroups = mutableListOf<TileGroup>()

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
        checkAndFixMapSize()
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
            game.popScreen()
        }
    }

    fun askIfDirty(question: String, confirmText: String, isConfirmPositive: Boolean = false, action: ()->Unit) {
        if (!isDirty) return action()
        ConfirmPopup(screen = this, question, confirmText, isConfirmPositive, action = action).open()
    }

    fun hideSelection() {
        for (group in highlightedTileGroups)
            group.hideHighlight()
        highlightedTileGroups.clear()
    }
    fun highlightTile(tile: TileInfo, color: Color = Color.WHITE) {
        for (group in mapHolder.tileGroups[tile] ?: return) {
            group.showHighlight(color)
            highlightedTileGroups.add(group)
        }
    }
    fun updateTile(tile: TileInfo) {
        mapHolder.tileGroups[tile]!!.forEach {
            it.update()
        }
    }
    fun updateAndHighlight(tile: TileInfo, color: Color = Color.WHITE) {
        updateTile(tile)
        highlightTile(tile, color)
    }

    private fun checkAndFixMapSize() {
        val areaFromTiles = tileMap.values.size
        val params = tileMap.mapParameters
        val areaFromSize = params.getArea()
        if (areaFromSize == areaFromTiles) return

        Gdx.app.postRunnable {
            val message = ("Invalid map: Area ([$areaFromTiles]) does not match saved dimensions ([" +
                    params.displayMapDimensions() + "]).").tr() +
                    "\n" + "The dimensions have now been fixed for you.".tr()
            ToastPopup(message, this@MapEditorScreen, 4000L )
        }

        if (params.shape == MapShape.hexagonal) {
            params.mapSize = MapSizeNew(HexMath.getHexagonalRadiusForArea(areaFromTiles).toInt())
            return
        }

        // These mimic tileMap.max* without the abs()
        val minLatitude = (tileMap.values.map { it.latitude }.minOrNull() ?: 0f).toInt()
        val minLongitude = (tileMap.values.map { it.longitude }.minOrNull() ?: 0f).toInt()
        val maxLatitude = (tileMap.values.map { it.latitude }.maxOrNull() ?: 0f).toInt()
        val maxLongitude = (tileMap.values.map { it.longitude }.maxOrNull() ?: 0f).toInt()
        params.mapSize = MapSizeNew((maxLongitude - minLongitude + 1), (maxLatitude - minLatitude + 1) / 2)
    }

    override fun recreate(): BaseScreen = MapEditorScreen(tileMap)
}
