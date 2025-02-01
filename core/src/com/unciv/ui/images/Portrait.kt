package com.unciv.ui.images

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.stats.Stats
import com.unciv.ui.components.extensions.*

/**
 *  ### Manages "portraits" for a subset of RulesetObjects
 *  - A Portrait will be a classic circular Icon in vanilla
 *  - Mods can supply portraits in separate texture paths that can fill a square
 *  - Instantiate through [ImageGetter]`.get<type>Portrait()` methods
 *  - TODO - that's as far as I understand this - @SomeTroglodyte
 *  ### Caveat
 *  - This is a Group and does **not** support [Layout].
 *  - It sets its own [size] but **paints outside these bounds** - by [borderSize].
 *  - Typically, if you want one in a Table Cell, add an extra [borderSize] padding to avoid surprises.
 */
open class Portrait(val type: Type, val imageName: String, val size: Float, val borderSize: Float = 2f) : Group() {

    enum class Type(val directory: String) {
        Unit("Unit"),
        Building("Building"),
        Tech("Tech"),
        Resource("Resource"),
        Improvement("Improvement"),
        Promotion("UnitPromotion"),
        Unique("Unique"),
        Nation("Nation"),
        Religion("Religion"),
        UnitAction("UnitAction")
    }

    val image: Image
    val background: Group
    val ruleset: Ruleset = ImageGetter.ruleset

    var isPortrait = false

    val pathPortrait = "${type.directory}Portraits/$imageName"
    val pathPortraitFallback = "${type.directory}Portraits/Fallback"
    val pathIcon = "${type.directory}Icons/$imageName"
    val pathIconFallback = "${type.directory}Icons/Fallback"

    open fun getDefaultInnerBackgroundTint(): Color { return Color.WHITE.cpy() }
    open fun getDefaultOuterBackgroundTint(): Color { return Color.BLACK.cpy() }
    open fun getDefaultImageTint(): Color { return Color.WHITE.cpy() }
    open fun getDefaultImage(): Image {
        return when {
            ImageGetter.imageExists(pathIcon) -> ImageGetter.getImage(pathIcon)
            ImageGetter.imageExists(pathIconFallback) -> ImageGetter.getImage(pathIconFallback)
            else -> ImageGetter.getCircle()
        }
    }

    init {
        isTransform = false // NOT NonTransformGroup, since we need to turn it upside down when generating font chars

        image = getMainImage()
        background = getMainBackground()

        this.setSize(size + borderSize, size + borderSize)

        background.center(this)
        image.center(this)

        this.addActor(background)
        this.addActor(image)
    }

    /** Inner image */
    fun getMainImage() : Image {
        return when {
            ImageGetter.imageExists(pathPortrait) -> {
                isPortrait = true
                ImageGetter.getImage(pathPortrait)
            }
            ImageGetter.imageExists(pathPortraitFallback) -> {
                isPortrait = true
                ImageGetter.getImage(pathPortraitFallback)
            }
            else -> getDefaultImage().apply { color = getDefaultImageTint() }
        }
    }
    
    // Overridable so portraits can use circle images from their texture to minimize texture swapping
    protected open fun getCircleImage() = ImageGetter.getCircle()

    /** Border / background */
    private fun getMainBackground() : Group {

        if (isPortrait && ImageGetter.imageExists("${type.directory}Portraits/Background")) {
            val backgroundImage = ImageGetter.getImage("${type.directory}Portraits/Background")
            val ratioW = image.width / backgroundImage.width
            val ratioH = image.height / backgroundImage.height
            image.setSize((size + borderSize)*ratioW, (size + borderSize)*ratioH)
            return backgroundImage.toGroup(size + borderSize)
        } else {
            image.setSize(size*0.75f, size*0.75f)

            val bg = object: Group(){
                init { apply { isTransform = false } }
                override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
            }

            val circleInner = getCircleImage()
            val circleOuter = getCircleImage()

            circleInner.setSize(size, size)
            circleOuter.setSize(size + borderSize, size + borderSize)
            bg.setSize(size + borderSize, size + borderSize)

            circleInner.align = Align.center
            circleOuter.align = Align.center

            circleInner.color = getDefaultInnerBackgroundTint()
            circleOuter.color = getDefaultOuterBackgroundTint()

            circleOuter.center(bg)
            circleInner.center(bg)

            circleOuter.setOrigin(Align.center)
            circleInner.setOrigin(Align.center)

            bg.addActor(circleOuter)
            bg.addActor(circleInner)

            return bg
        }
    }

}

class PortraitResource(name: String, size: Float, borderSize: Float) : Portrait(Type.Resource, name, size, borderSize) {

    override fun getDefaultInnerBackgroundTint(): Color =
        ruleset.tileResources[imageName]?.resourceType?.getColor() ?: Color.WHITE

    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

class PortraitTech(name: String, size: Float) : Portrait(Type.Tech, name, size) {
    override fun getDefaultOuterBackgroundTint(): Color = getDefaultImageTint()
    override fun getDefaultImageTint(): Color =
        ruleset.eras[ruleset.technologies[imageName]?.era()]?.getColor()?.darken(0.6f) ?: ImageGetter.CHARCOAL

