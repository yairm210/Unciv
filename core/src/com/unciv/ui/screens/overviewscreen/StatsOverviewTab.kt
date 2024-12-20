package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import kotlin.math.roundToInt

class StatsOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    private val happinessTable = Table()
    private val unhappinessTable = UnhappinessTable()
    private val goldAndSliderTable = Table()
    private val goldTable = Table()
    private val scienceTable = Table()
    private val cultureTable = Table()
    private val faithTable = Table()
    private val greatPeopleTable = Table()
    private val scoreTable = Table()
    private val isReligionEnabled = gameInfo.isReligionEnabled()

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        overviewScreen.game.settings.addCompletedTutorialTask("See your stats breakdown")
        super.activated(index, caption, pager)
    }

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
        unhappinessTable.update()

        goldAndSliderTable.add(goldTable).row()
        if (gameInfo.ruleset.modOptions.hasUnique(UniqueType.ConvertGoldToScience))
            goldAndSliderTable.addGoldSlider()

        update()

        val allStatTables = sequence {
            yield(happinessTable)
            if (unhappinessTable.show) yield(unhappinessTable)
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
        val statMap = viewingPlayer.stats.getStatMapForNextTurn()
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
        add(label.toLabel(hideIcons = true)).left()
        add(roundedValue.toLabel()).right().row()
    }
    private fun Table.addTotal(value: Float) {
        add("Total".toLabel()).left()
        add(value.roundToInt().toLabel()).right()
        pack()
    }

    private fun updateHappinessTable() = happinessTable.apply {
        addHeading("Happiness")
        val happinessBreakdown = viewingPlayer.stats.getHappinessBreakdown()
        for ((key, value) in happinessBreakdown)
            addLabeledValue(key, value)
        addTotal(happinessBreakdown.values.sum())
    }

    inner class UnhappinessTable : Table() {
        val show: Boolean

        private val uniques: Set<Unique>

        init {
            defaults().pad(5f)
            uniques = sequenceOf(
                    UniqueType.ConditionalBetweenHappiness,
                    UniqueType.ConditionalBelowHappiness
                ).flatMap { conditionalType ->
                    viewingPlayer.getTriggeredUniques(conditionalType)
                        .sortedBy { it.type } // otherwise order might change as a HashMap is involved
                }.filterNot { it.isHiddenToUsers() }
                .toSet()
            show = uniques.isNotEmpty()
        }

        fun update() {
            add(ImageGetter.getStatIcon("Malcontent"))
                .size(Constants.headingFontSize.toFloat())
                .right().padRight(1f)
            add("Unhappiness".toLabel(fontSize = Constants.headingFontSize)).left()
            addSeparator()

            add(MarkupRenderer.render(
                uniques.map { FormattedLine(it) },
                labelWidth = (overviewScreen.stage.width * 0.25f).coerceAtLeast(greatPeopleTable.width * 0.8f),
                iconDisplay = FormattedLine.IconDisplay.NoLink
            )).colspan(2)
        }
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
        val slider = UncivSlider("Convert gold to science", 0f, 1f, 0.1f,
            viewingPlayer.tech.goldPercentConvertedToScience,
            getTipText = UncivSlider::formatPercent
        ) {
            viewingPlayer.tech.goldPercentConvertedToScience = it
            for (city in viewingPlayer.cities) { city.cityStats.update() }
            update()
        }
        slider.isDisabled = !GUI.isAllowedChangeState()

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
        val greatPersonPointsPerTurn = viewingPlayer.greatPeople.getGreatPersonPointsForNextTurn()
        for ((greatPerson, points) in greatPersonPoints) {
            val pointsToGreatPerson = viewingPlayer.greatPeople.getPointsRequiredForGreatPerson(greatPerson)
            add(greatPerson.toLabel()).left()
            add("$points/$pointsToGreatPerson".toLabel())
            add(greatPersonPointsPerTurn[greatPerson].toLabel()).right().row()
        }

        val greatGeneralPoints = viewingPlayer.greatPeople.greatGeneralPointsCounter
        val pointsForNextGreatGeneral = viewingPlayer.greatPeople.pointsForNextGreatGeneralCounter
        for ((unit, points) in greatGeneralPoints) {
            val pointsToGreatGeneral = pointsForNextGreatGeneral[unit]
            add(unit.toLabel()).left()
            add("$points/$pointsToGreatGeneral".toLabel())
        }

        pack()
    }

    private fun updateScoreTable() = scoreTable.apply {
        clear()
        val scoreHeader = Table()
        val scoreIcon = ImageGetter.getImage("OtherIcons/Score")
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
