package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.map.*
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.*


//+todo adapt to new BaseRuleset way of things - currently generate will use G&K / edit will use Vanilla
//todo Loading a map should set the mod checkboxes from the file
//Todo mod tab: "Revert" and better Explanation
//todo Mod check on mod tab should respect _selected_ not loaded base ruleset
//todo Deciv Gold/Water: Font symbol loaded and stays when going Vanilla, but translation never shows

//todo normalize properly

//todo check completeness of translation templates
//todo height of the resources+improvement scroller wrong
//todo width of the tabs sometimes derails (brush line getting longer than initial width)
//todo drag painting - migrate from old editor
//todo Nat Wonder step generator: *New* wonders?
//todo functional Tab for Units
//todo allow loading maps from mods (but not saving)
//todo copy/paste tile areas? (As tool tab, brush sized, floodfill forbidden, tab displays copied area)
//todo Synergy with Civilopedia for drawing loose tiles / terrain icons
//todo left-align everything so a half-open drawer is more useful
//todo combined brush
//todo Load should check isDirty before discarding and replacing the current map
//todo New function `convertTerrains` is auto-run after rivers the right decision for step-wise generation? Will paintRiverFromTo need the same? Will painting manually need the conversion?
//todo work in Simon's changes to continent/landmass
//todo work in Simon's regions - check whether generate and store or discard is the way
//todo Regions: If relevant, view and possibly work in Simon's colored visualization
//todo Civilopedia links from View tab
//todo Tooltips for Edit items with info on placeability? Place this info as Brush description? In Expander?
//todo Civilopedia links from edit items by right-click/long-tap?

class MapEditorScreenV2(map: TileMap? = null): BaseScreen() {
    /** The map being edited, with mod list for that map */
    var tileMap: TileMap
    /** Flag indicating the map should be saved */
    var isDirty = false

    /** The parameters to use for new maps, and the UI-shown mod list (which can be applied to the active map) */
    var newMapParameters = getDefaultParameters()

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
    var mapHolder: EditorMapHolderV2
    val tabs: MapEditorMainTabs
    var tileClickHandler: ((tile: TileInfo)->Unit)? = null

    private val highlightedTileGroups = mutableListOf<TileGroup>()

    init {
        if (map == null) {
            ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
            tileMap = TileMap(MapSize.Tiny.radius, ruleset, false).apply {
                mapParameters.mapSize = MapSizeNew(MapSize.Tiny)
            }
        } else {
            ruleset = map.ruleset ?:
                RulesetCache.getComplexRuleset(map.mapParameters.mods, map.mapParameters.baseRuleset)
            tileMap = map
        }

        mapHolder = newMapHolder() // will set up ImageGetter and translations, and all dirty flags
        isDirty = false

        tabs = MapEditorMainTabs(this)
        MapEditorToolsDrawer(tabs, stage)

        // The top level pager assigns its own key bindings, but making nested TabbedPagers bind keys
        // so all levels select to show the tab in question is too complex. Sub-Tabs need to maintain
        // the key binding here and the used key in their `addPage`s again for the tooltips.
        fun selectGeneratePage(index: Int) { tabs.run { selectPage(1); generate.selectPage(index) } }
        keyPressDispatcher[KeyCharAndCode.ctrl('n')] = { selectGeneratePage(0) }
        keyPressDispatcher[KeyCharAndCode.ctrl('g')] = { selectGeneratePage(1) }
        keyPressDispatcher[KeyCharAndCode.BACK] = this::closeEditor
        keyPressDispatcher.setCheckpoint()
    }

    companion object {
        private fun getDefaultParameters(): MapParameters {
            val lastSetup = UncivGame.Current.settings.lastGameSetup
                ?: return MapParameters()
            return lastSetup.mapParameters.clone().apply {
                reseed()
                mods.removeAll(RulesetCache.getSortedBaseRulesets())
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

    private fun newMapHolder(): EditorMapHolderV2 {
        ImageGetter.setNewRuleset(ruleset)
        tileMap.setTransients(ruleset,false)
        tileMap.setStartingLocationsTransients()
        UncivGame.Current.translations.translationActiveMods = ruleset.mods

        val result = EditorMapHolderV2(this, tileMap, ruleset) {
            tileClickHandler?.invoke(it)
        }

        stage.root.addActorAt(0, result)
        stage.scrollFocus = result

        isDirty = true
        modsTabNeedsRefresh = true
        editTabsNeedRefresh = true
        naturalWondersNeedRefresh = true
        return result
    }

    fun loadMap(map: TileMap, newRuleset: Ruleset? = null) {
        mapHolder.remove()
        tileMap = map
        checkAndFixMapSize()
        ruleset = newRuleset ?:
            RulesetCache.getComplexRuleset(map.mapParameters.mods, map.mapParameters.baseRuleset)
        mapHolder = newMapHolder()
        isDirty = false
        Gdx.app.postRunnable {
            // Doing this directly freezes the game, despite loadMap already running under postRunnable
            tabs.selectPage(0)
        }
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
        if (!isDirty) return game.setScreen(MainMenuScreen())
        YesNoPopup("Do you want to leave without saving the recent changes?", action = {
            game.setScreen(MainMenuScreen())
        }, screen = this, restoreDefault = {
            keyPressDispatcher[KeyCharAndCode.BACK] = this::closeEditor
        }).open()
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
            ToastPopup(message, this@MapEditorScreenV2, 4000L )
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

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(MapEditorScreenV2(tileMap))
        }
    }
}
