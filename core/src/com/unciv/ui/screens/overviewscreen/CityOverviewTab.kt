package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel

class CityOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class CityTabPersistableData(
        var sortedBy: CityOverviewTabColumn = CityOverviewTabColumn.CityColumn,
        var descending: Boolean = false
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = sortedBy == CityOverviewTabColumn.CityColumn
    }

    override val persistableData = (persistedData as? CityTabPersistableData) ?: CityTabPersistableData()

    companion object {
        const val iconSize = 50f  //if you set this too low, there is a chance that the tables will be misaligned
        const val paddingVert = 5f      // vertical padding
        const val paddingHorz = 8f      // horizontal padding
    }

    private val cityInfoTableHeader = Table(skin)
    private val cityInfoTableDetails = Table(skin)
    private val cityInfoTableTotal = Table(skin)

    override fun getFixedContent() = Table().apply {
        add("Cities".toLabel(fontSize = Constants.headingFontSize)).padTop(10f).row()
        add(cityInfoTableHeader).padBottom(paddingVert).row()
        addSeparator(Color.GRAY)
    }

    init {
        cityInfoTableHeader.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        cityInfoTableDetails.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        cityInfoTableTotal.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)

        updateTotal()
        update()

        top()
        add(cityInfoTableDetails).row()
        addSeparator(Color.GRAY).pad(paddingVert, 0f)
        add(cityInfoTableTotal)
    }

    private fun toggleSort(sortBy: CityOverviewTabColumn) {
        if (sortBy == persistableData.sortedBy) {
            persistableData.descending = !persistableData.descending
        } else {
            persistableData.sortedBy = sortBy
            persistableData.descending = sortBy.defaultDescending
        }
    }

    private fun getSortSymbol() = if(persistableData.descending) "￬" else "￪"

    private fun update() {
        updateHeader()
        updateCities()
        equalizeColumns(cityInfoTableDetails, cityInfoTableHeader, cityInfoTableTotal)
        layout()
    }

    private fun updateHeader() {
        fun sortOnClick(sortBy: CityOverviewTabColumn) {
            toggleSort(sortBy)
            // sort the table: clear and fill with sorted data
            update()
        }

        fun getSortIcon(column: CityOverviewTabColumn): Group {
            val group = Group()
            val actor = column.getHeaderIcon() ?: return group

            group.apply {
                isTransform = false
                setSize(iconSize, iconSize)
                onClick { sortOnClick(column) }
            }

            if (column == persistableData.sortedBy) {
                val label = getSortSymbol().toLabel()
                label.setOrigin(Align.bottomRight)
                label.setPosition(iconSize - 2f, 0f)
                group.addActor(label)
            }

            actor.setSize(iconSize, iconSize)
            actor.center(this)
            actor.setOrigin(Align.center)
            if (column.headerTip.isNotEmpty())
                actor.addTooltip(column.headerTip, 18f, tipAlign = Align.center)
            group.addActor(actor)
            return group
        }

        cityInfoTableHeader.clear()
        for (column in CityOverviewTabColumn.values()) {
            cityInfoTableHeader.add(getSortIcon(column))
                .size(iconSize).align(column.align)
                .fill(column.fillX, false).expand(column.expandX, false)
        }
        cityInfoTableHeader.pack()
    }

    private fun updateCities() {
        cityInfoTableDetails.clear()
        if (viewingPlayer.cities.isEmpty()) return

        val sorter = persistableData.sortedBy.getComparator()
        var cityList = viewingPlayer.cities.sortedWith(sorter)
        if (persistableData.descending)
            cityList = cityList.reversed()

        val constructionCells = mutableListOf<Cell<Actor>>()
        for (city in cityList) {
            for (column in CityOverviewTabColumn.values()) {
                val actor = column.getEntryActor(city, overviewScreen)
                if (actor == null) {
                    cityInfoTableDetails.add()
                    continue
                }
                val cell = cityInfoTableDetails.add(actor).align(column.align)
                    .fill(column.fillX, false).expand(column.expandX, false)
                if (column.equalizeHeight) constructionCells.add(cell)
            }
            cityInfoTableDetails.row()
        }

        // row heights may diverge - fix it by setting minHeight to
        // largest actual height (of the construction cell) - !! guarded by isEmpty test above
        val largestLabelHeight = constructionCells.maxByOrNull{ it.prefHeight }!!.prefHeight
        for (cell in constructionCells) cell.minHeight(largestLabelHeight)

        cityInfoTableDetails.pack()
    }

    private fun updateTotal() {
        for (column in CityOverviewTabColumn.values()) {
            val actor = column.getTotalsActor(viewingPlayer.cities)
            cityInfoTableTotal.add(actor).align(column.align)
                .fill(column.fillX, false).expand(column.expandX, false)
        }
        cityInfoTableTotal.pack()
    }
}
