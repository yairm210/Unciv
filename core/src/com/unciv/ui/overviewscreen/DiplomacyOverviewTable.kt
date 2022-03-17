package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.*

class DiplomacyOverviewTab (
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class DiplomacyTabPersistableData(
        var includeCityStates: Boolean = false
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = !includeCityStates
    }
    override val persistableData = (persistedData as? DiplomacyTabPersistableData) ?: DiplomacyTabPersistableData()

    init {
        update()
    }

    fun update() {
        clear()
        val relevantCivs = gameInfo.civilizations
            .filter { !it.isBarbarian() && !it.isSpectator() && (persistableData.includeCityStates || !it.isCityState()) }
        val diplomacyGroup = DiplomacyGroup(viewingPlayer, overviewScreen.centerAreaHeight, persistableData.includeCityStates)
        val playerKnowsAndUndefeatedCivs = relevantCivs.filter { diplomacyGroup.playerKnows(it) && !it.isDefeated() }
        val playerKnowsAndDefeatedCivs = relevantCivs.filter { diplomacyGroup.playerKnows(it) && it.isDefeated() }
        if (playerKnowsAndUndefeatedCivs.size > 1)
            add(diplomacyGroup).top()

        val titleTable = Table()
        titleTable.add("Our Civilization:".toLabel()).colspan(2).row()
        titleTable.add(ImageGetter.getNationIndicator(viewingPlayer.nation, 25f)).pad(5f)
        titleTable.add(viewingPlayer.civName.toLabel()).left().padRight(10f)
        titleTable.add(viewingPlayer.calculateScoreBreakdown().values.sum().toInt().toLabel()).row()

        val civTableScrollPane = getCivTableScroll(relevantCivs, titleTable, playerKnowsAndUndefeatedCivs, playerKnowsAndDefeatedCivs)

        val toggleCityStatesButton = "City-States".toTextButton()
        toggleCityStatesButton.color = if (persistableData.includeCityStates) Color.RED else Color.GREEN
        toggleCityStatesButton.onClick {
            persistableData.includeCityStates = !persistableData.includeCityStates
            update()
        }

        val floatingTable = Table()
        floatingTable.add(toggleCityStatesButton).row()
        floatingTable.add(civTableScrollPane.addBorder(2f, Color.WHITE)).pad(10f)
        add(floatingTable)
    }


    private fun getCivMiniTable(civInfo: CivilizationInfo): Table {
        val table = Table()
        table.add(ImageGetter.getNationIndicator(civInfo.nation, 25f)).pad(5f)
        table.add(civInfo.civName.toLabel()).left().padRight(10f)
        table.touchable = Touchable.enabled
        table.onClick {
            if (civInfo.isDefeated() || viewingPlayer.isSpectator() || civInfo == viewingPlayer) return@onClick
            UncivGame.Current.setScreen(DiplomacyScreen(viewingPlayer).apply { updateRightSide(civInfo) })
        }
        return table
    }

    private fun getCivTableScroll(relevantCivs: List<CivilizationInfo>, titleTable: Table,
                                  playerKnowsAndUndefeatedCivs: List<CivilizationInfo>,
                                  playerKnowsAndDefeatedCivs: List<CivilizationInfo>): AutoScrollPane {
        val civTable = Table()
        civTable.defaults().pad(5f)
        civTable.background = ImageGetter.getBackground(Color.BLACK)
        civTable.add("[${relevantCivs.size}] Civilizations in the game".toLabel()).pad(5f).colspan(2).row()
        civTable.add(titleTable).colspan(2).row()
        val turnsTillNextDiplomaticVote = viewingPlayer.getTurnsTillNextDiplomaticVote()
        if (turnsTillNextDiplomaticVote != null)
            civTable.add("Turns until the next\ndiplomacy victory vote: [$turnsTillNextDiplomaticVote]".toLabel()).center().pad(5f).colspan(2).row()
        civTable.addSeparator()
        civTable.add("Known and alive ([${playerKnowsAndUndefeatedCivs.size - 1}])".toLabel())
            .pad(5f).colspan(2).row()
        if (playerKnowsAndUndefeatedCivs.size > 1) {
            civTable.addSeparator()
            var cityStatesParsed = 0
            playerKnowsAndUndefeatedCivs.filter { it != viewingPlayer }.forEach {
                civTable.add(getCivMiniTable(it)).left()
                if (it.isCityState()) {
                    cityStatesParsed++
                } else {
                    civTable.add(it.calculateScoreBreakdown().values.sum().toInt().toLabel()).left()
                }
                if (!it.isCityState() || cityStatesParsed % 2 == 0) 
                    civTable.row()
            }
        }
        civTable.addSeparator()
        civTable.add("Known and defeated ([${playerKnowsAndDefeatedCivs.size}])".toLabel())
            .pad(5f).colspan(2).row()
        if (playerKnowsAndDefeatedCivs.isNotEmpty()) {
            civTable.addSeparator()
            var cityStatesParsed = 0
            playerKnowsAndDefeatedCivs.forEach {
                civTable.add(getCivMiniTable(it)).left()
                if (it.isCityState()) {
                    cityStatesParsed++
                } else {
                    civTable.add(it.calculateScoreBreakdown().values.sum().toInt().toLabel()).left()
                }
                if (!it.isCityState() || cityStatesParsed % 2 == 0)
                    civTable.row()
            }
        }
        val civTableScrollPane = AutoScrollPane(civTable)
        civTableScrollPane.setOverscroll(false, false)
        return civTableScrollPane
    }

    private class DiplomacyGroup(val viewingPlayer: CivilizationInfo, freeHeight: Float, includeCityStates: Boolean): Group() {
        private fun onCivClicked(civLines: HashMap<String, MutableSet<Actor>>, name: String) {
            // ignore the clicks on "dead" civilizations, and remember the selected one
            val selectedLines = civLines[name] ?: return

            // let's check whether lines of all civs are visible (except selected one)
            var atLeastOneLineVisible = false
            var allAreLinesInvisible = true
            for (lines in civLines.values) {
                // skip the civilization selected by user, and civilizations with no lines
                if (lines == selectedLines || lines.isEmpty()) continue

                val visibility = lines.first().isVisible
                atLeastOneLineVisible = atLeastOneLineVisible || visibility
                allAreLinesInvisible = allAreLinesInvisible && visibility

                // check whether both visible and invisible lines are present
                if (atLeastOneLineVisible && !allAreLinesInvisible) {
                    // invert visibility of the selected civ's lines
                    selectedLines.forEach { it.isVisible = !it.isVisible }
                    return
                }
            }

            if (selectedLines.first().isVisible)
            // invert visibility of all lines except selected one
                civLines.filter { it.key != name }.forEach { it.value.forEach { line -> line.isVisible = !line.isVisible } }
            else
            // it happens only when all are visible except selected one
            // invert visibility of the selected civ's lines
                selectedLines.forEach { it.isVisible = !it.isVisible }
        }


        fun playerKnows(civ: CivilizationInfo) = civ == viewingPlayer ||
                viewingPlayer.diplomacy.containsKey(civ.civName)

        init {
            val relevantCivs = viewingPlayer.gameInfo.civilizations.filter { !it.isBarbarian() && (includeCityStates || !it.isCityState()) }
            val playerKnowsAndUndefeatedCivs = relevantCivs.filter { playerKnows(it) && !it.isDefeated() }
            setSize(freeHeight, freeHeight)
            val civGroups = HashMap<String, Actor>()
            val civLines = HashMap<String, MutableSet<Actor>>()
            for (i in 0..playerKnowsAndUndefeatedCivs.lastIndex) {
                val civ = playerKnowsAndUndefeatedCivs[i]

                val civGroup = ImageGetter.getNationIndicator(civ.nation, 30f)

                val vector = HexMath.getVectorForAngle(2 * Math.PI.toFloat() * i / playerKnowsAndUndefeatedCivs.size)
                civGroup.center(this)
                civGroup.moveBy(vector.x * freeHeight / 2.25f, vector.y * freeHeight / 2.25f)
                civGroup.touchable = Touchable.enabled
                civGroup.onClick {
                    onCivClicked(civLines, civ.civName)
                }

                civGroups[civ.civName] = civGroup
                addActor(civGroup)
            }

            for (civ in relevantCivs.filter { playerKnows(it) && !it.isDefeated() })
                for (diplomacy in civ.diplomacy.values.filter {
                    (it.otherCiv().isMajorCiv() || includeCityStates) && playerKnows(it.otherCiv()) && !it.otherCiv().isDefeated()
                }) {
                    val civGroup = civGroups[civ.civName]!!
                    val otherCivGroup = civGroups[diplomacy.otherCivName]!!

                    if (!civLines.containsKey(civ.civName))
                        civLines[civ.civName] = mutableSetOf()

                    val statusLine = ImageGetter.getLine(civGroup.x + civGroup.width / 2, civGroup.y + civGroup.height / 2,
                        otherCivGroup.x + otherCivGroup.width / 2, otherCivGroup.y + otherCivGroup.height / 2, 2f)

                    statusLine.color = if (diplomacy.diplomaticStatus == DiplomaticStatus.War) Color.RED else Color.GREEN

                    civLines[civ.civName]!!.add(statusLine)

                    addActor(statusLine)
                    statusLine.toBack()
                }
        }
    }
}
