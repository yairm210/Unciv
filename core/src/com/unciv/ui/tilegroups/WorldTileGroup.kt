package com.unciv.ui.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(internal val worldScreen: WorldScreen, tile: Tile, tileSetStrings: TileSetStrings)
    : TileGroup(tile,tileSetStrings) {

    override fun update(viewingCiv: Civilization?) {

        layerMisc.removePopulationIcon()

        val city = tile.getCity()
        val tileIsViewable = isViewable(viewingCiv!!)

        // Show population icon overlay (if option is enabled)
        if (tileIsViewable && tile.isWorked() && UncivGame.Current.settings.showWorkedTiles
                && city!!.civ == viewingCiv) {
            layerMisc.setNewPopulationIcon()
        }

        super.update(viewingCiv)
    }


    override fun clone(): WorldTileGroup = WorldTileGroup(worldScreen, tile , tileSetStrings)
}
