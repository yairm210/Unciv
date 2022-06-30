package com.unciv.ui.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.ui.worldscreen.WorldScreen

class VictoryScreen(val worldScreen: WorldScreen) : PickerScreen() {

    private val gameInfo = worldScreen.gameInfo
    private val playerCivInfo = worldScreen.viewingCiv
    private val enabledVictoryTypes = gameInfo.gameParameters.victoryTypes

    private val contentsTable = Table()

    init {
        val difficultyLabel = ("{Difficulty}: {${gameInfo.difficulty}}").toLabel()
        difficultyLabel.setPosition(10f, stage.height - 10, Align.topLeft)
        stage.addActor(difficultyLabel)

        val tabsTable = Table().apply { defaults().pad(10f) }
        val setMyVictoryButton = "Our status".toTextButton().onClick { setOurVictoryTable() }
        if (!playerCivInfo.isSpectator()) tabsTable.add(setMyVictoryButton)
        val setGlobalVictoryButton = "Global status".toTextButton().onClick { setGlobalVictoryTable() }
        tabsTable.add(setGlobalVictoryButton)

        val rankingLabel = if (UncivGame.Current.settings.useDemographics) "Demographics" else "Rankings"
        val setCivRankingsButton = rankingLabel.toTextButton().onClick { setCivRankingsTable() }
        tabsTable.add(setCivRankingsButton)
        topTable.add(tabsTable)
        topTable.addSeparator()
        topTable.add(contentsTable)

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
    }


    private fun wonOrLost(description: String, victoryType: String?, hasWon: Boolean) {
        val endGameMessage =
            when {
                hasWon && (victoryType == null || victoryType !in gameInfo.ruleSet.victories) -> "Your civilization stands above all others! The exploits of your people shall be remembered until the end of civilization itself!"
                victoryType == null || victoryType !in gameInfo.ruleSet.victories -> "You have been defeated. Your civilization has been overwhelmed by its many foes. But your people do not despair, for they know that one day you shall return - and lead them forward to victory!"
                hasWon -> playerCivInfo.gameInfo.ruleSet.victories[victoryType]!!.victoryString
                else -> playerCivInfo.gameInfo.ruleSet.victories[victoryType]!!.defeatString
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

        contentsTable.clear()
        contentsTable.add(ourVictoryStatusTable)
    }

    private fun getOurVictoryColumn(victory: String): Table {
        val victoryObject = gameInfo.ruleSet.victories[victory]!!
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
        val victoriesToShow = gameInfo.ruleSet.victories.filter { !it.value.hiddenInVictoryScreen && enabledVictoryTypes.contains(it.key) }

        for (victory in victoriesToShow) {
            globalVictoryTable.add(getGlobalVictoryColumn(majorCivs, victory.key))
        }

        contentsTable.clear()
        contentsTable.add(globalVictoryTable)
    }

    private fun getGlobalVictoryColumn(majorCivs: List<CivilizationInfo>, victory: String): Table {
        val victoryColumn = Table().apply { defaults().pad(10f) }

        victoryColumn.add("[$victory] Victory".toLabel()).row()
        victoryColumn.addSeparator()

        for (civ in majorCivs.filter { !it.isDefeated() }.sortedByDescending { it.victoryManager.amountMilestonesCompleted(victory) }) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
            victoryColumn.add(getCivGroup(civ, "\n" + buttonText.tr(), playerCivInfo)).fillX().row()
        }

        for (civ in majorCivs.filter { it.isDefeated() }.sortedByDescending { it.victoryManager.amountMilestonesCompleted(victory) }) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
            victoryColumn.add(getCivGroup(civ, "\n" + buttonText.tr(), playerCivInfo)).fillX().row()
        }

        return victoryColumn
    }

    private fun setCivRankingsTable() {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        contentsTable.clear()

        if (UncivGame.Current.settings.useDemographics) contentsTable.add(buildDemographicsTable(majorCivs))
        else contentsTable.add(buildRankingsTable(majorCivs))
    }

    enum class RankLabels { Rank, Value, Best, Average, Worst}
    private fun buildDemographicsTable(majorCivs: List<CivilizationInfo>): Table {
        val demographicsTable = Table().apply { defaults().pad(5f) }
        buildDemographicsHeaders(demographicsTable)

        for (rankLabel in RankLabels.values())   {
            demographicsTable.row()
            demographicsTable.add(rankLabel.name.toLabel())

            for (category in RankingType.values()) {
                val aliveMajorCivsSorted = majorCivs.filter{ it.isAlive() }.sortedByDescending { it.getStatForRanking(category) }

                fun addRankCivGroup(civ: CivilizationInfo) { // local function for reuse of getting and formatting civ stats
                    demographicsTable.add(getCivGroup(civ, ": " + civ.getStatForRanking(category).toString(), playerCivInfo)).fillX()
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

    private fun buildRankingsTable(majorCivs: List<CivilizationInfo>): Table {
        val rankingsTable = Table().apply { defaults().pad(5f) }

        for (category in RankingType.values()) {
            val column = Table().apply { defaults().pad(5f) }
            val textAndIcon = Table().apply { defaults() }
            val columnImage = category.getImage()
            if (columnImage != null) textAndIcon.add(columnImage).size(Constants.defaultFontSize.toFloat() * 0.75f).padRight(2f).padTop(-2f)
            textAndIcon.add(category.name.replace('_' , ' ').toLabel()).row()
            column.add(textAndIcon)
            column.addSeparator()

            for (civ in majorCivs.sortedByDescending { it.getStatForRanking(category) }) {
                column.add(getCivGroup(civ, ": " + civ.getStatForRanking(category).toString(), playerCivInfo)).fillX().row()
            }

            rankingsTable.add(column)
        }

        return rankingsTable
    }

    private fun getCivGroup(civ: CivilizationInfo, afterCivNameText: String, currentPlayer: CivilizationInfo): Table {
        val civGroup = Table()

        var labelText = "{${civ.civName.tr()}}{${afterCivNameText.tr()}}"
        var labelColor = Color.WHITE
        val backgroundColor: Color

        if (civ.isDefeated()) {
            civGroup.add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
            backgroundColor = Color.LIGHT_GRAY
            labelColor = Color.BLACK
        } else if (currentPlayer == civ // || game.viewEntireMapForDebug
            || currentPlayer.knows(civ)
            || currentPlayer.isDefeated()
            || currentPlayer.victoryManager.hasWon()
        ) {
            civGroup.add(ImageGetter.getNationIndicator(civ.nation, 30f))
            backgroundColor = civ.nation.getOuterColor()
            labelColor = civ.nation.getInnerColor()
        } else {
            civGroup.add(ImageGetter.getRandomNationIndicator(30f))
            backgroundColor = Color.DARK_GRAY
            labelText = Constants.unknownNationName
        }

        civGroup.background = ImageGetter.getRoundedEdgeRectangle(backgroundColor)
        val label = labelText.toLabel(labelColor)
        label.setAlignment(Align.center)

        civGroup.add(label).padLeft(10f)
        civGroup.pack()
        return civGroup
    }
}
