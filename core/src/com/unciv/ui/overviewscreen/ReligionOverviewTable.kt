package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.ReligionState
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.utils.*
import kotlin.math.max

class ReligionOverviewTable(
    private val viewingPlayer: CivilizationInfo,
    private val overviewScreen: EmpireOverviewScreen
): Table() {
    
    val gameInfo = viewingPlayer.gameInfo
    
    private val civStatsTable = Table(BaseScreen.skin)
    
    private val religionsTable = Table(BaseScreen.skin)
    private val topButtons = Table(BaseScreen.skin)
    private val topButtonLabel = "Click an icon to see the stats of this religion".toLabel()
    private val statsTable = Table(BaseScreen.skin)
    private val beliefsTable = Table(BaseScreen.skin)
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
        if (viewingPlayer.religionManager.canGenerateProphet()) {
            statsTable.add("Minimal Faith required for\nthe next [great prophet equivalent]:"
                .fillPlaceholders(viewingPlayer.religionManager.getGreatProphetEquivalent()!!)
                .toLabel()
            ).left()
            statsTable.add(
                (viewingPlayer.religionManager.faithForNextGreatProphet() + 1)
                .toLabel()
            ).right().pad(5f).row()
        }
        
        statsTable.add("Religions to be founded:".toLabel()).left()
        
        val foundedReligions = viewingPlayer.gameInfo.civilizations.count { it.religionManager.religionState >= ReligionState.Religion }
        statsTable.add((viewingPlayer.religionManager.amountOfFoundableReligions() - foundedReligions).toLabel()).right().pad(5f).row()
        
        statsTable.add("Religious status:".toLabel()).left()
        statsTable.add(viewingPlayer.religionManager.religionState.toString().toLabel()).right().pad(5f).row()
    }
    
    private fun addReligionButtons() {
        topButtons.clear()
        val existingReligions: List<Religion> = gameInfo.civilizations.mapNotNull { it.religionManager.religion }
        for (religion in existingReligions) {
            val button: Button
            if (religion.isPantheon()) {
                val image = if (viewingPlayer.knows(religion.foundingCivName) || viewingPlayer.civName == religion.foundingCivName)
                    ImageGetter.getNationIndicator(religion.getFounder().nation, 60f)
                else
                    ImageGetter.getRandomNationIndicator(60f)
                button = Button(image, BaseScreen.skin)
            } else {
                button = Button(
                    ImageGetter.getCircledReligionIcon(religion.getIconName(), 60f),
                    BaseScreen.skin
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
        
        statsTable.add("Religion Name:".toLabel()).left()
        statsTable.add(religion.getReligionDisplayName().toLabel()).right().pad(5f).row()
        statsTable.add("Founding Civ:".toLabel()).left()
        val foundingCivName =
            if (viewingPlayer.knows(religion.foundingCivName) || viewingPlayer.civName == religion.foundingCivName) 
                religion.foundingCivName
            else Constants.unknownNationName
        statsTable.add(foundingCivName.toLabel()).right().pad(5f).row()
        if (religion.isMajorReligion()) {
            val holyCity = gameInfo.getCities().firstOrNull { it.religion.religionThisIsTheHolyCityOf == religion.name }
            if (holyCity != null) {
                statsTable.add("Holy City:".toLabel()).left()
                val cityName = 
                    if (viewingPlayer.exploredTiles.contains(holyCity.getCenterTile().position))
                        holyCity.name
                    else Constants.unknownNationName
                statsTable.add(cityName.toLabel()).right().pad(5f).row()
            }
        }
        statsTable.add("Cities following this religion:".toLabel()).left()
        statsTable.add(religion.getFounder().religionManager.numberOfCitiesFollowingThisReligion().toString()).right().pad(5f).row()
        
        val minWidth = max(statsTable.minWidth, beliefsTable.minWidth) + 5
        
        statsTable.width = minWidth
        for (cell in beliefsTable.cells) {
            cell.minWidth(minWidth)           
        }
    }

    private fun createBeliefDescription(belief: Belief) =
        MarkupRenderer.render(
            belief.run { sequence {
                yield(FormattedLine(name, size = Constants.headingFontSize, centered = true))
                yield(FormattedLine())
                yieldAll(getCivilopediaTextLines(gameInfo.ruleSet, true))
            } }.toList()
        ) {
            UncivGame.Current.setScreen(CivilopediaScreen(gameInfo.ruleSet, overviewScreen, link = it))
        }.apply {
            background = ImageGetter.getBackground(ImageGetter.getBlue())
        }
}
