package com.unciv.ui.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(internal val worldScreen: WorldScreen, tileInfo: TileInfo, tileSetStrings: TileSetStrings)
    : TileGroup(tileInfo,tileSetStrings) {

    private var cityButton: CityButton? = null

    fun selectUnit(unit: MapUnit) {
        if(unit.baseUnit.movesLikeAirUnits()) return // doesn't appear on map so nothing to select
        val unitImage = if (unit.isCivilian()) icons.civilianUnitIcon
        else icons.militaryUnitIcon
        unitImage?.selectUnit()
    }

    fun update(viewingCiv: CivilizationInfo) {
        val city = tileInfo.getCity()

        icons.removePopulationIcon()
        val tileIsViewable = isViewable(viewingCiv)
        val showEntireMap = UncivGame.Current.viewEntireMapForDebug

        if (tileIsViewable && tileInfo.isWorked() && UncivGame.Current.settings.showWorkedTiles
                && city!!.civInfo == viewingCiv)
            icons.addPopulationIcon()
        // update city buttons in explored tiles or entire map
        if (showEntireMap
                || viewingCiv.exploredTiles.contains(tileInfo.position)
                || viewingCiv.isSpectator()
                || (worldScreen.viewingCiv.isSpectator() && !worldScreen.fogOfWar)) {
            updateCityButton(city, tileIsViewable || showEntireMap) // needs to be before the update so the units will be above the city button
        } else if (worldScreen.viewingCiv.isSpectator() && worldScreen.fogOfWar) {
            // remove city buttons in unexplored tiles during spectating and fog of war enabled
            updateCityButton(null, showEntireMap)
        }

        super.update(viewingCiv, UncivGame.Current.settings.showResourcesAndImprovements, UncivGame.Current.settings.showTileYields)
    }


    private fun updateCityButton(city: CityInfo?, viewable: Boolean) {
        if (city == null && cityButton != null)// there used to be a city here but it was razed
        {
            cityButton!!.remove()
            cityButton = null
        }
        if (city != null && tileInfo.isCityCenter()) {
            if (cityButton == null) {
                cityButton = CityButton(city, this)
                cityButtonLayerGroup.addActor(cityButton)
            }

            cityButton!!.update(viewable)
        }
    }

    fun selectCity(city: CityInfo?) : Boolean {
        if (city == null) return false
        return worldScreen.bottomUnitTable.citySelected(city)
    }

    override fun clone(): WorldTileGroup = WorldTileGroup(worldScreen, tileInfo , tileSetStrings)
}
