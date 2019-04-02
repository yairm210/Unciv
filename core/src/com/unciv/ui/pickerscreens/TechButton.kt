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

class TechButton(techName:String, val techManager: TechManager) : Table(CameraStageBaseScreen.skin) {
    val text= Label("", skin).setFontColor(Color.WHITE).apply { setAlignment(Align.center) }
    init {
        touchable = Touchable.enabled
        defaults().pad(10f)
        background = ImageGetter.getDrawable("OtherIcons/civTableBackground.png")
        if(ImageGetter.techIconExists(techName))
            add(ImageGetter.getTechIconGroup(techName)) // this is 60*60

        val rightSide = Table()
        val techCost = techManager.costOfTech(techName)
        val remainingTech = techManager.remainingScienceToTech(techName)
        if(techCost!=remainingTech){
            val percentComplete = (techCost-remainingTech)/techCost.toFloat()
            add(ImageGetter.getProgressBarVertical(2f, 50f, percentComplete, Color.BLUE, Color.WHITE))
        }
        rightSide.add(text).row()

        // here we add little images of what the tech gives you
        val techEnabledIcons = Table()
        techEnabledIcons.defaults().pad(5f)

        val techEnabledUnits = GameBasics.Units.values.filter { it.requiredTech==techName }
        val ourUniqueUnits = techEnabledUnits.filter { it.uniqueTo == techManager.civInfo.civName }
        val replacedUnits = ourUniqueUnits.map { it.replaces!! }
        val ourEnabledUnits = techEnabledUnits.filter { it.uniqueTo == null && !replacedUnits.contains(it.name) }
                .union(ourUniqueUnits)

        for(unit in ourEnabledUnits)
            techEnabledIcons.add(ImageGetter.getConstructionImage(unit.name).surroundWithCircle(30f))

        for(building in GameBasics.Buildings.values.filter { it.requiredTech==techName
                && (it.uniqueTo==null || it.uniqueTo==techManager.civInfo.civName)})
            techEnabledIcons.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))

        for(improvement in GameBasics.TileImprovements.values.filter { it.techRequired==techName || it.improvingTech==techName }) {
            if(improvement.name.startsWith("Remove"))
                techEnabledIcons.add(ImageGetter.getImage("OtherIcons/Stop")).size(30f)
            else techEnabledIcons.add(ImageGetter.getImprovementIcon(improvement.name, 30f))
        }

        for(resource in GameBasics.TileResources.values.filter { it.revealedBy==techName })
            techEnabledIcons.add(ImageGetter.getResourceImage(resource.name, 30f))

        val tech = GameBasics.Technologies[techName]!!
        for(unique in tech.uniques)
            techEnabledIcons.add(ImageGetter.getImage("OtherIcons/Star")
                    .apply { color= Color.BLACK }.surroundWithCircle(30f))

        rightSide.add(techEnabledIcons)

        add(rightSide)
        pack()
    }
}