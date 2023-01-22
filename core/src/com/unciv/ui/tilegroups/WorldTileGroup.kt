package com.unciv.ui.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(internal val worldScreen: WorldScreen, tile: Tile, tileSetStrings: TileSetStrings)
    : TileGroup(tile,tileSetStrings) {

    private var cityButton: CityButton? = null

    fun selectUnit(unit: MapUnit) {
        if(unit.baseUnit.movesLikeAirUnits()) return // doesn't appear on map so nothing to select
        val unitImage = if (unit.isCivilian()) icons.civilianUnitIcon
        else icons.militaryUnitIcon
        unitImage?.selectUnit()
    }

    fun update(viewingCiv: Civilization) {
        val city = tile.getCity()

        icons.removePopulationIcon()
        val tileIsViewable = isViewable(viewingCiv)
        val showEntireMap = UncivGame.Current.viewEntireMapForDebug

        if (tileIsViewable && tile.isWorked() && UncivGame.Current.settings.showWorkedTiles
                && city!!.civInfo == viewingCiv)
            icons.addPopulationIcon()
        // update city buttons in explored tiles or entire map
        if (showEntireMap
                || viewingCiv.hasExplored(tile)
                || viewingCiv.isSpectator()
                || (worldScreen.viewingCiv.isSpectator() && !worldScreen.fogOfWar)) {
            updateCityButton(city, tileIsViewable || showEntireMap) // needs to be before the update so the units will be above the city button
        } else if (worldScreen.viewingCiv.isSpectator() && worldScreen.fogOfWar) {
            // remove city buttons in unexplored tiles during spectating and fog of war enabled
            updateCityButton(null, showEntireMap)
        }

        super.update(viewingCiv, UncivGame.Current.settings.showResourcesAndImprovements, UncivGame.Current.settings.showTileYields)
    }


    private fun updateCityButton(city: City?, viewable: Boolean) {
        if (city == null && cityButton != null)// there used to be a city here but it was razed
        {
            cityButton!!.remove()
            cityButton = null
        }
        if (city != null && tile.isCityCenter()) {
            if (cityButton == null) {
                cityButton = CityButton(city, this)
                cityButtonLayerGroup.addActor(cityButton)
            }

            cityButton!!.update(viewable)
        }
    }

    fun selectCity(city: City?) : Boolean {
        if (city == null) return false
        return worldScreen.bottomUnitTable.citySelected(city)
    }

    override fun clone(): WorldTileGroup = WorldTileGroup(worldScreen, tile , tileSetStrings)
}
