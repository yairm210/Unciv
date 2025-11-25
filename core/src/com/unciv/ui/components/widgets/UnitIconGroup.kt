package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.extensions.addToCenter
import com.unciv.ui.components.extensions.centerX
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.surroundWithThinCircle
import com.unciv.ui.images.ImageGetter

private class FlagBackground(drawable: TextureRegionDrawable, size: Float) : Image(drawable) {

    var drawableInner: TextureRegionDrawable? = null

    var innerColor: Color = Color.WHITE
    var outerColor: Color = Color.RED
    var outlineColor: Color = Color.WHITE

    var drawOutline = false

    private val innerMultiplier = 0.88f
    private val outlineMultiplier = 1.08f

    private val innerWidth: Float; private val innerHeight: Float
    private val innerOffsetX: Float; private val innerOffsetY: Float

    private val outlineWidth: Float; private val outlineHeight: Float
    private val outlineOffsetX: Float; private val outlineOffsetY: Float

    init {

        val ratio = height/width
        width = size
        height = size * ratio

        innerWidth = width * innerMultiplier; innerHeight = height * innerMultiplier
        innerOffsetX = (width - innerWidth) / 2; innerOffsetY = (height - innerHeight) / 2

        outlineWidth = width * outlineMultiplier; outlineHeight = height * outlineMultiplier
        outlineOffsetX = (outlineWidth - width) / 2; outlineOffsetY = (outlineHeight - height) / 2
    }

    override fun getDrawable(): TextureRegionDrawable {
        return super.getDrawable() as TextureRegionDrawable
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val alpha = color.a*parentAlpha
        val drawable = drawable

        if (drawOutline) {
            batch.setColor(outlineColor.r, outlineColor.g, outlineColor.b, outlineColor.a*alpha)
            drawable.draw(batch, x-outlineOffsetX, y-outlineOffsetY, outlineWidth, outlineHeight)
        }

        batch.setColor(outerColor.r, outerColor.g, outerColor.b, outerColor.a*alpha)
        drawable.draw(batch, x, y, width, height)

        batch.setColor(innerColor.r, innerColor.g, innerColor.b, innerColor.a * alpha)
        if (drawableInner == null) {
            drawable.draw(batch, x + innerOffsetX, y + innerOffsetY, innerWidth, innerHeight)
        } else {
            drawableInner!!.draw(batch, x, y, width, height)
        }
    }

}

/** Displays the unit's icon and action */
class UnitIconGroup(val unit: MapUnit, val size: Float) : NonTransformGroup() {
    var actionGroup: Group? = null

    private val flagIcon = ImageGetter.getUnitIcon(unit.baseUnit, unit.civ.nation.getInnerColor())
    private var flagBg: FlagBackground = FlagBackground(getBackgroundDrawableForUnit(), size)
    private var flagSelection: Image = getBackgroundSelectionForUnit()
    private var flagMask: Image? = getBackgroundMaskForUnit()

    init {
        color.a *= UncivGame.Current.settings.unitIconOpacity

        val sizeSelectionX = size * 1.6f
        val sizeSelectionY = sizeSelectionX * flagSelection.height / flagSelection.width

        setSize(flagBg.width, flagBg.height)

        flagSelection.color.set(1f, 1f, 0.9f, 0f)
        flagSelection.align = Align.center
        flagSelection.setSize(sizeSelectionX, sizeSelectionY)

        flagBg.innerColor = unit.civ.nation.getOuterColor()
        flagBg.outerColor = unit.civ.nation.getInnerColor()
        flagBg.outlineColor = flagBg.innerColor
        flagBg.drawableInner = getBackgroundInnerDrawableForUnit()

        if (flagMask != null) {
            flagMask!!.setSize(size * 0.88f, size * 0.88f * flagMask!!.height / flagMask!!.width)
        }

        val flagIconSizeMultiplier: Float = if (unit.isCivilian()) 0.5f else 0.65f
        flagIcon.setSize(size * flagIconSizeMultiplier)

        addToCenter(flagSelection)
        addToCenter(flagBg)
        if (flagMask != null)
            addToCenter(flagMask!!)
        addToCenter(flagIcon)

        val actionImage = getActionImage()
        if (actionImage != null) {
            actionGroup = actionImage
                .surroundWithCircle(size/2 * 0.9f)
                .surroundWithThinCircle()
            actionGroup!!.setPosition(size/2, 0f)
            addActor(actionGroup)
        }

        if (unit.health < 100) { // add health bar
            val hp = ImageGetter.getHealthBar(unit.health.toFloat(), 100f, size * 0.78f)
            addActor(hp)
            hp.centerX(this)
        }
    }

