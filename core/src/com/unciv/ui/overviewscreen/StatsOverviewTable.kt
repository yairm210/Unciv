package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.UncivSlider
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.toLabel
import kotlin.math.roundToInt

class StatsOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    private val happinessTable = Table()
    private val goldAndSliderTable = Table()
    private val goldTable = Table()
    private val scienceTable = Table()
    private val cultureTable = Table()
    private val faithTable = Table()
    private val greatPeopleTable = Table()
    private val scoreTable = Table()
    private val isReligionEnabled = gameInfo.isReligionEnabled()

    init {
        val tablePadding = 30f  // Padding around each of the stat tables
        defaults().pad(tablePadding).top()

        happinessTable.defaults().pad(5f)
        goldTable.defaults().pad(5f)
        scienceTable.defaults().pad(5f)
        cultureTable.defaults().pad(5f)
        faithTable.defaults().pad(5f)
        greatPeopleTable.defaults().pad(5f)
        scoreTable.defaults().pad(5f)

        goldAndSliderTable.add(goldTable).row()
        if (gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.convertGoldToScience))
            goldAndSliderTable.addGoldSlider()

        update()

        val allStatTables = sequence {
            yield(happinessTable)
            yield(goldAndSliderTable)
            yield(scienceTable)
            yield(cultureTable)
            if (isReligionEnabled) yield(faithTable)
            yield(greatPeopleTable)
            yield(scoreTable)
        }

        var optimumColumns = 1
        for (numColumns in allStatTables.count() downTo 1) {
            val totalWidth = allStatTables.withIndex()
                .groupBy { it.index % numColumns }
                .mapNotNull { col ->
                    // `col` goes by column and lists the statTables in that column
                    // as Map.Entry<Int, <List<IndexedValue<Table>>>
                    col.value.maxOfOrNull {  // `it` is the IndexedValue<Table> from allStatTables.withIndex()
                        it.value.prefWidth + tablePadding * 2
                    }
                }.sum()
            if (totalWidth < overviewScreen.stage.width) {
                optimumColumns = numColumns
                break
            }
        }

        for (entry in allStatTables.withIndex()) {
            if (entry.index % optimumColumns == 0) row()
            add(entry.value)
        }
    }

    fun update() {
        val statMap = viewingPlayer.stats().getStatMapForNextTurn()
        updateHappinessTable()
        goldTable.updateStatTable(Stat.Gold, statMap)
        scienceTable.updateStatTable(Stat.Science, statMap)
        cultureTable.updateStatTable(Stat.Culture, statMap)
        if (isReligionEnabled) faithTable.updateStatTable(Stat.Faith, statMap)
        updateGreatPeopleTable()
        updateScoreTable()
    }

    private fun Table.addHeading(label: String) {
        clear()
        add(label.toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()
        addSeparator()
    }
    private fun Table.addLabeledValue(label: String, value: Float) {
        val roundedValue = value.roundToInt()
        if (roundedValue == 0) return
        add(label.toLabel()).left()
        add(roundedValue.toLabel()).right().row()
    }
    private fun Table.addTotal(value: Float) {
        add("Total".toLabel()).left()
        add(value.roundToInt().toLabel()).right()
        pack()
    }

    private fun updateHappinessTable() = happinessTable.apply {
        addHeading("Happiness")
        val happinessBreakdown = viewingPlayer.stats().getHappinessBreakdown()
        for ((key, value) in happinessBreakdown)
            addLabeledValue(key, value)
        addTotal(happinessBreakdown.values.sum())
    }

    private fun Table.updateStatTable(stat: Stat, statMap: StatMap) {
        addHeading(stat.name)
        var total = 0f
        for ((source, stats) in statMap) {
            addLabeledValue(source, stats[stat])
            total += stats[stat]
        }
        addTotal(total)
    }

    private fun Table.addGoldSlider() {
        addSeparator()
        val sliderTable = Table()
        sliderTable.add("Convert gold to science".toLabel()).row()

        val slider = UncivSlider(0f, 1f, 0.1f,
            initial = viewingPlayer.tech.goldPercentConvertedToScience,
            getTipText = UncivSlider::formatPercent
        ) {
            viewingPlayer.tech.goldPercentConvertedToScience = it
            for (city in viewingPlayer.cities) { city.cityStats.update() }
            update()
        }
        slider.isDisabled = !UncivGame.Current.worldScreen!!.canChangeState

        sliderTable.add(slider).padTop(15f)
        add(sliderTable).colspan(2)
    }

    private fun updateGreatPeopleTable() = greatPeopleTable.apply {
        clear()
        val greatPeopleHeader = Table()
        val greatPeopleIcon = ImageGetter.getStatIcon("Specialist")
        greatPeopleIcon.color = Color.ROYAL
        greatPeopleHeader.add(greatPeopleIcon).padRight(1f).size(Constants.headingFontSize.toFloat())
        greatPeopleHeader.add("Great person points".toLabel(fontSize = Constants.headingFontSize))
        add(greatPeopleHeader).colspan(3).row()
        addSeparator()
        add()
        add("Current points".toLabel())
        add("Points per turn".toLabel()).row()

        val greatPersonPoints = viewingPlayer.greatPeople.greatPersonPointsCounter
        val greatPersonPointsPerTurn = viewingPlayer.getGreatPersonPointsForNextTurn()
        val pointsToGreatPerson = viewingPlayer.greatPeople.pointsForNextGreatPerson
        for ((greatPerson, points) in greatPersonPoints) {
            add(greatPerson.toLabel()).left()
            add("$points/$pointsToGreatPerson".toLabel())
            add(greatPersonPointsPerTurn[greatPerson]!!.toLabel()).right().row()
        }

        val pointsForGreatGeneral = viewingPlayer.greatPeople.greatGeneralPoints
        val pointsForNextGreatGeneral = viewingPlayer.greatPeople.pointsForNextGreatGeneral
        add("Great General".toLabel()).left()
        add("$pointsForGreatGeneral/$pointsForNextGreatGeneral".toLabel())
        pack()
    }

    private fun updateScoreTable() = scoreTable.apply {
        clear()
        val scoreHeader = Table()
        val scoreIcon = ImageGetter.getImage("OtherIcons/Cultured")
        scoreIcon.color = Color.FIREBRICK
        scoreHeader.add(scoreIcon).padRight(1f).size(Constants.headingFontSize.toFloat())
        scoreHeader.add("Score".toLabel(fontSize = Constants.headingFontSize))
        add(scoreHeader).colspan(2).row()
        addSeparator()

        val scoreBreakdown = viewingPlayer.calculateScoreBreakdown()
        for ((label, value) in scoreBreakdown)
            addLabeledValue(label, value.toFloat())
        addTotal(scoreBreakdown.values.sum().toFloat())
    }
}
