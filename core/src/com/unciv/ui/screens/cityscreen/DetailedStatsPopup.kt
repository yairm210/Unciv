package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.StatTreeNode
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import java.text.DecimalFormat
import kotlin.math.max

class DetailedStatsPopup(
    private val cityScreen: CityScreen
) : Popup(cityScreen, Scrollability.None) {
    private val headerTable = Table()
    private val totalTable = Table()

    private var sourceHighlighted: String? = null
    private var onlyWithStat: Stat? = null
    private var isDetailed: Boolean = false

    private val colorTotal: Color = Color.BLUE.brighten(0.5f)
    private val colorSelector: Color = Color.GREEN.darken(0.5f)

    private val percentFormatter = DecimalFormat("0.#%").apply { positivePrefix = "+"; multiplier = 1 }
    private val decimalFormatter = DecimalFormat("0.#")

    init {
        headerTable.defaults().pad(3f, 0f)
        add(headerTable).padBottom(0f).row()

        totalTable.defaults().pad(3f, 0f)

        val scrollPane = AutoScrollPane(totalTable)
        scrollPane.setOverscroll(false, false)
        val scrollPaneCell = add(scrollPane).padTop(0f)
        scrollPaneCell.maxHeight(cityScreen.stage.height *3 / 4)

        row()
        addCloseButton(additionalKey = KeyCharAndCode.SPACE)
        update()

        showListeners.add { cityScreen.pauseFireworks = true }
        closeListeners.add { cityScreen.pauseFireworks = false }
    }

    private fun update() {
        headerTable.clear()
        totalTable.clear()

        val cityStats = cityScreen.city.cityStats
        val showFaith = cityScreen.city.civ.gameInfo.isReligionEnabled()

        val stats = when {
            onlyWithStat != null -> listOfNotNull(onlyWithStat)
            !showFaith -> Stat.values().filter { it != Stat.Faith }
            else -> Stat.values().toList()
        }
        val columnCount = stats.size + 1
        val statColMinWidth = if (onlyWithStat != null) 150f else 110f

        headerTable.add(getToggleButton(isDetailed)).minWidth(150f).grow()

        for (stat in stats) {
            val label = stat.name.toLabel()
            label.onClick {
                onlyWithStat = if (onlyWithStat == null) stat else null
                update()
            }
            headerTable.add(wrapInTable(label, if (onlyWithStat == stat) colorSelector else null))
                .minWidth(statColMinWidth).grow()
        }
        headerTable.row()
        headerTable.addSeparator().padBottom(2f)

        totalTable.add("Base values".toLabel().apply { setAlignment(Align.center) })
            .colspan(columnCount).growX().row()
        totalTable.addSeparator(colSpan = columnCount).padTop(2f)
        traverseTree(totalTable, stats, cityStats.baseStatTree, mergeHappiness = true, percentage = false)

        totalTable.addSeparator().padBottom(2f)
        totalTable.add("Bonuses".toLabel().apply { setAlignment(Align.center) })
            .colspan(columnCount).growX().row()
        totalTable.addSeparator().padTop(2f)
        traverseTree(totalTable, stats, cityStats.statPercentBonusTree, percentage = true)

        totalTable.addSeparator().padBottom(2f)
        totalTable.add("Final".toLabel().apply { setAlignment(Align.center) })
            .colspan(columnCount).growX().row()
        totalTable.addSeparator().padTop(2f)

        val final = LinkedHashMap<Stat, Float>()
        val map = cityStats.finalStatList.toSortedMap()

        for ((key, value) in cityScreen.city.cityStats.happinessList) {
            if (!map.containsKey(key)) {
                map[key] = Stats(happiness = value)
            } else if (map[key]!![Stat.Happiness] == 0f) {
                map[key]!![Stat.Happiness] = value
            }
        }

        for ((source, finalStats) in map) {

            if (finalStats.isEmpty())
                continue

            if (onlyWithStat != null && finalStats[onlyWithStat!!] == 0f)
                continue

            val label = source.toLabel(hideIcons = true).apply {
                setAlignment(Align.left)
                onClick {
                    sourceHighlighted = if (sourceHighlighted == source) null else source
                    update()
                }
            }

            val color = colorSelector.takeIf { sourceHighlighted == source }
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
            totalTable.add(wrapInTable(final[stat]?.toOneDecimalLabel(), colorTotal))
                .minWidth(statColMinWidth).grow()
        }
        totalTable.row()

        // Mini version of IPageExtensions.equalizeColumns - the number columns work thanks to statColMinWidth
        headerTable.packIfNeeded()
        totalTable.packIfNeeded()
        val firstColumnWidth = max(totalTable.getColumnWidth(0), headerTable.getColumnWidth(0))
        headerTable.cells.first().minWidth(firstColumnWidth)
        totalTable.cells.first().minWidth(firstColumnWidth)
        headerTable.invalidate()
        totalTable.invalidate()
    }

    private fun getToggleButton(showDetails: Boolean): IconCircleGroup {
        val label = (if (showDetails) "-" else "+").toLabel()
        label.setAlignment(Align.center)
        val button = label
            .surroundWithCircle(25f, color = BaseScreen.skinStrings.skinConfig.baseColor)
            .surroundWithCircle(27f, false)
        button.onActivation(binding = KeyboardBinding.ShowStatDetails) {
            isDetailed = !isDetailed
            update()
        }
        button.keyShortcuts.add(Input.Keys.PLUS)  //todo Choose alternative (alt binding, remove, auto-equivalence, multikey bindings)
        return button
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

            val label = text.toLabel(hideIcons = true).apply {
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

    private fun Float.toPercentLabel() = percentFormatter.format(this).toLabel()
    private fun Float.toOneDecimalLabel() = decimalFormatter.format(this).toLabel()
}
