package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.utils.Timer
import com.unciv.ui.components.YearTextUtil
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenReplay(
    worldScreen: WorldScreen
) : VictoryScreen.VictoryScreenTab(worldScreen) {
    private var replayTimer : Timer.Task? = null
    private val yearLabel = "".toLabel()
    private val replayMap = ReplayMap(gameInfo.tileMap)

    init {
        defaults().pad(10f)

        add(yearLabel).row()
        add(replayMap).row()

        // restartTimer() - done later!
    }

    internal fun restartTimer() {
        replayTimer?.cancel()
        val firstTurn = gameInfo.historyStartTurn
        val finalTurn = gameInfo.turns
        replayTimer = Timer.schedule(
            object : Timer.Task() {
                private var nextTurn = firstTurn
                override fun run() {
                    updateReplayTable(nextTurn++)
                }
            }, 0.0f,
            // A game of 600 rounds will take one minute.
            0.1f,
            // End at the last turn.
            finalTurn - firstTurn
        )
    }

    internal fun resetTimer() {
        replayTimer?.cancel()
        replayTimer = null
    }

    private fun updateReplayTable(turn: Int) {
        val finalTurn = gameInfo.turns
        val year = gameInfo.getYear(turn - finalTurn)
        yearLabel.setText(
            YearTextUtil.toYearText(
                year, gameInfo.currentPlayerCiv.isLongCountDisplay()
            )
        )
        replayMap.update(turn)
    }
}
