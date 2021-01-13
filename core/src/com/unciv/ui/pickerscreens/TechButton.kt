package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.TechManager
import com.unciv.ui.utils.*

class TechButton(techName:String, private val techManager: TechManager, isWorldScreen: Boolean = true) : Table(CameraStageBaseScreen.skin) {
    val text = "".toLabel().apply { setAlignment(Align.center) }

    init {
        touchable = Touchable.enabled
        background = ImageGetter.getRoundedEdgeTableBackground()
        pad(10f)

        if (ImageGetter.techIconExists(techName))
            add(ImageGetter.getTechIconGroup(techName, 60f)).left()

        val rightSide = Table()
        val techCost = techManager.costOfTech(techName)
        val remainingTech = techManager.remainingScienceToTech(techName)

        if (isWorldScreen) {
            val percentComplete = (techCost - remainingTech) / techCost.toFloat()
            add(ImageGetter.getProgressBarVertical(2f, 50f, percentComplete, Color.BLUE, Color.WHITE)).pad(10f)
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

        for (unit in tech.getEnabledUnits(techManager.civInfo)
                .filter { "Will not be displayed in Civilopedia" !in it.uniques })
            techEnabledIcons.add(ImageGetter.getConstructionImage(unit.name).surroundWithCircle(techIconSize))

        for (building in tech.getEnabledBuildings(techManager.civInfo)
                .filter { "Will not be displayed in Civilopedia" !in it.uniques })
            techEnabledIcons.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(techIconSize))

        for (building in tech.getObsoletedBuildings(techManager.civInfo)
                .filter { "Will not be displayed in Civilopedia" !in it.uniques })
            techEnabledIcons.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(techIconSize).apply {
                val closeImage = ImageGetter.getImage("OtherIcons/Close")
                closeImage.setSize(techIconSize / 2, techIconSize / 2)
                closeImage.color = Color.RED
                closeImage.center(this)
                addActor(closeImage)
            })

        for (improvement in ruleset.tileImprovements.values
                .filter {
                    it.techRequired == techName || it.uniqueObjects.any { u -> u.params.contains(techName) }
                            || it.uniqueObjects.any { it.placeholderText == "[] once [] is discovered" && it.params[1] == techName }
                }
                .filter { it.uniqueTo == null || it.uniqueTo == civName })
            if (improvement.name.startsWith("Remove"))
                techEnabledIcons.add(ImageGetter.getImage("OtherIcons/Stop")).size(techIconSize)
            else techEnabledIcons.add(ImageGetter.getImprovementIcon(improvement.name, techIconSize))


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