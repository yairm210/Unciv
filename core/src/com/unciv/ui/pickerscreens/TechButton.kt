package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.TechManager
import com.unciv.ui.utils.*

class TechButton(techName:String, private val techManager: TechManager, isWorldScreen: Boolean = true) : Table(BaseScreen.skin) {
    val text = "".toLabel().apply { setAlignment(Align.center) }

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
                    .addColor(Color.BLUE.cpy().lerp(Color.WHITE, 0.3f), percentWillBeComplete)
                    .addColor(Color.BLUE.cpy().lerp(Color.BLACK, 0.5f), percentComplete)
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

        for (unit in tech.getEnabledUnits(techManager.civInfo))
            techEnabledIcons.add(ImageGetter.getConstructionImage(unit.name).surroundWithCircle(techIconSize))

        for (building in tech.getEnabledBuildings(techManager.civInfo))
            techEnabledIcons.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(techIconSize))

        for (building in tech.getObsoletedBuildings(techManager.civInfo))
            techEnabledIcons.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(techIconSize).apply {
                val closeImage = ImageGetter.getRedCross(techIconSize / 2, 1f)
                closeImage.center(this)
                addActor(closeImage)
            })

        for (improvement in ruleset.tileImprovements.values
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
            techEnabledIcons.add(ImageGetter.getImage("OtherIcons/Star")
                .apply { color = Color.BLACK }.surroundWithCircle(techIconSize))

        if (isWorldScreen) rightSide.add(techEnabledIcons)
        else rightSide.add(techEnabledIcons)
                .minWidth(225f)
    }
}
