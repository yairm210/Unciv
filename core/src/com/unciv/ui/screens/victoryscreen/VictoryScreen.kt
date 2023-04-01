package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

//TODO someoneHasWon should look at gameInfo.victoryData
//TODO debug access to replay
//TODO more linting -enabledVictories, global: sort once ...
//TODO replay slider
//TODO keys

class VictoryScreen(worldScreen: WorldScreen) : PickerScreen() {

    private val gameInfo = worldScreen.gameInfo
    private val playerCiv = worldScreen.viewingCiv
    private val tabs = TabbedPager(separatorColor = Color.WHITE, shorcutScreen = this)

    internal class CivWithStat(val civ: Civilization, val value: Int) {
        constructor(civ: Civilization, category: RankingType) : this(civ, civ.getStatForRanking(category))
    }

    init {
        //**************** Set up the tabs ****************
        splitPane.setFirstWidget(tabs)

        if (!playerCiv.isSpectator())
            tabs.addPage("Our status", VictoryScreenOurVictory(worldScreen), scrollAlign = Align.topLeft)
        tabs.addPage("Global status", VictoryScreenGlobalVictory(worldScreen), scrollAlign = Align.topLeft)
        if (UncivGame.Current.settings.useDemographics)
            tabs.addPage("Demographics", VictoryScreenDemographics(worldScreen), scrollAlign = Align.topLeft)
        else
            tabs.addPage("Rankings", VictoryScreenCivRankings(worldScreen), scrollAlign = Align.topLeft)
        val showReplay = playerCiv.isSpectator() || gameInfo.victoryData != null || playerCiv.isDefeated()
        if (showReplay)
            tabs.addPage("Replay", VictoryScreenReplay(worldScreen), syncScroll = false)
        tabs.selectPage(0)

        //**************** Set up bottom area - buttons and description label ****************
        rightSideButton.isVisible = false

        var someoneHasWon = false

        val playerVictoryType = playerCiv.victoryManager.getVictoryTypeAchieved()
        if (playerVictoryType != null) {
            someoneHasWon = true
            wonOrLost("You have won a [$playerVictoryType] Victory!", playerVictoryType, true)
        }
        for (civ in gameInfo.civilizations.filter { it.isMajorCiv() && it != playerCiv }) {
            val civVictoryType = civ.victoryManager.getVictoryTypeAchieved()
            if (civVictoryType != null) {
                someoneHasWon = true
                wonOrLost("[${civ.civName}] has won a [$civVictoryType] Victory!", civVictoryType, false)
            }
        }

        if (playerCiv.isDefeated()) {
            wonOrLost("", null, false)
        } else if (!someoneHasWon) {
            setDefaultCloseAction()
        }

        //**************** Set up floating info panels ****************
        tabs.pack()
        val panelY = stage.height - tabs.getRowHeight(0) * 0.5f
        val topRightPanel = VerticalGroup().apply {
            space(5f)
            align(Align.right)
            addActor("{Game Speed}: {${gameInfo.gameParameters.speed}}".toLabel())
            if ("Time" in gameInfo.gameParameters.victoryTypes)
                addActor("{Max Turns}: ${gameInfo.gameParameters.maxTurns}".toLabel())
            pack()
        }
        stage.addActor(topRightPanel)
        topRightPanel.setPosition(stage.width - 10f, panelY, Align.right)

        val difficultyLabel = "{Difficulty}: {${gameInfo.difficulty}}".toLabel()
        stage.addActor(difficultyLabel)
        difficultyLabel.setPosition(10f, panelY, Align.left)
    }

    private fun wonOrLost(description: String, victoryType: String?, hasWon: Boolean) {
        val victory = playerCiv.gameInfo.ruleset.victories[victoryType]
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

    override fun dispose() {
        super.dispose()
        tabs.selectPage(-1)  // Tells Replay page to stop its timer
    }
}
