package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.surroundWithCircle

class UnitGroup(val unit: MapUnit, val size: Float): Group() {
    var blackSpinningCircle: Image? = null
    var actionGroup :Group? = null
    val unitBaseImage = ImageGetter.getUnitIcon(unit.name, unit.civInfo.nation.getInnerColor())
        .apply { setSize(size * 0.75f, size * 0.75f) }

    init {
        val background = getBackgroundImageForUnit()
        background.apply {
            this.color = unit.civInfo.nation.getOuterColor()
            setSize(size, size)
        }
        setSize(size, size)
        addActor(background)
        unitBaseImage.center(this)
        addActor(unitBaseImage)

        val actionImage = getActionImage()
        if (actionImage != null) {
            actionImage.color = Color.BLACK
            val actionCircle = actionImage.surroundWithCircle(size / 2 * 0.9f)
                .surroundWithCircle(size / 2, false, Color.BLACK)
            actionCircle.setPosition(size / 2, 0f)
            addActor(actionCircle)
            actionGroup = actionCircle
        }

        if (unit.health < 100) { // add health bar
            addActor(ImageGetter.getHealthBar(unit.health.toFloat(), 100f, size))
        }

        isTransform = false // performance helper - nothing here is rotated or scaled
    }

    private fun getBackgroundImageForUnit(): Image {
        return when {
            unit.isEmbarked() -> ImageGetter.getImage("OtherIcons/Banner")
            unit.isFortified() -> ImageGetter.getImage("OtherIcons/Shield")
            else -> ImageGetter.getCircle()
        }
    }

    fun getActionImage(): Image? {
        return when {
            unit.isFortified() -> ImageGetter.getImage("OtherIcons/Shield")
            unit.isSleeping() -> ImageGetter.getImage("OtherIcons/Sleep")
            unit.isMoving() -> ImageGetter.getStatIcon("Movement")
            //todo: Less hardcoding, or move to Constants with explanation (should icon change with mods?)
            unit.isExploring() -> ImageGetter.getUnitIcon("Scout")
            unit.isAutomated() -> ImageGetter.getUnitIcon("Great Engineer")
            unit.isSetUpForSiege() -> ImageGetter.getUnitIcon("Catapult")
            else -> null
        }
    }


    fun selectUnit() {
        val whiteHalo = getBackgroundImageForUnit()
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
            spinningCircle.setOrigin(
                spinningCircle.width / 2 - whiteHaloSize / 2,
                spinningCircle.height / 2
            )
            addActor(spinningCircle)
            spinningCircle.addAction(
                Actions.repeat(
                    RepeatAction.FOREVER,
                    Actions.rotateBy(90f, 1f)
                )
            )
            blackSpinningCircle = spinningCircle
        }
    }
}