    override fun getCircleImage(): Image = ImageGetter.getImage("TechIcons/Circle")
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

class PortraitUnit(name: String, size: Float) : Portrait(Type.Unit, name, size) {
    override fun getDefaultImageTint(): Color = Color.BLACK
    override fun getCircleImage() = ImageGetter.getImage("OtherIcons/ConstructionCircle")
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

class PortraitBuilding(name: String, size: Float) : Portrait(Type.Building, name, size) {
    override fun getDefaultImageTint(): Color = Color.BLACK
    override fun getCircleImage() = ImageGetter.getImage("OtherIcons/ConstructionCircle")
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

class PortraitUnavailableWonderForTechTree(name: String, size: Float) : Portrait(Type.Building, name, size) {
    override fun getDefaultOuterBackgroundTint(): Color = Color.RED
}

class PortraitUnique(name: String, size: Float) : Portrait(Type.Unique, name, size) {
    override fun getDefaultImageTint(): Color = ImageGetter.CHARCOAL
}

class PortraitReligion(name: String, size: Float) : Portrait(Type.Religion, name, size) {
    override fun getDefaultImageTint(): Color = ImageGetter.CHARCOAL
}

class PortraitUnitAction(name: String, size: Float) : Portrait(Type.UnitAction, name, size) {
    override fun getDefaultImageTint(): Color = ImageGetter.CHARCOAL
}

class PortraitImprovement(name: String, size: Float, dim: Boolean = false, isPillaged: Boolean = false, borderSize: Float = 2f)
    : Portrait(Type.Improvement, name, size, borderSize) {

    init {
        if (dim) {
            image.color.a = 0.7f
            background.color.a = 0.7f
        }
        if (isPillaged) {
            val pillagedIcon = ImageGetter.getImage("OtherIcons/Fire")
            pillagedIcon.setSize(width/2, height/2)
            pillagedIcon.setPosition(width, 0f, Align.bottomRight)
            addActor(pillagedIcon)
        }
    }

    private fun getColorFromStats(stats: Stats): Color {
        if (stats.asSequence().none { it.value > 0 })
            return Color.WHITE
        return stats.asSequence().maxByOrNull { it.value }!!.key.color
    }

    override fun getDefaultInnerBackgroundTint(): Color {
        val improvement = ImageGetter.ruleset.tileImprovements[imageName]
        if (improvement != null)
            return getColorFromStats(improvement)
        return Color.WHITE
    }

    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

class PortraitNation(name: String, size: Float) : Portrait(Type.Nation, name, size, size*0.1f) {

    override fun getDefaultImage(): Image {
        val nation = ruleset.nations[imageName]
        val isCityState = nation != null && nation.isCityState
        val pathCityState = "NationIcons/CityState"

        return when {
            ImageGetter.imageExists(pathIcon) -> ImageGetter.getImage(pathIcon)
            isCityState && ImageGetter.imageExists(pathCityState)-> ImageGetter.getImage(pathCityState)
            ImageGetter.imageExists(pathIconFallback) -> ImageGetter.getImage(pathIconFallback)
            else -> ImageGetter.getCircle()
        }
    }

    override fun getDefaultInnerBackgroundTint(): Color = 
        ruleset.nations[imageName]?.getOuterColor() ?: ImageGetter.CHARCOAL

    override fun getDefaultOuterBackgroundTint(): Color = getDefaultImageTint()
    override fun getDefaultImageTint(): Color = ruleset.nations[imageName]?.getInnerColor() ?: Color.WHITE

}

class PortraitPromotion(name: String, size: Float) : Portrait(Type.Promotion, name, size) {

    var level = 0

    init {
        if (level > 0) {
            val padding = if (level == 3) 0.5f else 2f
            val starTable = Table().apply { defaults().pad(padding) }
            repeat(level) {
                starTable.add(ImageGetter.getImage("OtherIcons/Star")).size(size / 4f)
            }
            starTable.centerX(this)
            starTable.y = size / 6f
            addActor(starTable)
        }
    }

    override fun getDefaultImage(): Image {
        val (nameWithoutBrackets, level, basePromotionName) = Promotion.getBaseNameAndLevel(imageName)

        this.level = level
        val pathWithoutBrackets = "UnitPromotionIcons/$nameWithoutBrackets"
        val pathBase = "UnitPromotionIcons/$basePromotionName"
        val pathUnit = "UnitIcons/${basePromotionName.removeSuffix(" ability")}"

        return when {
            ImageGetter.imageExists(pathWithoutBrackets) -> {
                this.level = 0
                ImageGetter.getImage(pathWithoutBrackets)
            }
            ImageGetter.imageExists(pathBase) -> ImageGetter.getImage(pathBase)
            ImageGetter.imageExists(pathUnit) -> ImageGetter.getImage(pathUnit)
            else -> ImageGetter.getImage(pathIconFallback)
        }
    }
    
    override fun getDefaultImageTint(): Color = ruleset.unitPromotions[imageName]?.innerColorObject
        ?: defaultInnerColor
    override fun getDefaultOuterBackgroundTint(): Color = getDefaultImageTint()
    override fun getDefaultInnerBackgroundTint(): Color = ruleset.unitPromotions[imageName]?.outerColorObject
        ?: defaultOuterColor

    companion object {
        val defaultInnerColor = colorFromRGB(255, 226, 0)
        val defaultOuterColor = colorFromRGB(0, 12, 49)
    }
}
