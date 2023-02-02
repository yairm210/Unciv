package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.surroundWithThinCircle

class UnitGroup(val unit: MapUnit, val size: Float): Group() {
    var actionGroup :Group? = null
    val flagIcon = ImageGetter.getUnitIcon(unit.name, unit.civ.nation.getInnerColor())
        .apply {
            if (unit.isCivilian())
                setSize(size * 0.60f, size * 0.60f)
            else
                setSize(size * 0.75f, size * 0.75f) }
    var flagSelection: Image = getBackgroundSelectionForUnit()
    var flagBg: Group = Group()

    init {

        val outerBg = getBackgroundImageForUnit()
        val innerBg = getBackgroundImageForUnit()
        val maskBg = getBackgroundMaskForUnit()

        val sizeSelectionX = size*1.9f; val sizeSelectionY = sizeSelectionX*flagSelection.height/flagSelection.width
        val sizeOuterBgX = size*1.15f; val sizeOuterBgY = sizeOuterBgX*outerBg.height/outerBg.width
        val sizeInnerBgX = size; val sizeInnerBgY = sizeInnerBgX*innerBg.height/innerBg.width

        setSize(sizeOuterBgX, sizeOuterBgY)

        flagSelection.color.set(1f, 1f, 1f, 0f)
        flagSelection.align = Align.center
        flagSelection.setSize(sizeSelectionX, sizeSelectionY)
        flagSelection.center(this)

        flagBg.setSize(sizeOuterBgX, sizeOuterBgY)

        // 0f (invisible) to 1f (fully opaque)
        flagIcon.color.a = UncivGame.Current.settings.unitIconOpacity

        outerBg.color = unit.civ.nation.getInnerColor()
        outerBg.color.a = UncivGame.Current.settings.unitIconOpacity
        outerBg.setSize(sizeOuterBgX, sizeOuterBgY)
        outerBg.center(flagBg)

        innerBg.color = unit.civ.nation.getOuterColor()
        innerBg.color.a = UncivGame.Current.settings.unitIconOpacity
        innerBg.setSize(sizeInnerBgX, sizeInnerBgY)
        innerBg.center(flagBg)

        maskBg?.color?.a = UncivGame.Current.settings.unitIconOpacity
        maskBg?.setSize(size, size*maskBg.height / maskBg.width)
        maskBg?.center(flagBg)

        flagBg.addActor(outerBg)
        flagBg.addActor(innerBg)
        if (maskBg != null)
            flagBg.addActor(maskBg)
        flagBg.center(this)

        flagIcon.center(this)

        addActor(flagSelection)
        addActor(flagBg)
        addActor(flagIcon)

        val actionImage = getActionImage()
        if (actionImage != null) {
            val actionCircle = actionImage
                .surroundWithCircle(size / 2 * 0.9f)
                .surroundWithThinCircle()
            actionCircle.setPosition(size / 2, 0f)
            actionCircle.color.a = UncivGame.Current.settings.unitIconOpacity
            addActor(actionCircle)
            actionGroup = actionCircle
            actionCircle.toFront()
        }

        if (unit.health < 100) { // add health bar
            addActor(ImageGetter.getHealthBar(unit.health.toFloat(), 100f, size))
        }

        isTransform = false // performance helper - nothing here is rotated or scaled
    }

    private fun getBackgroundImageForUnit(): Image {
        return when {
            unit.isEmbarked() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagEmbark")
            unit.isFortified() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagFortify")
            unit.isCivilian() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagCivilian")
            else -> ImageGetter.getImage("UnitFlagIcons/UnitFlag")
        }
    }

    private fun getBackgroundMaskForUnit(): Image? {

        val filename = when {
            unit.isEmbarked() -> "UnitFlagIcons/UnitFlagMaskEmbark"
            unit.isFortified() -> "UnitFlagIcons/UnitFlagMaskFortify"
            unit.isCivilian() -> "UnitFlagIcons/UnitFlagMaskCivilian"
            else -> "UnitFlagIcons/UnitFlagMask"
        }

        if (ImageGetter.imageExists(filename))
            return ImageGetter.getImage(filename)
        return null
    }

    private fun getBackgroundSelectionForUnit(): Image {
        return when {
            unit.isEmbarked() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionEmbark")
            unit.isFortified() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionFortify")
            unit.isCivilian() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionCivilian")
            else -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelection")
        }
    }

    private fun getActionImage(): Image? {
        return when {
            unit.isSleeping() -> ImageGetter.getImage("UnitActionIcons/Sleep")
            unit.isMoving() -> ImageGetter.getImage("UnitActionIcons/MoveTo")
            unit.isExploring() -> ImageGetter.getImage("UnitActionIcons/Explore")
            unit.isAutomated() -> ImageGetter.getImage("UnitActionIcons/Automate")
            unit.isSetUpForSiege() -> ImageGetter.getImage("UnitActionIcons/SetUp")
            else -> null
        }
    }

    fun highlightRed() {
        flagSelection.color = colorFromRGB(230, 20, 0).apply { a = 1.0f }
        flagSelection.width *= 0.9f
        flagSelection.height *= 0.9f
        flagSelection.center(flagSelection.parent)
    }

    fun selectUnit() {

        //Make unit icon background colors fully opaque when units are selected
        flagBg.children.forEach { it.color?.a = 1f }

        //If unit is idle, leave actionGroup at 50% opacity when selected
        if (unit.isIdle()) {
            actionGroup?.color?.a = 0.5f
        } else { //Else set to 100% opacity when selected
            actionGroup?.color?.a = 1f
        }

        // Unit base icon is faded out only if out of moves
        // Foreign unit icons are never faded!
        val shouldBeFaded = (unit.owner == UncivGame.Current.worldScreen?.viewingCiv?.civName
                && unit.currentMovement == 0f)
        val alpha = if (shouldBeFaded) 0.5f else 1f
        flagIcon.color.a = alpha
        flagBg.color.a = alpha

        if (UncivGame.Current.settings.continuousRendering) {
            flagSelection.color.a = 1f
            flagSelection.addAction(
                Actions.repeat(
                    RepeatAction.FOREVER,
                    Actions.sequence(
                        Actions.alpha(0.6f, 1f),
                        Actions.alpha(1f, 1f)
                    )
                )
            )
        } else {
            flagSelection.color.a = 0.8f
        }
    }
}
