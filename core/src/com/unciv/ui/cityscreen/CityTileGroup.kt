package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.centerX

class CityTileGroup(private val city: CityInfo, tileInfo: TileInfo, tileSetStrings: TileSetStrings) : TileGroup(tileInfo,tileSetStrings) {

    var isWorkable = false
    private var yieldGroup = YieldGroup()

    init {
        isTransform = false // performance helper - nothing here is rotated or scaled
        addActor(yieldGroup)
        if (city.location == tileInfo.position) {
            icons.addPopulationIcon(ImageGetter.getImage("OtherIcons/Star")
                    .apply { color = Color.GOLD })
        }

    }

    fun update() {
        super.update(city.civInfo,true)

        // this needs to happen on update, because we can buy tiles, which changes the definition of the bought tiles...
        when {
            tileInfo.getCity()!=city -> { // outside of city
                baseLayerGroup.color.a = 0.3f
                yieldGroup.isVisible = false
                if (city.canAcquireTile(tileInfo))
                    icons.addPopulationIcon(ImageGetter.getStatIcon("Acquire"))
            }

            tileInfo !in city.tilesInRange -> { // within city but not close enough to be workable
                yieldGroup.isVisible = false
                baseLayerGroup.color.a = 0.5f
            }

            tileInfo.isLocked() -> {
                icons.addPopulationIcon(ImageGetter.getImage("OtherIcons/Lock"))
            }

            !tileInfo.isCityCenter() -> { // workable
                icons.addPopulationIcon()
                isWorkable = true
            }
        }

        terrainFeatureLayerGroup.color.a=0.5f
        icons.improvementIcon?.setColor(1f, 1f, 1f, 0.5f)
        resourceImage?.setColor(1f, 1f, 1f, 0.5f)
        cityImage?.setColor(1f, 1f, 1f, 0.5f)
        icons.civilianUnitIcon?.setColor(1f, 1f, 1f, 0.5f)
        icons.militaryUnitIcon?.setColor(1f, 1f, 1f, 0.5f)
        updatePopulationIcon()
        updateYieldGroup()
    }

    private fun updateYieldGroup() {
        yieldGroup.setStats(tileInfo.getTileStats(city, city.civInfo))
        yieldGroup.setOrigin(Align.center)
        yieldGroup.setScale(0.7f)
        yieldGroup.toFront()
        yieldGroup.centerX(this)
        yieldGroup.y= height * 0.25f - yieldGroup.height / 2

        if (tileInfo.isWorked() || city.canAcquireTile(tileInfo)) {
            yieldGroup.color = Color.WHITE
        }
        else if(!tileInfo.isCityCenter()){
            yieldGroup.color = Color.GRAY.cpy().apply { a=0.5f }
        }
    }

    private fun updatePopulationIcon() {
        val populationIcon = icons.populationIcon
        if (populationIcon != null) {
            populationIcon.setSize(30f, 30f)
            populationIcon.setPosition(width / 2 - populationIcon.width / 2,
                    height * 0.85f - populationIcon.height / 2)

            if (tileInfo.isWorked() || city.canAcquireTile(tileInfo)) {
                populationIcon.color = Color.WHITE
            }
            else if(!tileInfo.isCityCenter()){
                populationIcon.color = Color.GRAY.cpy()
            }

            populationIcon.toFront()
        }
    }

}