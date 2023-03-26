package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreen(private val worldScreen: WorldScreen) : PickerScreen() {

    private val gameInfo = worldScreen.gameInfo
    private val playerCivInfo = worldScreen.viewingCiv

    private val headerTable = Table()
    private val contentsTable = Table()

    private var replayTab: VictoryScreenReplay? = null

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

        //TODO the following should look at gameInfo.victoryData
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
        if (gameInfo.gameParameters.victoryTypes.contains("Time")) {
            val maxTurnsLabel = "{Max Turns}: ${gameInfo.gameParameters.maxTurns}".toLabel()
            headerTableRightCell.add(maxTurnsLabel).padTop(5f)
        }

        val leftCell = headerTable.add(difficultyLabel).padLeft(10f).left()
        headerTable.add(tabsTable).expandX().center()
        val rightCell = headerTable.add(headerTableRightCell).padRight(10f).right()
        headerTable.addSeparator()
        headerTable.pack()
        // Make the outer cells the same so that the middle one is properly centered
        if (leftCell.actorWidth > rightCell.actorWidth) rightCell.width(leftCell.actorWidth)
        else leftCell.width(rightCell.actorWidth)

        pickerPane.clearChildren()
        pickerPane.add(headerTable).growX().row()
        pickerPane.add(splitPane).expand().fill()

        topTable.add(contentsTable)
    }

    private fun wonOrLost(description: String, victoryType: String?, hasWon: Boolean) {
        val victory = playerCivInfo.gameInfo.ruleset.victories[victoryType]
            ?: Victory()  // This contains our default victory/defeat texts
        val endGameMessage = when {
                hasWon -> victory.victoryString
                else -> victory.defeatString
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
        resetContent(VictoryScreenOurVictory(worldScreen))
    }

    private fun setGlobalVictoryTable() {
        resetContent(VictoryScreenGlobalVictory(worldScreen))
    }

    private fun setCivRankingsTable() {
        resetContent(VictoryScreenCivRankings(worldScreen))
    }

    private fun setReplayTable() {
        if (replayTab == null) replayTab = VictoryScreenReplay(worldScreen)
        resetContent(replayTab!!)
        replayTab!!.restartTimer()
    }

    private fun resetContent(newContent: Table) {
        replayTab?.resetTimer()
        contentsTable.clear()
        contentsTable.add(newContent)
    }

    override fun dispose() {
        super.dispose()
        replayTab?.resetTimer()
    }

    open class VictoryScreenTab(worldScreen: WorldScreen) : Table(skin) {
        protected val gameInfo = worldScreen.gameInfo
        protected val playerCivInfo = worldScreen.viewingCiv
    }
}
