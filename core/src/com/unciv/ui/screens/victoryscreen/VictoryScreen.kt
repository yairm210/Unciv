package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Victory
import com.unciv.models.translations.tr
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.areSecretKeysPressed
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

//TODO someoneHasWon should look at gameInfo.victoryData

class VictoryScreen(
    private val worldScreen: WorldScreen,
    pageNumber: Int = 0
) : PickerScreen(), RecreateOnResize {

    private val gameInfo = worldScreen.gameInfo
    private val playerCiv = worldScreen.viewingCiv
    private val tabs = TabbedPager(separatorColor = Color.WHITE, shortcutScreen = this)

    internal class CivWithStat(val civ: Civilization, val value: Int) {
        constructor(civ: Civilization, category: RankingType) : this(civ, civ.getStatForRanking(category))
    }

    private enum class VictoryTabs(
        val key: Char,
        val iconName: String = "",
        val caption: String? = null,
        val allowAsSecret: Boolean = false
    ) {
        OurStatus('O', "StatIcons/Specialist", caption = "Our status") {
            override fun getContent(worldScreen: WorldScreen) = VictoryScreenOurVictory(worldScreen)
            override fun isHidden(playerCiv: Civilization) = playerCiv.isSpectator()
        },
        Global('G', "OtherIcons/Nations", caption = "Global status") {
            override fun getContent(worldScreen: WorldScreen) = VictoryScreenGlobalVictory(worldScreen)
        },
        Demographics('D', "CityStateIcons/Cultured", allowAsSecret = true) {
            override fun getContent(worldScreen: WorldScreen) = VictoryScreenDemographics(worldScreen)
            override fun isHidden(playerCiv: Civilization) = !UncivGame.Current.settings.useDemographics
        },
        Rankings('R', "CityStateIcons/Cultured", allowAsSecret = true) {
            override fun getContent(worldScreen: WorldScreen) = VictoryScreenCivRankings(worldScreen)
            override fun isHidden(playerCiv: Civilization) = UncivGame.Current.settings.useDemographics
        },
        Charts('C', "OtherIcons/Charts") {
            override fun getContent(worldScreen: WorldScreen) = VictoryScreenCharts(worldScreen)
            override fun isHidden(playerCiv: Civilization) =
                !playerCiv.isSpectator() && playerCiv.statsHistory.size < 2
        },
        Replay('P', "OtherIcons/Load", allowAsSecret = true) {
            override fun getContent(worldScreen: WorldScreen) = VictoryScreenReplay(worldScreen)
            override fun isHidden(playerCiv: Civilization) =
                !playerCiv.isSpectator()
                        && playerCiv.gameInfo.victoryData == null
                        && playerCiv.isAlive()
                        // We show the replay after 50 turns. This is quite an arbitrary number, but
                        // we don't want to leak the starting position right away (assuming we don't
                        // condense the replay map in a similar way to the minimap (ie. it fills
                        // to only the discovered area) and probably before 50 turns not much
                        // interesting would happen anyway in the replay and the slider might feel
                        // weird, too.
                        && playerCiv.gameInfo.turns < 50
        };
        abstract fun getContent(worldScreen: WorldScreen): Table
        open fun isHidden(playerCiv: Civilization) = false
    }

    init {
        //**************** Set up the tabs ****************
        splitPane.setFirstWidget(tabs)
        val iconSize = Constants.headingFontSize.toFloat()

        for (tab in VictoryTabs.values()) {
            val tabHidden = tab.isHidden(playerCiv)
            if (tabHidden && !(tab.allowAsSecret && Gdx.input.areSecretKeysPressed()))
                continue
            val icon = if (tab.iconName.isEmpty()) null else ImageGetter.getImage(tab.iconName)
            tabs.addPage(
                tab.caption ?: tab.name,
                tab.getContent(worldScreen),
                icon, iconSize,
                scrollAlign = Align.topLeft,
                shortcutKey = KeyCharAndCode(tab.key),
                secret = tabHidden && tab.allowAsSecret
            )
        }
        tabs.selectPage(pageNumber)

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
        // When horizontal screen space is scarce so they would overlap, insert
        // them into the scrolling portion of the TabbedPager header instead
        tabs.pack()
        val topRightPanel = VerticalGroup().apply {
            space(5f)
            align(Align.right)
            addActor("{Game Speed}: {${gameInfo.gameParameters.speed}}".toLabel())
            if ("Time" in gameInfo.gameParameters.victoryTypes)
                addActor("{Max Turns}: ${gameInfo.gameParameters.maxTurns}".toLabel())
            pack()
        }
        val difficultyLabel = "{Difficulty}: {${gameInfo.difficulty}}".toLabel()
        val neededSpace = topRightPanel.width.coerceAtLeast(difficultyLabel.width) * 2 + tabs.getHeaderPrefWidth()
        if (neededSpace > stage.width) {
            tabs.decorateHeader(difficultyLabel, true)
            tabs.decorateHeader(topRightPanel, false)
            tabs.headerScroll.fadeScrollBars = false
        } else {
            val panelY = stage.height - tabs.getRowHeight(0) * 0.5f
            stage.addActor(topRightPanel)
            topRightPanel.setPosition(stage.width - 10f, panelY, Align.right)
            stage.addActor(difficultyLabel)
            difficultyLabel.setPosition(10f, panelY, Align.left)
        }
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

    override fun show() {
        super.show()
        tabs.askForPassword(secretHashCode = 2747985)
    }

    override fun dispose() {
        tabs.selectPage(-1)  // Tells Replay page to stop its timer
        super.dispose()
    }

    override fun recreate(): BaseScreen = VictoryScreen(worldScreen, tabs.activePage)
}