    private fun getBackgroundDrawableForUnit(): TextureRegionDrawable {
        return when {
            unit.isEmbarked() -> ImageGetter.getDrawable("UnitFlagIcons/UnitFlagEmbark")
            unit.isFortified() -> ImageGetter.getDrawable("UnitFlagIcons/UnitFlagFortify")
            unit.isGuarding() -> ImageGetter.getDrawable("UnitFlagIcons/UnitFlagFortify")
            unit.isCivilian() -> ImageGetter.getDrawable("UnitFlagIcons/UnitFlagCivilian")
            else -> ImageGetter.getDrawable("UnitFlagIcons/UnitFlag")
        }
    }

    private fun getBackgroundInnerDrawableForUnit(): TextureRegionDrawable? {
        return when {
            unit.isEmbarked() -> ImageGetter.getDrawableOrNull("UnitFlagIcons/UnitFlagEmbarkInner")
            unit.isFortified() -> ImageGetter.getDrawableOrNull("UnitFlagIcons/UnitFlagFortifyInner")
            unit.isGuarding() -> ImageGetter.getDrawableOrNull("UnitFlagIcons/UnitFlagFortifyInner")
            unit.isCivilian() -> ImageGetter.getDrawableOrNull("UnitFlagIcons/UnitFlagCivilianInner")
            else -> ImageGetter.getDrawableOrNull("UnitFlagIcons/UnitFlagInner")
        }
    }

    private fun getBackgroundMaskForUnit(): Image? {

        val filename = when {
            unit.isEmbarked() -> "UnitFlagIcons/UnitFlagMaskEmbark"
            unit.isFortified() -> "UnitFlagIcons/UnitFlagMaskFortify"
            unit.isGuarding() -> "UnitFlagIcons/UnitFlagMaskFortify"
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
            unit.isGuarding() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionFortify")
            unit.isCivilian() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionCivilian")
            else -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelection")
        }
    }

    private fun getActionImage(): Image? {
        return when {
            unit.isSleeping() -> ImageGetter.getImage("UnitActionIcons/Sleep")
            unit.getTile().improvementInProgress != null && unit.canBuildImprovement(unit.getTile().getTileImprovementInProgress()!!) ->
                ImageGetter.getImage("ImprovementIcons/${unit.getTile().improvementInProgress}")
            unit.isEscorting() -> ImageGetter.getImage("UnitActionIcons/Escort")
            unit.isMoving() -> ImageGetter.getImage("UnitActionIcons/MoveTo")
            unit.isExploring() -> ImageGetter.getImage("UnitActionIcons/Explore")
            unit.isAutomated() -> ImageGetter.getImage("UnitActionIcons/Automate")
            unit.isSetUpForSiege() -> ImageGetter.getImage("UnitActionIcons/SetUp")
            else -> null
        }
    }

    fun highlightRed() {
        flagSelection.color = colorFromRGB(230, 0, 0)
        flagBg.drawOutline = true
    }

    fun selectUnit() {

        val opacity = 1f

        color.a = opacity

        //If unit is idle, leave actionGroup at 50% opacity when selected
        if (unit.isIdle()) {
            actionGroup?.color?.a = opacity * 0.5f
        } else { //Else set to 100% opacity when selected
            actionGroup?.color?.a = opacity
        }

        // Unit base icon is faded out only if out of moves
        // Foreign unit icons are never faded!
        val shouldBeFaded = (unit.owner == GUI.getSelectedPlayer().civName
                && !unit.hasMovement() && GUI.getSettings().unitIconOpacity == 1f)
        val alpha = if (shouldBeFaded) opacity * 0.5f else opacity
        flagIcon.color.a = alpha
        flagBg.color.a = alpha
        flagSelection.color.a = opacity

        if (GUI.getSettings().continuousRendering) {
            flagSelection.color.a = opacity
            flagSelection.addAction(
                Actions.repeat(
                    RepeatAction.FOREVER,
                    Actions.sequence(
                        Actions.alpha(0f, 1f),
                        Actions.alpha(opacity, 1f)
                    )
                )
            )
        } else {
            flagSelection.color.a = opacity * 0.8f
        }
    }
}
