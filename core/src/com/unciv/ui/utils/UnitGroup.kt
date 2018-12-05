package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.map.MapUnit

class UnitImage(val unit: MapUnit, val size: Float): Group() {
    init {
        val unitBaseImage = ImageGetter.getUnitIcon(unit.name, unit.civInfo.getNation().getSecondaryColor())
                .apply { setSize(size * 0.75f, size * 0.75f) }

        val background = getBackgroundImageForUnit(unit)
        background.apply {
            this.color = unit.civInfo.getNation().getColor()
            setSize(size, size)
        }
        setSize(size, size)
        addActor(background)
        unitBaseImage.center(this)
        addActor(unitBaseImage)


        if (unit.health < 100) { // add health bar
            addActor(ImageGetter.getHealthBar(unit.health.toFloat(), 100f, size))
        }
    }

    fun getBackgroundImageForUnit(unit: MapUnit): Image {
        return when {
            unit.isEmbarked() -> ImageGetter.getImage("OtherIcons/Banner")
            unit.isFortified() -> ImageGetter.getImage("OtherIcons/Shield.png")
            else -> ImageGetter.getCircle()
        }
    }
}