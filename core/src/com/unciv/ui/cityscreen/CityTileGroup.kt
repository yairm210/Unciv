package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.centerX

class CityTileGroup(private val city: CityInfo, tileInfo: TileInfo) : TileGroup(tileInfo) {


    init {
        addActor(yieldGroup)
        if (city.location == tileInfo.position) {
            populationImage = ImageGetter.getImage("StatIcons/City_Center_(Civ6).png")
            addActor(populationImage)
        }
    }

    fun update() {
        val canSeeTile = UnCivGame.Current.viewEntireMapForDebug
                || city.civInfo.viewableTiles.contains(tileInfo)
        val showSubmarine = UnCivGame.Current.viewEntireMapForDebug
                || city.civInfo.viewableInvisibleUnitsTiles.contains(tileInfo)
                || (!tileInfo.hasEnemySubmarine())
        super.update(canSeeTile,true, showSubmarine)

        updatePopulationImage()
        if (improvementImage != null) improvementImage!!.setColor(1f, 1f, 1f, 0.5f)
        if (resourceImage != null) resourceImage!!.setColor(1f, 1f, 1f, 0.5f)
        if (cityImage != null) cityImage!!.setColor(1f, 1f, 1f, 0.5f)
        if (civilianUnitImage != null) civilianUnitImage!!.setColor(1f, 1f, 1f, 0.5f)
        if (militaryUnitImage!= null) militaryUnitImage!!.setColor(1f, 1f, 1f, 0.5f)
        updateYieldGroup()
    }

    private fun updateYieldGroup() {
        yieldGroup.setStats(tileInfo.getTileStats(city, city.civInfo.gameInfo.getCurrentPlayerCivilization()))
        yieldGroup.setOrigin(Align.center)
        yieldGroup.setScale(0.7f)
        yieldGroup.toFront()
        yieldGroup.centerX(this)
        yieldGroup.y= height * 0.25f - yieldGroup.height / 2
    }

    private fun updatePopulationImage() {
        if (populationImage != null) {
            populationImage!!.setSize(30f, 30f)
            populationImage!!.setPosition(width / 2 - populationImage!!.width / 2,
                    height * 0.85f - populationImage!!.height / 2)

            if (tileInfo.isWorked() || city.canAcquireTile(tileInfo)) {
                populationImage!!.color = Color.WHITE
            }
            else if(!tileInfo.isCityCenter()){
                populationImage!!.color = Color.GRAY.cpy().apply { a=0.5f }
            }

            populationImage!!.toFront()
        }
    }


}