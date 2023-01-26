package com.unciv.ui.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(internal val worldScreen: WorldScreen, tile: Tile, tileSetStrings: TileSetStrings)
    : TileGroup(tile,tileSetStrings) {

    fun update(viewingCiv: Civilization) {

        layerMisc.removePopulationIcon()

        val city = tile.getCity()
        val tileIsViewable = isViewable(viewingCiv)
        val showEntireMap = UncivGame.Current.viewEntireMapForDebug

        // Show population icon overlay (if option is enabled)
        if (tileIsViewable && tile.isWorked() && UncivGame.Current.settings.showWorkedTiles
                && city!!.civInfo == viewingCiv) {
            layerMisc.setNewPopulationIcon()
        }

        // Update city buttons in explored tiles or entire map
        // needs to be before the update so the units will be above the city button
        if (showEntireMap || viewingCiv.hasExplored(tile) || viewingCiv.isSpectator()
                || (worldScreen.viewingCiv.isSpectator() && !worldScreen.fogOfWar)) {
            layerCityButton.update(city, tileIsViewable || showEntireMap)
        }
        // Remove city buttons in unexplored tiles during spectating and fog of war enabled
        else if (worldScreen.viewingCiv.isSpectator() && worldScreen.fogOfWar) {
            layerCityButton.update(null, showEntireMap)
        }

        super.update(viewingCiv, UncivGame.Current.settings.showResourcesAndImprovements, UncivGame.Current.settings.showTileYields)
    }


    override fun clone(): WorldTileGroup = WorldTileGroup(worldScreen, tile , tileSetStrings)
}
