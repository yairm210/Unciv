package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.TechManager
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.brighten
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerY
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.setFontSize
import com.unciv.ui.utils.extensions.setSize
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.surroundWithThinCircle
import com.unciv.ui.utils.extensions.toGroup
import com.unciv.ui.utils.extensions.toLabel

class TechButton(techName:String, private val techManager: TechManager, isWorldScreen: Boolean = true) : Table(BaseScreen.skin) {
    val text = "".toLabel().apply {
        wrap = false
        setFontSize(14)
        setAlignment(Align.left)
        setEllipsis(true)
    }
    val turns = "".toLabel().apply {
        setFontSize(14)
        setAlignment(Align.right)
    }
    var orderIndicator: IconCircleGroup? = null
    var bg = Image(BaseScreen.skinStrings.getUiBackground("TechPickerScreen/TechButton", BaseScreen.skinStrings.roundedEdgeRectangleMidShape))

    init {
        touchable = Touchable.enabled
        background = BaseScreen.skinStrings.getUiBackground("TechPickerScreen/TechButton", BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
            tintColor = Color.WHITE.darken(0.3f))

        bg.toBack()
        addActor(bg)

        pad(0f).padBottom(5f).padTop(5f).padLeft(5f).padRight(0f)

        val isResearched = (techManager.isResearched(techName) && techName != Constants.futureTech)
        add(ImageGetter.getTechIconGroup(techName, 46f, isResearched))
            .padRight(5f).padLeft(2f).left()

        if (isWorldScreen) {
            val techCost = techManager.costOfTech(techName)
            val remainingTech = techManager.remainingScienceToTech(techName)
            val techThisTurn = techManager.civInfo.statsForNextTurn.science

            val percentComplete = (techCost - remainingTech) / techCost.toFloat()
            val percentWillBeComplete = (techCost - (remainingTech-techThisTurn)) / techCost.toFloat()
            val progressBar = ImageGetter.ProgressBar(2f, 48f, true)
                    .setBackground(Color.WHITE)
                    .setSemiProgress(Color.BLUE.brighten(0.3f), percentWillBeComplete)
                    .setProgress(Color.BLUE.darken(0.5f), percentComplete)
            add(progressBar.addBorder(1f, Color.GRAY)).padLeft(0f).padRight(5f)
        }

        val rightSide = Table()

        rightSide.add(text).width(140f).top().left().padRight(15f)
        rightSide.add(turns).width(40f).top().left().padRight(10f).row()

        addTechEnabledIcons(techName, isWorldScreen, rightSide)

        rightSide.centerY(this)
        add(rightSide).expandX().left()
        pack()

        bg.setSize(width-3f, height-3f)
        bg.align = Align.center
        bg.center(this)

        pack()
    }

    fun setButtonColor(color: Color) {
        bg.color = color
        pack()
    }

    private fun addTechEnabledIcons(techName: String, isWorldScreen: Boolean, rightSide: Table) {
        val techEnabledIcons = Table().align(Align.left)
        techEnabledIcons.background = BaseScreen.skinStrings.getUiBackground(
            "TechPickerScreen/TechButtonIconsOutline",
            BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
            tintColor = Color.BLACK.cpy().apply { a = 0.7f }
        )
        techEnabledIcons.pad(0f).padLeft(10f).padTop(2f).padBottom(2f)
        techEnabledIcons.defaults().padRight(5f)
        val techIconSize = 30f

        val civName = techManager.civInfo.civName
        val ruleset = techManager.civInfo.gameInfo.ruleSet

        val tech = ruleset.technologies[techName]!!

        val icons = ArrayList<Group>()

        for (unit in tech.getEnabledUnits(ruleset, techManager.civInfo)) {
            icons.add(ImageGetter.getPortraitImage(unit.name, techIconSize))
        }

        for (building in tech.getEnabledBuildings(ruleset, techManager.civInfo)) {
            icons.add(ImageGetter.getPortraitImage(building.name, techIconSize))
        }

        for (obj in tech.getObsoletedObjects(ruleset, techManager.civInfo)) {
            val obsoletedIcon = when (obj) {
                is Building -> ImageGetter.getPortraitImage(obj.name, techIconSize)
                is TileResource -> ImageGetter.getResourceImage(obj.name, techIconSize)
                is TileImprovement -> ImageGetter.getImprovementIcon(obj.name, techIconSize)
                else -> continue
            }.also {
                val closeImage = ImageGetter.getRedCross(techIconSize / 2, 1f)
                closeImage.center(it)
                it.addActor(closeImage)
            }
            icons.add(obsoletedIcon)
        }

        for (resource in ruleset.tileResources.values.filter { it.revealedBy == techName }) {
            icons.add(ImageGetter.getResourceImage(resource.name, techIconSize))
        }

        for (improvement in ruleset.tileImprovements.values.asSequence()
            .filter { it.techRequired == techName }
            .filter { it.uniqueTo == null || it.uniqueTo == civName }
        ) {
            icons.add(ImageGetter.getImprovementIcon(improvement.name, techIconSize, true))
        }

        for (improvement in ruleset.tileImprovements.values.asSequence()
            .filter { it.uniqueObjects.any { u -> u.allParams.contains(techName) } }
            .filter { it.uniqueTo == null || it.uniqueTo == civName }
        ) {
            icons.add(
                ImageGetter.getImage("OtherIcons/Unique")
                    .surroundWithCircle(techIconSize)
                    .surroundWithThinCircle())
        }

        for (unique in tech.uniques) {
            icons.add(
                when (unique) {
                    UniqueType.EnablesCivWideStatProduction.text.replace("civWideStat", "Gold" )
                    -> ImageGetter.getImage("OtherIcons/ConvertGold").toGroup(techIconSize)
                    UniqueType.EnablesCivWideStatProduction.text.replace("civWideStat", "Science" )
                    -> ImageGetter.getImage("OtherIcons/ConvertScience").toGroup(techIconSize)
                    else -> ImageGetter.getImage("OtherIcons/Unique")
                        .surroundWithCircle(techIconSize)
                        .surroundWithThinCircle()
                }
            )
        }

        for (i in 0..4) {
            val icon = icons.getOrNull(i)
            if (icon != null)
                techEnabledIcons.add(icon)
        }

        rightSide.add(techEnabledIcons)
            .colspan(2)
            .minWidth(195f)
            .prefWidth(195f)
            .maxWidth(195f)
            .expandX().left().row()
    }

    fun addOrderIndicator(number:Int){
        orderIndicator = number.toString().toLabel(fontSize = 18)
            .apply { setAlignment(Align.center) }
            .surroundWithCircle(28f, color = BaseScreen.skinStrings.skinConfig.baseColor)
            .surroundWithCircle(30f,false)
        orderIndicator!!.setPosition(0f, height, Align.topLeft)
        addActor(orderIndicator)
    }

}
