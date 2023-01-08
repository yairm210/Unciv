package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.StatTreeNode
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.brighten
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import java.text.DecimalFormat

class DetailedStatsPopup(val cityScreen: CityScreen, stageToShowOn: Stage) : Popup(
    stageToShowOn = stageToShowOn,
    scrollable = false) {

    constructor(screen: CityScreen) : this(screen, screen.stage)

    private val totalTable = Table()

    private var sourceHighlighted: String? = null
    private var onlyWithStat: Stat? = null
    private var isDetailed: Boolean = false

    private val colorTotal: Color = Color.BLUE.brighten(0.5f)
    private val colorSelector: Color = Color.GREEN.darken(0.5f)

    init {

        val scrollPane = AutoScrollPane(totalTable)
        scrollPane.setOverscroll(false, false)
        val scrollPaneCell = add(scrollPane)
        scrollPaneCell.maxHeight(cityScreen.stage.height *3 / 4)

        row()
        addCloseButton("Cancel", KeyCharAndCode('n'))
        update()
    }

    private fun update() {

        totalTable.clear()

        val cityStats = cityScreen.city.cityStats
        val showFaith = cityScreen.city.civInfo.gameInfo.isReligionEnabled()

        val stats = when {
            onlyWithStat != null -> listOfNotNull(onlyWithStat)
            !showFaith -> Stat.values().filter { it != Stat.Faith }
            else -> Stat.values().toList()
        }

        totalTable.defaults().pad(3f).padLeft(0f).padRight(0f)

        totalTable.add(getToggleButton(isDetailed).onClick {
            isDetailed = !isDetailed
            update() }).minWidth(150f).grow()

        for (stat in stats) {
            val label = stat.name.toLabel()
            label.onClick {
                onlyWithStat = if (onlyWithStat == null) stat else null
                update()
            }
            totalTable.add(wrapInTable(label, if (onlyWithStat == stat) colorSelector else null))
                .minWidth(if (onlyWithStat == stat) 150f else 110f).grow()
        }
        totalTable.row()

        totalTable.addSeparator().padBottom(2f)
        totalTable.add("Base values".toLabel().apply { setAlignment(Align.center) })
            .colspan(totalTable.columns).padLeft(0f).padRight(0f).growX().row()
        totalTable.addSeparator().padTop(2f)
        traverseTree(totalTable, stats, cityStats.baseStatTree, mergeHappiness = true, percentage = false)

        totalTable.addSeparator().padBottom(2f)
        totalTable.add("Bonuses".toLabel().apply { setAlignment(Align.center) })
            .colspan(totalTable.columns).padLeft(0f).padRight(0f).growX().row()
        totalTable.addSeparator().padTop(2f)
        traverseTree(totalTable, stats, cityStats.statPercentBonusTree, percentage = true)

        totalTable.addSeparator().padBottom(2f)
        totalTable.add("Final".toLabel().apply { setAlignment(Align.center) })
            .colspan(totalTable.columns).padLeft(0f).padRight(0f).growX().row()
        totalTable.addSeparator().padTop(2f)

        val final = LinkedHashMap<Stat, Float>()
        val map = cityStats.finalStatList.toSortedMap()

        for ((key, value) in cityScreen.city.cityStats.happinessList) {
            if (!map.containsKey(key)) {
                map[key] = Stats()
                map[key]!![Stat.Happiness] = value
            } else if (map[key]!![Stat.Happiness] == 0f) {
                map[key]!![Stat.Happiness] = value
            }
        }

        for ((source, finalStats) in map) {

            if (finalStats.all { it.value == 0f })
                continue

            if (onlyWithStat != null && finalStats[onlyWithStat!!] == 0f)
                continue

            val label = source.toLabel().apply {
                setAlignment(Align.left)
                onClick {
                    sourceHighlighted = if (sourceHighlighted == source) null else source
                    update()
                }
            }

            var color: Color? = null

            if (sourceHighlighted == source)
                color = colorSelector

            totalTable.add(wrapInTable(label, color, Align.left)).grow()

            for (stat in stats) {
                val value = finalStats[stat]
                val cell = when (value) {
                    0f -> "-".toLabel()
                    else -> value.toOneDecimalLabel()
                }

                totalTable.add(wrapInTable(cell, color)).grow()

                var f = final[stat]
                if (f == null)
                    f = 0f
                f += value
                final[stat] = f

            }
            totalTable.row()
        }

        totalTable.add(wrapInTable("Total".toLabel(), colorTotal)).grow()
        for (stat in stats) {
            totalTable.add(wrapInTable(final[stat]?.toOneDecimalLabel(), colorTotal)).grow()
        }
        totalTable.row()
    }

    private fun getToggleButton(showDetails: Boolean): IconCircleGroup {
        val label = (if (showDetails) "-" else "+").toLabel()
        label.setAlignment(Align.center)
        return label
            .surroundWithCircle(25f, color = BaseScreen.skinStrings.skinConfig.baseColor)
            .surroundWithCircle(27f, false)
    }

    private fun traverseTree(
        table: Table,
        stats: List<Stat>,
        statTreeNode: StatTreeNode,
        mergeHappiness: Boolean = false,
        percentage: Boolean = false,
        indentation: Int = 0
    ) {

        val total = LinkedHashMap<Stat, Float>()
        val map = statTreeNode.children.toSortedMap()

        if (mergeHappiness) {
            for ((key, value) in cityScreen.city.cityStats.happinessList) {
                if (!map.containsKey(key)) {
                    map[key] = StatTreeNode()
                    map[key]?.setInnerStat(Stat.Happiness, value)
                } else if (map[key]!!.totalStats.happiness == 0f) {
                    map[key]?.setInnerStat(Stat.Happiness, value)
                }
            }
        }

        for ((name, child) in map) {

            val text = "- ".repeat(indentation) + name.tr()

            if (child.totalStats.all { it.value == 0f }) {
                table.row()
                continue
            }

            if (onlyWithStat != null && child.totalStats[onlyWithStat!!] == 0f) {
                table.row()
                continue
            }

            val label = text.toLabel().apply {
                setAlignment(Align.left)
                onClick {
                    sourceHighlighted = if (sourceHighlighted == text) null else text
                    update()
                }
            }

            var color: Color? = null

            if (sourceHighlighted == text)
                color = colorSelector

            table.add(wrapInTable(label, color, Align.left)).fill().left()

            for (stat in stats) {
                val value = child.totalStats[stat]
                val cell = when {
                    value == 0f -> "-".toLabel()
                    percentage ->  value.toPercentLabel()
                    else -> value.toOneDecimalLabel()
                }

                table.add(wrapInTable(cell, color)).grow()

                if (indentation == 0) {
                    var current = total[stat]
                    if (current == null)
                        current = 0f
                    total[stat] = current + value
                }
            }

            table.row()
            if (isDetailed)
                traverseTree(table, stats, child, percentage = percentage, indentation = indentation + 1)

        }

        if (indentation == 0) {
            table.add(wrapInTable("Total".toLabel(), colorTotal)).grow()
            for (stat in stats) {
                if (percentage)
                    table.add(wrapInTable(total[stat]?.toPercentLabel(), colorTotal)).grow()
                else
                    table.add(wrapInTable(total[stat]?.toOneDecimalLabel(), colorTotal)).grow()
            }
            table.row()
        }

    }

    private fun wrapInTable(label: Label?, color: Color? = null, align: Int = Align.center) : Table {
        val tbl = Table()
        label?.setAlignment(align)
        if (color != null)
            tbl.background = BaseScreen.skinStrings.getUiBackground("General/Border", tintColor = color)
        tbl.add(label).growX()
        return tbl
    }

    companion object {
        private fun Float.toPercentLabel() =
                "${if (this>0f) "+" else ""}${DecimalFormat("0.#").format(this)}%".toLabel()
        private fun Float.toOneDecimalLabel() =
                DecimalFormat("0.#").format(this).toLabel()
    }
}

