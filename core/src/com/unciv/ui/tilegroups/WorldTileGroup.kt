package com.unciv.ui.tilegroups

import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(internal val worldScreen: WorldScreen, tileInfo: TileInfo, tileSetStrings: TileSetStrings)
    : TileGroup(tileInfo,tileSetStrings) {

    var cityButton: CityButton? = null

    fun selectUnit(unit: MapUnit) {
        if(unit.type.isAirUnit()) return // doesn't appear on map so nothing to select
        val unitImage = if (unit.type.isCivilian()) icons.civilianUnitIcon
        else icons.militaryUnitIcon
        unitImage?.selectUnit()
    }

    fun update(viewingCiv: CivilizationInfo) {
        val city = tileInfo.getCity()

        icons.removePopulationIcon()
        val tileIsViewable = isViewable(viewingCiv)
        if (tileIsViewable && tileInfo.isWorked() && UnCivGame.Current.settings.showWorkedTiles
                && city!!.civInfo.isPlayerCivilization())
            icons.addPopulationIcon()

        val currentPlayerCiv = worldScreen.viewingCiv
        if (UnCivGame.Current.viewEntireMapForDebug
                || currentPlayerCiv.exploredTiles.contains(tileInfo.position))
            updateCityButton(city, tileIsViewable || UnCivGame.Current.viewEntireMapForDebug) // needs to be before the update so the units will be above the city button

        super.update(viewingCiv, UnCivGame.Current.settings.showResourcesAndImprovements)


        // order by z index!
        cityImage?.toFront()
        terrainFeatureOverlayImage?.toFront()
        icons.improvementIcon?.toFront()
        resourceImage?.toFront()
        cityButton?.toFront()
        icons.civilianUnitIcon?.toFront()
        icons.militaryUnitIcon?.toFront()
        fogImage.toFront()
    }


    private fun updateCityButton(city: CityInfo?, viewable: Boolean) {
        if (city == null && cityButton != null)// there used to be a city here but it was razed
        {
            cityButton!!.remove()
            cityButton = null
        }
        if (city != null && tileInfo.isCityCenter()) {
            if (cityButton == null) {
                cityButton = CityButton(city, this, CameraStageBaseScreen.skin)
                cityButtonLayerGroup.addActor(cityButton)
            }

            cityButton!!.update(viewable)
        }
    }

    fun selectCity(city: CityInfo?) : Boolean {
        if (city == null) return false
        return worldScreen.bottomUnitTable.citySelected(city)
    }
}