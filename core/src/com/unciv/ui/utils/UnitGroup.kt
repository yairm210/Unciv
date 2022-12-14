package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerX
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toGroup

class UnitGroup(val unit: MapUnit, val size: Float): Group() {
    var actionGroup :Group? = null
    val unitBaseImage = ImageGetter.getUnitIcon(unit.name, unit.civInfo.nation.getInnerColor())
        .apply {
            if (unit.isCivilian())
                setSize(size * 0.60f, size * 0.60f)
            else
                setSize(size * 0.75f, size * 0.75f) }
    var flagSelection: Image? = null
    var flagHalo: Image? = null
    var flagBg: Group? = null

    init {

        val bgW = size
        var bgH = size

        if (unit.isFortified())
            bgH *= 1.1f
        else if (unit.isEmbarked())
            bgH *= 1.1f
        else if (unit.isCivilian())
            bgH *= 1.2f

        setSize(bgW*1.25f, bgH*1.25f)

        flagSelection = getBackgroundSelectionForUnit()
        flagSelection?.apply {
            align = Align.center
            color.a = 0f
            if (unit.isCivilian() && !unit.isEmbarked())
                setSize(bgW*2.5f, bgH*2.5f)
            else
                setSize(bgW*2f, bgH*2f)
            center(this@UnitGroup)
        }

        flagHalo = getBackgroundImageForUnit()
        flagHalo?.apply{
            align = Align.center
            color.a = 0f
            if (unit.isCivilian() && !unit.isEmbarked())
                setSize(bgW*1.40f, bgH*1.40f)
            else
                setSize(bgW*1.30f, bgH*1.30f)
            center(this@UnitGroup)
        }

        flagBg = Group().apply {
            setSize(bgW*1.15f, bgH*1.15f)
            val outer = getBackgroundImageForUnit().apply {
                align = Align.center
                color = unit.civInfo.nation.getInnerColor()
                color.a = UncivGame.Current.settings.unitIconOpacity
                if (unit.isCivilian() && !unit.isEmbarked())
                    setSize(bgW*1.20f, bgH*1.20f)
                else
                    setSize(bgW*1.15f, bgH*1.15f)
            }

            val inner = getBackgroundImageForUnit().apply {
                align = Align.center
                color = unit.civInfo.nation.getOuterColor()
                color.a = UncivGame.Current.settings.unitIconOpacity
                setSize(bgW, bgH)
            }

            val mask = getBackgroundMaskForUnit().apply {
                align = Align.center
                color.a = UncivGame.Current.settings.unitIconOpacity
                setSize(bgW, bgH)
            }

            outer.center(this)
            inner.center(this)

            mask.center(this)
            addActor(outer)
            addActor(inner)
            addActor(mask)
            center(this@UnitGroup)
        }
        unitBaseImage.center(this)
        addActor(flagSelection)
        addActor(flagHalo)
        addActor(flagBg)
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
            unit.isEmbarked() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagEmbark")
            unit.isFortified() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagFortify")
            unit.isCivilian() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagCivilian")
            else -> ImageGetter.getImage("UnitFlagIcons/UnitFlag")
        }
    }

    private fun getBackgroundMaskForUnit(): Image {
        return when {
            unit.isEmbarked() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagMaskEmbark")
            unit.isFortified() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagMaskFortify")
            unit.isCivilian() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagMaskCivilian")
            else -> ImageGetter.getImage("UnitFlagIcons/UnitFlagMask")
        }
    }

    private fun getBackgroundSelectionForUnit(): Image {
        return when {
            unit.isEmbarked() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionEmbark")
            unit.isFortified() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionFortify")
            unit.isCivilian() -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelectionCivilian")
            else -> ImageGetter.getImage("UnitFlagIcons/UnitFlagSelection")
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

        //Make unit icon background colors fully opaque when units are selected
        flagBg?.children?.forEach { it.color?.a = 1f }

        //If unit is idle, leave actionGroup at 50% opacity when selected
        if (!unit.isIdle()) {
            actionGroup?.color?.a = 0.5f
        } else { //Else set to 100% opacity when selected
            actionGroup?.color?.a = 1f
        }

        // Unit base icon is faded out only if out of moves
        if (unit.currentMovement == 0f && !unit.canAttack()) {
            unitBaseImage.color.a = 0.5f
            flagHalo?.color?.a = 0f
            flagBg?.children?.forEach { it.color.a = 0.5f }
        } else {
            unitBaseImage.color.a = 1f
            flagHalo?.color?.a = 1f
            flagBg?.children?.forEach { it.color.a = 1f }
        }

        flagSelection?.apply { color.a = 1f }
        flagHalo?.apply { color.a = 1f }

        if (UncivGame.Current.settings.continuousRendering) {
            flagSelection?.addAction(
                Actions.repeat(
                    RepeatAction.FOREVER,
                    Actions.sequence(
                        Actions.fadeOut(1f),
                        Actions.fadeIn(1f)
                    )
                )
            )
        }
    }
}
