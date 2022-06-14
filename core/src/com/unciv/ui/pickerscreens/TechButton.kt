package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.TechManager
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.brighten
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel

class TechButton(techName:String, private val techManager: TechManager, isWorldScreen: Boolean = true) : Table(BaseScreen.skin) {
    val text = "".toLabel().apply { setAlignment(Align.center) }
    var orderIndicator: IconCircleGroup? = null

    init {
        touchable = Touchable.enabled
        background = ImageGetter.getRoundedEdgeRectangle()
        pad(10f)

        if (ImageGetter.techIconExists(techName))
            add(ImageGetter.getTechIconGroup(techName, 60f)).left()

        val rightSide = Table()

        if (isWorldScreen) {
            val techCost = techManager.costOfTech(techName)
            val remainingTech = techManager.remainingScienceToTech(techName)
            val techThisTurn = techManager.civInfo.statsForNextTurn.science

            val percentComplete = (techCost - remainingTech) / techCost.toFloat()
            val percentWillBeComplete = (techCost - (remainingTech-techThisTurn)) / techCost.toFloat()
            val progressBar = ImageGetter.VerticalProgressBar(2f, 50f)
                    .addColor(Color.WHITE, 1f)
                    .addColor(Color.BLUE.brighten(0.3f), percentWillBeComplete)
                    .addColor(Color.BLUE.darken(0.5f), percentComplete)
            add(progressBar.addBorder(1f, Color.GRAY)).pad(10f)
            rightSide.add(text).padBottom(5f).row()
        } else rightSide.add(text).height(25f).padBottom(5f).row()

        addTechEnabledIcons(techName, isWorldScreen, rightSide)

        add(rightSide)
        pack()
    }

    private fun addTechEnabledIcons(techName: String, isWorldScreen: Boolean, rightSide: Table) {
        val techEnabledIcons = Table()
        techEnabledIcons.defaults().pad(5f)
        val techIconSize = 30f

        val civName = techManager.civInfo.civName
        val ruleset = techManager.civInfo.gameInfo.ruleSet

        val tech = ruleset.technologies[techName]!!

        for (unit in tech.getEnabledUnits(ruleset, techManager.civInfo))
            techEnabledIcons.add(ImageGetter.getConstructionImage(unit.name).surroundWithCircle(techIconSize))

        for (building in tech.getEnabledBuildings(ruleset, techManager.civInfo))
            techEnabledIcons.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(techIconSize))

        for (obj in tech.getObsoletedObjects(ruleset, techManager.civInfo)) {
            val obsoletedIcon = when (obj) {
                is Building -> ImageGetter.getConstructionImage(obj.name)
                    .surroundWithCircle(techIconSize)
                is TileResource -> ImageGetter.getResourceImage(obj.name, techIconSize)
                is TileImprovement -> ImageGetter.getImprovementIcon(obj.name, techIconSize)
                else -> continue
            }.also {
                val closeImage = ImageGetter.getRedCross(techIconSize / 2, 1f)
                closeImage.center(it)
                it.addActor(closeImage)
            }
            techEnabledIcons.add(obsoletedIcon)
        }


        for (improvement in ruleset.tileImprovements.values.asSequence()
            .filter {
                it.techRequired == techName
                || it.uniqueObjects.any { u -> u.allParams.contains(techName) }
            }
            .filter { it.uniqueTo == null || it.uniqueTo == civName }
        ) {
            techEnabledIcons.add(ImageGetter.getImprovementIcon(improvement.name, techIconSize))
        }


        for (resource in ruleset.tileResources.values.filter { it.revealedBy == techName })
            techEnabledIcons.add(ImageGetter.getResourceImage(resource.name, techIconSize))

        for (unique in tech.uniques)
            techEnabledIcons.add(
                ImageGetter.getImage("OtherIcons/Star")
                .apply { color = Color.BLACK }.surroundWithCircle(techIconSize))

        if (isWorldScreen) rightSide.add(techEnabledIcons)
        else rightSide.add(techEnabledIcons)
                .minWidth(225f)
    }

    fun addOrderIndicator(number:Int){
        orderIndicator = number.toString().toLabel(fontSize = 18)
            .apply { setAlignment(Align.center) }
            .surroundWithCircle(28f, color = ImageGetter.getBlue())
            .surroundWithCircle(30f,false)
        orderIndicator!!.setPosition(0f, height, Align.topLeft)
        addActor(orderIndicator)
    }
}
