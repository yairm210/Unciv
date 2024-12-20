package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.components.YearTextUtil
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenReplay(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val gameInfo = worldScreen.gameInfo

    private val finalTurn = gameInfo.turns
    private var replayTimer : Timer.Task? = null
    private val header = Table()

    private val yearLabel = "".toLabel()
    private val slider: UncivSlider
    private val replayMap: ReplayMap
    private val playImage = ImageGetter.getImage("OtherIcons/ForwardArrow")
    private val pauseImage = ImageGetter.getImage("OtherIcons/Pause")
    private val playPauseButton = Container(pauseImage)

    init {
        // yearLabel should be OK with 80f, Label("4000 BC").prefWidth is nearly 78f.
        // The 190f is twice (that plus space=15f). The minimum 120f should never happen, just in case.
        val firstTurn = gameInfo.historyStartTurn
        val maxSliderPercent = if (worldScreen.isPortrait()) 0.75f else 0.5f
        val sliderWidth = ((finalTurn - firstTurn) * 15f + 60f)
            .coerceAtMost(worldScreen.stage.width * maxSliderPercent)
            .coerceAtMost(worldScreen.stage.width - 190f)
            .coerceAtLeast(120f)
        slider = UncivSlider(
            "Turn",
            firstTurn.toFloat(), finalTurn.toFloat(), 1f,
            firstTurn.toFloat(),
            sound = UncivSound.Silent,
            tipType = UncivSlider.TipType.None,
            onChange = this::sliderChanged
        )
        replayMap = ReplayMap(
            gameInfo.tileMap,
            worldScreen.viewingCiv,
            worldScreen.stage.width - 50,
            worldScreen.stage.height - 250  // Empiric: `stage.height - pager.contentScroll_field.height` after init is 244.
        )

        playImage.setSize(24f)
        pauseImage.setSize(24f)
        playPauseButton.apply {
            // I decided against `align(Align.left)`: since the button is so much smaller than the year label, perfect symmetry is impossible
            setSize(26f, 26f)
            onClick(::togglePause)
        }
        yearLabel.setAlignment(Align.right)
        yearLabel.onClick {
            updateReplayTable(gameInfo.historyStartTurn)
            restartTimer()
        }

        header.defaults().space(15f).fillX().padTop(15f)
        header.add(yearLabel).minWidth(80f).right()
        header.add(slider).width(sliderWidth)
        header.add(playPauseButton).minWidth(80f)

        add(replayMap).pad(10f)
    }

    private fun togglePause() {
        if (replayTimer == null) restartTimer() else resetTimer()
    }

    private fun restartTimer() {
        replayTimer?.cancel()
        val firstTurn = slider.value.toInt()
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
        playPauseButton.actor = pauseImage
    }

    private fun resetTimer() {
        replayTimer?.cancel()
        replayTimer = null
        playPauseButton.actor = playImage
    }

    private fun sliderChanged(value: Float) {
        resetTimer()
        updateReplayTable(value.toInt())
    }

    private fun updateReplayTable(turn: Int) {
        val year = gameInfo.getYear(turn - finalTurn)
        yearLabel.setText(
            YearTextUtil.toYearText(
                year, gameInfo.currentPlayerCiv.isLongCountDisplay()
            ) + " / " + turn.tr() + Fonts.turn
        )
        slider.value = turn.toFloat()
        replayMap.update(turn)
        if (turn == finalTurn) resetTimer()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        restartTimer()
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        resetTimer()
    }

    override fun getFixedContent() = header
}
