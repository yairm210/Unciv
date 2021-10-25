package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.ReligionState
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.max
import kotlin.math.min

class ReligionOverviewTable(
    private val viewingPlayer: CivilizationInfo,
    private val overviewScreen: EmpireOverviewScreen
): Table() {
    
    val gameInfo = viewingPlayer.gameInfo
    
    private val civStatsTable = Table(CameraStageBaseScreen.skin)
    
    private val religionsTable = Table(CameraStageBaseScreen.skin)
    private val topButtons = Table(CameraStageBaseScreen.skin)
    private val topButtonLabel = "Click an icon to see the stats of this religion".toLabel()
    private val statsTable = Table(CameraStageBaseScreen.skin)
    private val beliefsTable = Table(CameraStageBaseScreen.skin)
    private var selectedReligion: String? = null
    
    init {
        addCivSpecificStats(civStatsTable)
        addReligionButtons()

        religionsTable.add(topButtons).pad(5f).row()
        religionsTable.add(topButtonLabel).pad(5f)
        religionsTable.addSeparator()
        religionsTable.add(statsTable).pad(5f).row()
        religionsTable.add(beliefsTable).pad(5f)
        
        add(civStatsTable).top().left().padRight(25f)
        add(religionsTable)
    }
    
    private fun addCivSpecificStats(statsTable: Table) {
        statsTable.add("Minimal faith required for\nthe next great prophet: ".toLabel()).left()
        statsTable.add((viewingPlayer.religionManager.faithForNextGreatProphet() + 1).toLabel()).right().pad(5f).row()
        statsTable.add("Religions that can still be founded: ".toLabel()).left()
        
        val foundedReligions = viewingPlayer.gameInfo.civilizations.count { it.religionManager.religionState >= ReligionState.Religion }
        statsTable.add((viewingPlayer.religionManager.amountOfFoundableReligions() - foundedReligions).toLabel()).right().pad(5f).row()
        
        statsTable.add("Religion status: ".toLabel()).left()
        statsTable.add(viewingPlayer.religionManager.religionState.toString().toLabel()).right().pad(5f).row()
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
                button = Button(
                    ImageGetter.getCircledReligionIcon(religion.getIconName(), 60f),
                    CameraStageBaseScreen.skin
                )
            }
            
            button.onClick {
                selectedReligion = religion.name
                addReligionButtons()
                loadReligion(religion)
            }
            
            if (selectedReligion == religion.name)
                button.disable()
            
            topButtons.add(button).pad(5f)
        }
    }
    
    private fun loadReligion(religion: Religion) {
        statsTable.clear()
        beliefsTable.clear()
        topButtonLabel.setText(religion.getReligionDisplayName().tr())
        for (belief in religion.getAllBeliefsOrdered()) {
            beliefsTable.add(createBeliefDescription(belief)).pad(10f).row()
        }
        
        statsTable.add("Religion Name:".toLabel())
        statsTable.add(religion.getReligionDisplayName().toLabel()).pad(5f).row()
        statsTable.add("Founding Civ:".toLabel())
        val foundingCivName =
            if (viewingPlayer.knows(religion.foundingCivName) || viewingPlayer.civName == religion.foundingCivName) 
                religion.foundingCivName
            else "???"
        statsTable.add(foundingCivName.toLabel()).pad(5f).row()
        if (religion.isMajorReligion()) {
            val holyCity = gameInfo.getCities().firstOrNull { it.religion.religionThisIsTheHolyCityOf == religion.name }
            if (holyCity != null) {
                statsTable.add("Holy City:".toLabel())
                val cityName = 
                    if (viewingPlayer.exploredTiles.contains(holyCity.getCenterTile().position))
                        holyCity.name
                    else "???"
                statsTable.add(cityName.toLabel()).pad(5f).row()
            }
        }
        statsTable.add("Cities following this religion:".toLabel())
        statsTable.add(gameInfo.getCivilization(religion.foundingCivName).religionManager.numberOfCitiesFollowingThisReligion().toString()).pad(5f).row()
        
        val minWidth = max(statsTable.minWidth, beliefsTable.minWidth) + 5
        
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
