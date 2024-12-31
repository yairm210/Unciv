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
import com.unciv.ui.components.extensions.toHeadingLabel
import com.unciv.ui.components.extensions.setLayer
import com.unciv.ui.components.fonts.Fonts
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
    private val happinessTable = buildStatTable()
    private val unhappinessTable = UnhappinessTable()
    private val goldAndSliderTable: Table?
    private val goldTable: Table
    private val scienceTable = buildStatTable()
    private val cultureTable = buildStatTable()
    private val faithTable = buildStatTable()
    private val greatPeopleTable = buildStatTable()
    private val scoreTable = buildStatTable()
    private val isReligionEnabled = gameInfo.isReligionEnabled()

    private val useGoldAndSliderTable =
        gameInfo.ruleset.modOptions.hasUnique(UniqueType.ConvertGoldToScience)

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        overviewScreen.game.settings.addCompletedTutorialTask("See your stats breakdown")
        super.activated(index, caption, pager)
    }

    init {
        val tablePadding = Fonts.rem(1f)  // Padding between each of the stat tables
        defaults().pad(tablePadding).top()

        unhappinessTable.update()

        if (useGoldAndSliderTable) {
            goldAndSliderTable = buildStatTable()
            goldTable = Table()
            goldAndSliderTable.add(goldTable).row()
            goldAndSliderTable.addGoldSlider()
        } else {
            goldAndSliderTable = null
            goldTable = buildStatTable()
        }

        update()

        val allStatTables = sequence {
            yield(happinessTable)
            if (unhappinessTable.show) yield(unhappinessTable)
            if (useGoldAndSliderTable) yield(goldAndSliderTable!!)
            else yield(goldTable)
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

    private fun buildStatTable() = Table().apply {
        setLayer()
        pad(Fonts.rem(1f))
        defaults().space(Fonts.rem(0.5f))
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
        add("Happiness".toHeadingLabel()).left()
        addSeparator(colSpan = 2)
        val happinessBreakdown = viewingPlayer.stats.getHappinessBreakdown()
        for ((key, value) in happinessBreakdown)
            addLabeledValue(key, value)
        addTotal(happinessBreakdown.values.sum())
    }

    inner class UnhappinessTable : Table() {
        val show: Boolean

        private val uniques: Set<Unique>

        init {
            defaults().space(Fonts.rem(0.5f))
            pad(Fonts.rem(1f))
            setLayer()

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
            add("Unhappiness".toHeadingLabel()).left()
            addSeparator(colSpan = 2)

            add(MarkupRenderer.render(
                uniques.map { FormattedLine(it) },
                labelWidth = (overviewScreen.stage.width * 0.25f).coerceAtLeast(greatPeopleTable.width * 0.8f),
                iconDisplay = FormattedLine.IconDisplay.NoLink
            )).colspan(2)
        }
    }

    private fun Table.updateStatTable(stat: Stat, statMap: StatMap) {
        add(stat.name.toHeadingLabel()).left()
        addSeparator(colSpan = 2)
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
