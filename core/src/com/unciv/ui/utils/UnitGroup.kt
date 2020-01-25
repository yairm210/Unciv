package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit

class UnitGroup(val unit: MapUnit, val size: Float): Group() {
    var blackSpinningCircle:Image?=null
    val unitBaseImage = ImageGetter.getUnitIcon(unit.name, unit.civInfo.nation.getInnerColor())
            .apply { setSize(size * 0.75f, size * 0.75f) }

    init {

        val background = getBackgroundImageForUnit(unit)
        background.apply {
            this.color = unit.civInfo.nation.getOuterColor()
            setSize(size, size)
        }
        setSize(size, size)
        addActor(background)
        unitBaseImage.center(this)
        addActor(unitBaseImage)


        if (unit.health < 100) { // add health bar
            addActor(ImageGetter.getHealthBar(unit.health.toFloat(), 100f, size))
        }

        isTransform=false // performance helper - nothing here is rotated or scaled
    }

    fun getBackgroundImageForUnit(unit: MapUnit): Image {
        return when {
            unit.isEmbarked() -> ImageGetter.getImage("OtherIcons/Banner")
            unit.isFortified() -> ImageGetter.getImage("OtherIcons/Shield")
            else -> ImageGetter.getCircle()
        }
    }


    fun selectUnit() {
        val whiteHalo = getBackgroundImageForUnit(unit)
        val whiteHaloSize = 30f
        whiteHalo.setSize(whiteHaloSize, whiteHaloSize)
        whiteHalo.center(this)
        addActor(whiteHalo)
        whiteHalo.toBack()

        if (UncivGame.Current.settings.continuousRendering) {
            val spinningCircle = if (blackSpinningCircle != null) blackSpinningCircle!!
            else ImageGetter.getCircle()
            spinningCircle.setSize(5f, 5f)
            spinningCircle.color = Color.BLACK
            spinningCircle.center(this)
            spinningCircle.x += whiteHaloSize / 2 // to edge of white halo
            spinningCircle.setOrigin(spinningCircle.width / 2 - whiteHaloSize / 2, spinningCircle.height / 2)
            addActor(spinningCircle)
            spinningCircle.addAction(Actions.repeat(RepeatAction.FOREVER, Actions.rotateBy(90f, 1f)))
            blackSpinningCircle = spinningCircle
        }
    }
}