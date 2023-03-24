package com.unciv.ui.screens.victoryscreen

import LineChart
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Timer
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.components.CivGroup
import com.unciv.ui.components.YearTextUtil
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreen(val worldScreen: WorldScreen) : PickerScreen() {

    private val gameInfo = worldScreen.gameInfo
    private val playerCivInfo = worldScreen.viewingCiv
    private val enabledVictoryTypes = gameInfo.gameParameters.victoryTypes

    private val headerTable = Table()
    private val contentsTable = Table()

    private var replayTimer : Timer.Task? = null

    init {
        val difficultyLabel = ("{Difficulty}: {${gameInfo.difficulty}}").toLabel()

        val tabsTable = Table().apply { defaults().pad(10f) }
        val setMyVictoryButton = "Our status".toTextButton().onClick { setOurVictoryTable() }
        if (!playerCivInfo.isSpectator()) tabsTable.add(setMyVictoryButton)
        val setGlobalVictoryButton = "Global status".toTextButton().onClick { setGlobalVictoryTable() }
        tabsTable.add(setGlobalVictoryButton)

        val rankingLabel = if (UncivGame.Current.settings.useDemographics) "Demographics" else "Rankings"
        val setCivRankingsButton = rankingLabel.toTextButton().onClick { setCivRankingsTable() }
        tabsTable.add(setCivRankingsButton)

        if (playerCivInfo.isSpectator())
            setGlobalVictoryTable()
        else
            setOurVictoryTable()

        rightSideButton.isVisible = false

        var someoneHasWon = false

        val playerVictoryType = playerCivInfo.victoryManager.getVictoryTypeAchieved()
        if (playerVictoryType != null) {
            someoneHasWon = true
            wonOrLost("You have won a [$playerVictoryType] Victory!", playerVictoryType, true)
        }
        for (civ in gameInfo.civilizations.filter { it.isMajorCiv() && it != playerCivInfo }) {
            val civVictoryType = civ.victoryManager.getVictoryTypeAchieved()
            if (civVictoryType != null) {
                someoneHasWon = true
                wonOrLost("[${civ.civName}] has won a [$civVictoryType] Victory!", civVictoryType, false)
            }
        }

        if (playerCivInfo.isDefeated()) {
            wonOrLost("", null, false)
        } else if (!someoneHasWon) {
            setDefaultCloseAction()
        }

        if (playerCivInfo.isSpectator() || someoneHasWon || playerCivInfo.isDefeated()) {
            val replayLabel = "Replay"
            val replayButton = replayLabel.toTextButton().onClick { setReplayTable() }
            tabsTable.add(replayButton)
        }

        val headerTableRightCell = Table()
        val gameSpeedLabel = "{Game Speed}: {${gameInfo.gameParameters.speed}}".toLabel()
        headerTableRightCell.add(gameSpeedLabel).row()
        if(enabledVictoryTypes.contains("Time")) {
            val maxTurnsLabel = "{Max Turns}: ${gameInfo.gameParameters.maxTurns}".toLabel()
            headerTableRightCell.add(maxTurnsLabel).padTop(5f)
        }

        val leftCell = headerTable.add(difficultyLabel).padLeft(10f).left()
        headerTable.add(tabsTable).expandX().center()
        val rightCell = headerTable.add(headerTableRightCell).padRight(10f).right()
        headerTable.addSeparator()
        headerTable.pack()
        // Make the outer cells the same so that the middle one is properly centered
        if(leftCell.actorWidth > rightCell.actorWidth) rightCell.width(leftCell.actorWidth)
        else leftCell.width(rightCell.actorWidth)

        pickerPane.clearChildren()
        pickerPane.add(headerTable).growX().row()
        pickerPane.add(splitPane).expand().fill()

        topTable.add(contentsTable)
    }

    private fun wonOrLost(description: String, victoryType: String?, hasWon: Boolean) {
        val endGameMessage =
            when {
                hasWon && (victoryType == null || victoryType !in gameInfo.ruleset.victories) -> "Your civilization stands above all others! The exploits of your people shall be remembered until the end of civilization itself!"
                victoryType == null || victoryType !in gameInfo.ruleset.victories -> "You have been defeated. Your civilization has been overwhelmed by its many foes. But your people do not despair, for they know that one day you shall return - and lead them forward to victory!"
                hasWon -> playerCivInfo.gameInfo.ruleset.victories[victoryType]!!.victoryString
                else -> playerCivInfo.gameInfo.ruleset.victories[victoryType]!!.defeatString
            }

        descriptionLabel.setText(description.tr() + "\n" + endGameMessage.tr())

        rightSideButton.setText("Start new game".tr())
        rightSideButton.isVisible = true
        rightSideButton.enable()
        rightSideButton.onClick {
            val newGameSetupInfo = GameSetupInfo(gameInfo)
            newGameSetupInfo.mapParameters.reseed()
            game.pushScreen(NewGameScreen(newGameSetupInfo))
        }

        closeButton.setText("One more turn...!".tr())
        closeButton.onClick {
            gameInfo.oneMoreTurnMode = true
            game.popScreen()
        }
    }

    private fun setOurVictoryTable() {
        val ourVictoryStatusTable = Table()
        ourVictoryStatusTable.defaults().pad(10f)
        val victoriesToShow = gameInfo.getEnabledVictories()

        for (victory in victoriesToShow) {
            ourVictoryStatusTable.add("[${victory.key}] Victory".toLabel())
        }
        ourVictoryStatusTable.row()

        for (victory in victoriesToShow) {
            ourVictoryStatusTable.add(getOurVictoryColumn(victory.key))
        }
        ourVictoryStatusTable.row()

        for (victory in victoriesToShow) {
            ourVictoryStatusTable.add(victory.value.victoryScreenHeader.toLabel())
        }

        resetContent()
        contentsTable.add(ourVictoryStatusTable)
    }

    private fun getOurVictoryColumn(victory: String): Table {
        val victoryObject = gameInfo.ruleset.victories[victory]!!
        val table = Table()
        table.defaults().pad(5f)
        var firstIncomplete = true
        for (milestone in victoryObject.milestoneObjects) {
            val completionStatus =
                when {
                    milestone.hasBeenCompletedBy(playerCivInfo) -> Victory.CompletionStatus.Completed
                    firstIncomplete -> {
                        firstIncomplete = false
                        Victory.CompletionStatus.Partially
                    }
                    else -> Victory.CompletionStatus.Incomplete
                }
            for (button in milestone.getVictoryScreenButtons(completionStatus, playerCivInfo)) {
                table.add(button).row()
            }
        }
        return table
    }

    private fun setGlobalVictoryTable() {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        val globalVictoryTable = Table().apply { defaults().pad(10f) }
        val victoriesToShow = gameInfo.ruleset.victories.filter { !it.value.hiddenInVictoryScreen && enabledVictoryTypes.contains(it.key) }

        for (victory in victoriesToShow) {
            globalVictoryTable.add(getGlobalVictoryColumn(majorCivs, victory.key))
        }

        resetContent()
        contentsTable.add(globalVictoryTable)
    }

    private fun getGlobalVictoryColumn(majorCivs: List<Civilization>, victory: String): Table {
        val victoryColumn = Table().apply { defaults().pad(10f) }

        victoryColumn.add("[$victory] Victory".toLabel()).row()
        victoryColumn.addSeparator()

        for (civ in majorCivs.filter { !it.isDefeated() }.sortedByDescending { it.victoryManager.amountMilestonesCompleted(victory) }) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
            victoryColumn.add(CivGroup(civ, "\n" + buttonText.tr(), playerCivInfo)).fillX().row()
        }

        for (civ in majorCivs.filter { it.isDefeated() }.sortedByDescending { it.victoryManager.amountMilestonesCompleted(victory) }) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
            victoryColumn.add(CivGroup(civ, "\n" + buttonText.tr(), playerCivInfo)).fillX().row()
        }

        return victoryColumn
    }

    private fun setCivRankingsTable() {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        resetContent()

        if (UncivGame.Current.settings.useDemographics) contentsTable.add(buildDemographicsTable(majorCivs))
        else {
            val rankingTypeButtons = Table().apply { defaults().pad(10f) }
            val lineChartHolder = Table()
            for (rankingType in RankingType.values()) {
                rankingTypeButtons.add(rankingType.name.toTextButton().onClick {
                    lineChartHolder.clear()
                    lineChartHolder.add(LineChart(
                        getLineChartData(majorCivs, rankingType),
                        gameInfo.currentPlayerCiv,
                        600f,
                        300f
                    ))
                })
            }
            contentsTable.add(rankingTypeButtons).row()
            contentsTable.add(lineChartHolder)
        }
    }

    fun getLineChartData(
        civilizations: List<Civilization>,
        rankingType: RankingType
    ): Map<Int, Map<Civilization, Int>> {
        val lineChartData = mutableMapOf<Int, MutableMap<Civilization, Int>>()
        civilizations.forEach {
            val statsHistory = it.statsHistory
            statsHistory.forEach { (turn, rankingData) ->
                val value = rankingData[rankingType]
                if (value != null) {
                    lineChartData.getOrPut(turn) { mutableMapOf() }[it] = value
                }
            }
        }
        return lineChartData
    }

    private fun setReplayTable() {
        val replayTable = Table().apply { defaults().pad(10f) }
        val yearLabel = "".toLabel()
        replayTable.add(yearLabel).row()
        val replayMap = ReplayMap(gameInfo.tileMap)
        replayTable.add(replayMap).row()

        var nextTurn = gameInfo.historyStartTurn
        val finalTurn = gameInfo.turns
        resetContent()
        replayTimer = Timer.schedule(
            object : Timer.Task() {
                override fun run() {
                    updateReplayTable(yearLabel, replayMap, nextTurn++)
                }
            }, 0.0f,
            // A game of 600 rounds will take one minute.
            0.1f,
            // End at the last turn.
            finalTurn - nextTurn
        )
        contentsTable.add(replayTable)
    }

    private fun updateReplayTable(yearLabel: Label, replayMap: ReplayMap, turn: Int) {
        val finalTurn = gameInfo.turns
        val year = gameInfo.getYear(turn - finalTurn)
        yearLabel.setText(
            YearTextUtil.toYearText(
                year, gameInfo.currentPlayerCiv.isLongCountDisplay()
            )
        )
        replayMap.update(turn)
    }

    enum class RankLabels { Rank, Value, Best, Average, Worst}
    private fun buildDemographicsTable(majorCivs: List<Civilization>): Table {
        val demographicsTable = Table().apply { defaults().pad(5f) }
        buildDemographicsHeaders(demographicsTable)

        for (rankLabel in RankLabels.values())   {
            demographicsTable.row()
            demographicsTable.add(rankLabel.name.toLabel())

            for (category in RankingType.values()) {
                val aliveMajorCivsSorted = majorCivs.filter{ it.isAlive() }.sortedByDescending { it.getStatForRanking(category) }

                fun addRankCivGroup(civ: Civilization) { // local function for reuse of getting and formatting civ stats
                    demographicsTable.add(CivGroup(civ, ": " + civ.getStatForRanking(category).toString(), playerCivInfo)).fillX()
                }

                @Suppress("NON_EXHAUSTIVE_WHEN") // RankLabels.Demographic treated above
                when (rankLabel) {
                    RankLabels.Rank -> demographicsTable.add((aliveMajorCivsSorted.indexOfFirst { it == worldScreen.viewingCiv } + 1).toLabel())
                    RankLabels.Value -> addRankCivGroup(worldScreen.viewingCiv)
                    RankLabels.Best -> addRankCivGroup(aliveMajorCivsSorted.firstOrNull()!!)
                    RankLabels.Average -> demographicsTable.add((aliveMajorCivsSorted.sumOf { it.getStatForRanking(category) } / aliveMajorCivsSorted.size).toLabel())
                    RankLabels.Worst -> addRankCivGroup(aliveMajorCivsSorted.lastOrNull()!!)
                }
            }
        }

        return demographicsTable
    }

    private fun buildDemographicsHeaders(demographicsTable: Table) {
        val demoLabel = Table().apply { defaults().pad(5f) }

        demoLabel.add("Demographic".toLabel()).row()
        demoLabel.addSeparator().fillX()
        demographicsTable.add(demoLabel)

        for (category in RankingType.values()) {
            val headers = Table().apply { defaults().pad(5f) }
            val textAndIcon = Table().apply { defaults() }
            val columnImage = category.getImage()
            if (columnImage != null) textAndIcon.add(columnImage).center().size(Constants.defaultFontSize.toFloat() * 0.75f).padRight(2f).padTop(-2f)
            textAndIcon.add(category.name.replace('_', ' ').toLabel()).row()
            headers.add(textAndIcon)
            headers.addSeparator()
            demographicsTable.add(headers)
        }
    }

    private fun resetContent() {
        replayTimer?.cancel()
        contentsTable.clear()
    }

    override fun dispose() {
        super.dispose()
        replayTimer?.cancel()
    }
}
