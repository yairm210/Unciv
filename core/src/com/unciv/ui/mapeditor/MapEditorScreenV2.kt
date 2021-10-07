package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.utils.*

//todo inspection cursor
//todo Tabbed tools
//todo 2-Level nested Tabbed controls
//todo View-Generate-Edit-Load-Save-Mods-Options?
//todo Tab for map generation
//todo Tab for map generator single routines
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
//todo Tab for mod selection
//todo Brushes
//todo Mode: Apply only to fitting tiles / force tile to fit 
//todo Save prompt / dirty flag
//todo Map statistics tab
//todo Tab for Units


class MapEditorScreenV2: CameraStageBaseScreen() {
    var mapName = ""
    var mapHolder: EditorMapHolderV2
    val tabs: TabbedPager
    var tileMap: TileMap
    var ruleset = RulesetCache.getBaseRuleset()

    var tileClickHandler: ((tile: TileInfo)->Unit)? = null

    init {
        tileMap = TileMap(MapSize.Tiny.radius, ruleset, false)
        tileMap.setTransients(ruleset,false)
        tileMap.setStartingLocationsTransients()

        mapHolder = EditorMapHolderV2(this, tileMap, ruleset) {
            tileClickHandler?.invoke(it)
        }
        stage.addActor(mapHolder)
        stage.scrollFocus = mapHolder

        tabs = MapEditorMainTabs(this)
//        val splitPane = ClickableSplitPane(tabs, skin)
//        stage.addActor(splitPane)
        MapEditorToolsDrawer(tabs, stage)
    }

}
