package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.ImageGetter

class CityTileGroup(private val city: CityInfo, tileInfo: TileInfo) : TileGroup(tileInfo) {

    var yieldGroup: YieldGroup

    init {
        this.yieldGroup = YieldGroup()
        addActor(yieldGroup)
        if (city.location == tileInfo.position) {
            populationImage = ImageGetter.getImage("StatIcons/City_Center_(Civ6).png")
            addActor(populationImage)
        }
    }

    fun update() {
        super.update(true)

        updateUnitImage(true)
        if(unitImage!=null) {
            unitImage!!.setPosition(width / 2 - unitImage!!.width / 2 + 20,
                    height / 2 - unitImage!!.height / 2 + 20) // top
        }

        updatePopulationImage()
        if (improvementImage != null) improvementImage!!.setColor(1f, 1f, 1f, 0.5f)
        if (resourceImage != null) resourceImage!!.setColor(1f, 1f, 1f, 0.5f)
        updateYieldGroup()
    }

    private fun updateYieldGroup() {
        yieldGroup.setStats(tileInfo.getTileStats(city, city.civInfo.gameInfo.getPlayerCivilization()))
        yieldGroup.setOrigin(Align.center)
        yieldGroup.setScale(0.7f)
        yieldGroup.toFront()
        yieldGroup.setPosition(width / 2 - yieldGroup.width / 2, height * 0.25f - yieldGroup.height / 2)
    }

    private fun updatePopulationImage() {
        if (populationImage != null) {
            populationImage!!.setSize(30f, 30f)
            populationImage!!.setPosition(width / 2 - populationImage!!.width / 2,
                    height * 0.85f - populationImage!!.height / 2)

            if (tileInfo.isWorked())
                populationImage!!.color = Color.WHITE
            else populationImage!!.color = Color.GRAY
            populationImage!!.toFront()
        }
    }


}
