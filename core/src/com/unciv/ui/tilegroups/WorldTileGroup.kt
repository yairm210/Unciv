package com.unciv.ui.tilegroups

import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.center
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(internal val worldScreen: WorldScreen, tileInfo: TileInfo) : TileGroup(tileInfo) {

    var cityButton: CityButton? = null

    fun selectUnit(unit: MapUnit) {
        val unitImage = if (unit.type.isCivilian()) civilianUnitImage
        else militaryUnitImage
        unitImage?.selectUnit()
    }

    init {
        yieldGroup.center(this)
        yieldGroup.moveBy(-22f, 0f)
    }


    fun update(isViewable: Boolean, showSubmarine: Boolean) {
        val city = tileInfo.getCity()

        removePopulationIcon()
        if (isViewable && tileInfo.isWorked() && UnCivGame.Current.settings.showWorkedTiles
                && city!!.civInfo.isPlayerCivilization())
            addPopulationIcon()

        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        if (UnCivGame.Current.viewEntireMapForDebug
                || currentPlayerCiv.exploredTiles.contains(tileInfo.position))
            updateCityButton(city, isViewable || UnCivGame.Current.viewEntireMapForDebug) // needs to be before the update so the units will be above the city button

        super.update(isViewable || UnCivGame.Current.viewEntireMapForDebug,
                UnCivGame.Current.settings.showResourcesAndImprovements, showSubmarine)

        yieldGroup.isVisible = !UnCivGame.Current.settings.showResourcesAndImprovements
        if (yieldGroup.isVisible)
            yieldGroup.setStats(tileInfo.getTileStats(currentPlayerCiv))

        // order by z index!
        cityImage?.toFront()
        terrainFeatureOverlayImage?.toFront()
        yieldGroup.toFront()
        improvementImage?.toFront()
        resourceImage?.toFront()
        cityButton?.toFront()
        civilianUnitImage?.toFront()
        militaryUnitImage?.toFront()
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
        return worldScreen.bottomBar.unitTable.citySelected(city)
    }
}