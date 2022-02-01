package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.*
import kotlin.math.roundToInt

class StatsOverviewTable (
    private val viewingPlayer: CivilizationInfo,
    private val overviewScreen: EmpireOverviewScreen
) : Table() {

    init {
        defaults().pad(40f)
        add(getHappinessTable()).top()
        add(getGoldTable()).top()
        add(getScienceTable()).top()
        add(getGreatPeopleTable()).top()
        add(getScoreTable()).top()
    }

    private fun getHappinessTable(): Table {
        val happinessTable = Table(BaseScreen.skin)
        happinessTable.defaults().pad(5f)
        happinessTable.add("Happiness".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()
        happinessTable.addSeparator()

        val happinessBreakdown = viewingPlayer.stats().getHappinessBreakdown()

        for (entry in happinessBreakdown.filterNot { it.value.roundToInt()==0 }) {
            happinessTable.add(entry.key.toLabel())
            happinessTable.add(entry.value.roundToInt().toString()).right().row()
        }
        happinessTable.add("Total".toLabel())
        happinessTable.add(happinessBreakdown.values.sum().roundToInt().toString()).right()
        happinessTable.pack()
        return happinessTable
    }

    private fun getGoldTable(): Table {
        val goldTable = Table(BaseScreen.skin)
        goldTable.defaults().pad(5f)
        goldTable.add("Gold".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()
        goldTable.addSeparator()
        var total = 0f
        for (entry in viewingPlayer.stats().getStatMapForNextTurn()) {
            if (entry.value.gold == 0f) continue
            goldTable.add(entry.key.toLabel())
            goldTable.add(entry.value.gold.roundToInt().toString()).right().row()
            total += entry.value.gold
        }
        goldTable.add("Total".toLabel())
        goldTable.add(total.roundToInt().toString()).right()

        if (viewingPlayer.gameInfo.ruleSet.modOptions.hasUnique(UniqueType.ConvertGoldToScience)) {
            goldTable.addSeparator()
            val sliderTable = Table()
            sliderTable.add("Convert gold to science".toLabel()).row()
            val slider = Slider(0f, 1f, 0.1f, false, BaseScreen.skin)
            slider.value = viewingPlayer.tech.goldPercentConvertedToScience

            slider.onChange {
                viewingPlayer.tech.goldPercentConvertedToScience = slider.value
                viewingPlayer.cities.forEach { it.cityStats.update() }
                overviewScreen.setCategoryActions["Stats"]!!()      // ? will probably steal focus and so prevent dragging the slider
            }
            sliderTable.add(slider)
            goldTable.add(sliderTable).colspan(2)
        }

        goldTable.pack()
        return goldTable
    }

    private fun getScienceTable(): Table {
        val scienceTable = Table(BaseScreen.skin)
        scienceTable.defaults().pad(5f)
        scienceTable.add("Science".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()
        scienceTable.addSeparator()
        val scienceStats = viewingPlayer.stats().getStatMapForNextTurn()
            .filter { it.value.science != 0f }
        for (entry in scienceStats) {
            scienceTable.add(entry.key.toLabel())
            scienceTable.add(entry.value.science.roundToInt().toString()).right().row()
        }
        scienceTable.add("Total".toLabel())
        scienceTable.add(scienceStats.map { it.value.science }.sum().roundToInt().toString()).right()
        scienceTable.pack()
        return scienceTable
    }

    private fun getGreatPeopleTable(): Table {
        val greatPeopleTable = Table(BaseScreen.skin)

        greatPeopleTable.defaults().pad(5f)
        val greatPeopleHeader = Table(BaseScreen.skin)
        val greatPeopleIcon = ImageGetter.getStatIcon("Specialist")
        greatPeopleIcon.color = Color.ROYAL
        greatPeopleHeader.add(greatPeopleIcon).padRight(12f).size(30f)
        greatPeopleHeader.add("Great person points".toLabel(fontSize = Constants.headingFontSize)).padTop(5f)
        greatPeopleTable.add(greatPeopleHeader).colspan(3).row()
        greatPeopleTable.addSeparator()
        greatPeopleTable.add()
        greatPeopleTable.add("Current points".toLabel())
        greatPeopleTable.add("Points per turn".toLabel()).row()

        val greatPersonPoints = viewingPlayer.greatPeople.greatPersonPointsCounter
        val greatPersonPointsPerTurn = viewingPlayer.getGreatPersonPointsForNextTurn()
        val pointsToGreatPerson = viewingPlayer.greatPeople.pointsForNextGreatPerson

        for((greatPerson, points) in greatPersonPoints) {
            greatPeopleTable.add(greatPerson.toLabel())
            greatPeopleTable.add("$points/$pointsToGreatPerson")
            greatPeopleTable.add(greatPersonPointsPerTurn[greatPerson].toString()).row()
        }
        val pointsForGreatGeneral = viewingPlayer.greatPeople.greatGeneralPoints
        val pointsForNextGreatGeneral = viewingPlayer.greatPeople.pointsForNextGreatGeneral
        greatPeopleTable.add("Great General".toLabel())
        greatPeopleTable.add("$pointsForGreatGeneral/$pointsForNextGreatGeneral").row()
        greatPeopleTable.pack()
        return greatPeopleTable
    }
    
    private fun getScoreTable(): Table {
        val scoreTableHeader = Table(BaseScreen.skin)
        scoreTableHeader.add("Score".toLabel(fontSize = Constants.headingFontSize)).padBottom(6f)
        
        val scoreTable = Table(BaseScreen.skin)
        scoreTable.defaults().pad(5f)
        scoreTable.add(scoreTableHeader).colspan(2).row()
        scoreTable.addSeparator()
        
        val scoreBreakdown = viewingPlayer.calculateScoreBreakdown().filter { it.value != 0.0 }
        for ((label, value) in scoreBreakdown) {
            scoreTable.add(label.toLabel())
            scoreTable.add(value.toInt().toLabel()).row()
        }
        
        scoreTable.add("Total".toLabel())
        scoreTable.add(scoreBreakdown.values.sum().toInt().toLabel())
        return scoreTable
    }
}
