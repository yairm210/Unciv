package com.unciv.ui.tilegroups

import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.center


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {
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

        if (UnCivGame.Current.viewEntireMapForDebug
                || tileInfo.tileMap.gameInfo.getPlayerCivilization().exploredTiles.contains(tileInfo.position))
            updateCityButton(city, isViewable || UnCivGame.Current.viewEntireMapForDebug) // needs to be before the update so the units will be above the city button

        super.update(isViewable || UnCivGame.Current.viewEntireMapForDebug,
                UnCivGame.Current.settings.showResourcesAndImprovements, showSubmarine)

        yieldGroup.isVisible = !UnCivGame.Current.settings.showResourcesAndImprovements
        if (yieldGroup.isVisible)
            yieldGroup.setStats(tileInfo.getTileStats(UnCivGame.Current.gameInfo.getPlayerCivilization()))

        // order by z index!
        cityImage?.toFront()
        terrainFeatureImage?.toFront()
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
                cityButton = CityButton(city, CameraStageBaseScreen.skin)
                addActor(cityButton)
                toFront() // so this tile is rendered over neighboring tiles
            }

            cityButton!!.update(viewable)
            cityButton!!.center(this)

        }
    }
}