package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.TechManager
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.setFontColor
import com.unciv.ui.utils.surroundWithCircle

class TechButton(techName:String, val techManager: TechManager, isWorldScreen: Boolean = true) : Table(CameraStageBaseScreen.skin) {
    val text= Label("", skin).setFontColor(Color.WHITE).apply { setAlignment(Align.center) }

    init {
        touchable = Touchable.enabled
        defaults().pad(10f)
        if (ImageGetter.techIconExists(techName))
            add(ImageGetter.getTechIconGroup(techName, 60f))

        val rightSide = Table()
        val techCost = techManager.costOfTech(techName)
        val remainingTech = techManager.remainingScienceToTech(techName)
        if (techCost != remainingTech) {
            val percentComplete = (techCost - remainingTech) / techCost.toFloat()
            add(ImageGetter.getProgressBarVertical(2f, 50f, percentComplete, Color.BLUE, Color.WHITE))
        } else add().width(2f)

        if (isWorldScreen) rightSide.add(text).row()
        else rightSide.add(text).height(25f).row()

        addTechEnabledIcons(techName, isWorldScreen, rightSide)

        add(rightSide)

        background = ImageGetter.getRoundedRectangleDrawable(Color.WHITE, this.prefWidth.toInt(), this.prefHeight.toInt(), 25)

        pack()
    }

    private fun addTechEnabledIcons(techName: String, isWorldScreen: Boolean, rightSide: Table) {
        val techEnabledIcons = Table()
        techEnabledIcons.defaults().pad(5f)

        val civName = techManager.civInfo.civName

        val techEnabledUnits = GameBasics.Units.values.filter { it.requiredTech == techName }
        val ourUniqueUnits = techEnabledUnits.filter { it.uniqueTo == civName }
        val replacedUnits = ourUniqueUnits.map { it.replaces!! }
        val ourEnabledUnits = techEnabledUnits.filter { it.uniqueTo == null && !replacedUnits.contains(it.name) }
                .union(ourUniqueUnits)

        for (unit in ourEnabledUnits)
            techEnabledIcons.add(ImageGetter.getConstructionImage(unit.name).surroundWithCircle(30f))

        val techEnabledBuildings = GameBasics.Buildings.values.filter { it.requiredTech == techName }
        val ourUniqueBuildings = techEnabledBuildings.filter { it.uniqueTo == civName }
        val replacedBuildings = ourUniqueBuildings.map { it.replaces!! }
        val ourEnabledBuildings = techEnabledBuildings
                .filter { it.uniqueTo == null && !replacedBuildings.contains(it.name) }
                .union(ourUniqueBuildings)

        for (building in ourEnabledBuildings)
            techEnabledIcons.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))

        for (improvement in GameBasics.TileImprovements.values
                .filter { it.techRequired == techName || it.improvingTech == techName }
                .filter { it.uniqueTo==null || it.uniqueTo==civName }) {
            if (improvement.name.startsWith("Remove"))
                techEnabledIcons.add(ImageGetter.getImage("OtherIcons/Stop")).size(30f)
            else techEnabledIcons.add(ImageGetter.getImprovementIcon(improvement.name, 30f))
        }

        for (resource in GameBasics.TileResources.values.filter { it.revealedBy == techName })
            techEnabledIcons.add(ImageGetter.getResourceImage(resource.name, 30f))

        val tech = GameBasics.Technologies[techName]!!
        for (unique in tech.uniques)
            techEnabledIcons.add(ImageGetter.getImage("OtherIcons/Star")
                    .apply { color = Color.BLACK }.surroundWithCircle(30f))

        if (isWorldScreen) rightSide.add(techEnabledIcons)
        else rightSide.add(techEnabledIcons).width(150f)
    }
}