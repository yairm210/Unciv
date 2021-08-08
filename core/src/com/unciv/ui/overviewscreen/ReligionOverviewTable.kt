package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.min

class ReligionOverviewTable(
    private val viewingPlayer: CivilizationInfo,
    private val overviewScreen: EmpireOverviewScreen
): Table() {
    
    val gameInfo = viewingPlayer.gameInfo
    private val topButtons = Table(CameraStageBaseScreen.skin)
    private val topButtonLabel = "Click an icon to see the stats of this religion".tr().toLabel()
    private val statsTable = Table(CameraStageBaseScreen.skin)
    private val beliefsTable = Table(CameraStageBaseScreen.skin)
    private var selectedReligion: String? = null
    
    init {
        draw(null)
    }
    
    private fun draw(religion: Religion?) {
        topButtons.clear()
        topButtonLabel.clear()
        statsTable.clear()
        beliefsTable.clear()
        clear()

        addReligionButtons()
        if (religion != null) loadReligion(religion)
        add(topButtons).pad(5f).row()
        add(topButtonLabel).pad(5f)
        addSeparator()
        add(statsTable).pad(5f).row()
        add(beliefsTable).pad(5f)
    }
    
    private fun addReligionButtons() {
        topButtons.clear()
        val existingReligions: List<Religion> = gameInfo.civilizations.mapNotNull { it.religionManager.religion }
        for (religion in existingReligions) {
            val button: Button
            if (religion.isPantheon()) {
                val image = if (viewingPlayer.knows(religion.foundingCivName) || viewingPlayer.civName == religion.foundingCivName)
                    ImageGetter.getNationIndicator(gameInfo.getCivilization(religion.foundingCivName).nation, 60f)
                else
                    ImageGetter.getRandomNationIndicator(60f)
                button = Button(image, CameraStageBaseScreen.skin)
            } else {
                val image = ImageGetter.getReligionIcon(religion.iconName)
                image.color = Color.BLACK
                val icon = image.surroundWithCircle(60f)
                button = Button(icon, CameraStageBaseScreen.skin)
            }
            
            button.onClick {
                selectedReligion = religion.name
                draw(religion)
            }
            
            if (selectedReligion == religion.name)
                button.disable()
            
            topButtons.add(button).pad(5f)
        }
    }
    
    private fun loadReligion(religion: Religion) {
        topButtonLabel.setText(religion.name.tr())
        for (belief in religion.getPantheonBeliefs()) {
            beliefsTable.add(createBeliefDescription(belief)).pad(10f).row()
        }
        for (belief in religion.getFollowerBeliefs()) {
            beliefsTable.add(createBeliefDescription(belief)).pad(10f).row()
        }
        for (belief in religion.getFounderBeliefs()) {
            beliefsTable.add(createBeliefDescription(belief)).pad(10f).row()
        }
        
        statsTable.add("Religion Name:".tr())
        statsTable.add(religion.name.tr()).pad(5f).row()
        statsTable.add("Founding Civ:".tr())
        val foundingCivName =
            if (viewingPlayer.knows(religion.foundingCivName) || viewingPlayer.civName == religion.foundingCivName) 
                religion.foundingCivName
            else "???"
        statsTable.add(foundingCivName.tr()).pad(5f).row()
        statsTable.add("Cities following this religion:".tr())
        statsTable.add(gameInfo.getCivilization(religion.foundingCivName).religionManager.citiesFollowingThisReligionCount().toString()).pad(5f).row()
        
        val minWidth = min(statsTable.minWidth, beliefsTable.minWidth) + 5f
        
        statsTable.width = minWidth
        for (cell in beliefsTable.cells) {
            cell.minWidth(minWidth)           
        }
    }
    
    private fun createBeliefDescription(belief: Belief): Table {
        val contentsTable = Table(CameraStageBaseScreen.skin)
        contentsTable.add(belief.type.name.toLabel()).row()
        contentsTable.add(belief.name.toLabel(fontSize = 24)).row()
        contentsTable.add(belief.uniques.joinToString().toLabel())
        contentsTable.background = ImageGetter.getBackground(ImageGetter.getBlue())
        contentsTable.padTop(5f).padBottom(5f)
        return contentsTable
    }
}