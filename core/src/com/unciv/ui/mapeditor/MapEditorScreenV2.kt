package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.map.*
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

//todo 2-Level nested Tabbed controls
//todo Tab for base terrains
//todo Tab for terrain features
//todo Flood-fill for base terrain or features
//todo Tab for natural wonders
//todo Tab for Resources
//todo Tab for Rivers
//todo Tool River->to
//todo Tab for Improvements
//todo Group Improvements into 2 tabs?
//todo Tab for Starting Locations
//todo Brushes
//todo Mode: Apply only to fitting tiles / force tile to fit 
//todo Dirty flag on all edits
//todo Tab for Units
//todo allow loading maps from mods (but not saving)
//todo copy/paste tile areas?
//todo should the single step generator routines invalidate/overwrite tileMap's parameters?
//todo Nat Wonder step generator: *New* wonders

class MapEditorScreenV2(map: TileMap? = null): CameraStageBaseScreen() {
    /** The map being edited, with mod list for that map */
    var tileMap: TileMap
    /** Flag indicating the map should be saved */
    var isDirty = false
    /** RuleSet corresponding to [tileMap]'s mod list */
    var ruleset = RulesetCache.getBaseRuleset()

    /** The parameters to use for new maps, and the UI-shown mod list (which can be applied to the active map) */
    var newMapParameters = getDefaultParameters()

    // UI
    var mapHolder: EditorMapHolderV2
    val tabs: TabbedPager
    var tileClickHandler: ((tile: TileInfo)->Unit)? = null

    init {
        tileMap = map ?: TileMap(MapSize.Tiny.radius, ruleset, false)

        mapHolder = newMapHolder()

        tabs = MapEditorMainTabs(this)
        MapEditorToolsDrawer(tabs, stage)

        keyPressDispatcher[KeyCharAndCode.BACK] = this::closeEditor
    }

    companion object {
        private fun getDefaultParameters(): MapParameters {
            val lastSetup = UncivGame.Current.settings.lastGameSetup
                ?: return MapParameters()
            return lastSetup.mapParameters.clone().apply { reseed() }
        }
        fun saveDefaultParameters(parameters: MapParameters) {
            val settings = UncivGame.Current.settings
            val lastSetup = settings.lastGameSetup
                ?: GameSetupInfo().also { settings.lastGameSetup = it }
            lastSetup.mapParameters = parameters.clone()
            settings.save()
        }
    }

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
        return result
    }

    fun loadMap(map: TileMap) {
        mapHolder.remove()
        tileMap = map
        checkAndFixMapSize()
        ruleset = RulesetCache.getComplexRuleset(map.mapParameters.mods)
        mapHolder = newMapHolder()
        isDirty = false
        Gdx.app.postRunnable {
            // Doing this directly freezes the game, despite already running under postRunnable
            tabs.selectPage(0)
        }
    }

    fun applyRuleset(newRuleset: Ruleset) {
        tileMap.mapParameters.mods = newRuleset.mods
        tileMap.ruleset = newRuleset
        ruleset = newRuleset
        ImageGetter.setNewRuleset(newRuleset)
        UncivGame.Current.translations.translationActiveMods = ruleset.mods
        isDirty = true
    }

    internal fun closeEditor() {
        if (!isDirty) return game.setScreen(MainMenuScreen())
        YesNoPopup("Do you want to leave without saving the recent changes?", action = {
            game.setScreen(MainMenuScreen())
        }, this).open()
    }

    private fun checkAndFixMapSize() {
        val areaFromTiles = tileMap.values.size
        tileMap.mapParameters.run {
            val areaFromSize = getArea()
            if (areaFromSize == areaFromTiles) return
            Gdx.app.postRunnable {
                val message = ("Invalid map: Area ([$areaFromTiles]) does not match saved dimensions ([" +
                        displayMapDimensions() + "]).").tr() +
                        "\n" + "The dimensions have now been fixed for you.".tr()
                ToastPopup(message, this@MapEditorScreenV2, 4000L )
            }
            if (shape == MapShape.hexagonal) {
                mapSize = MapSizeNew(HexMath.getHexagonalRadiusForArea(areaFromTiles).toInt())
                return
            }

            // These mimic tileMap.max* without the abs()
            val minLatitude = (tileMap.values.map { it.latitude }.minOrNull() ?: 0f).toInt()
            val minLongitude = (tileMap.values.map { it.longitude }.minOrNull() ?: 0f).toInt()
            val maxLatitude = (tileMap.values.map { it.latitude }.maxOrNull() ?: 0f).toInt()
            val maxLongitude = (tileMap.values.map { it.longitude }.maxOrNull() ?: 0f).toInt()
            mapSize = MapSizeNew((maxLongitude - minLongitude + 1), (maxLatitude - minLatitude + 1) / 2)
        }
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(MapEditorScreenV2(tileMap))
        }
    }
}
