package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.models.metadata.GameParameters
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import yairm210.purity.annotations.Readonly

class NextTurnProgress(
    // nullable so we can free the reference once the ProgressBar is shown
    private var nextTurnButton: NextTurnButton?
) : Table() {
    companion object {
        /** Background tint will be color of the right (shrinking) part of the bar - UI mods can override this */
        private val defaultRightColor = Color(0x600000ff)
        /** Minimum Height of the bar if the moddable background has no minHeight */
        private const val defaultBarHeight = 4f
        /** Distance from bottom of NextTurnButton */
        private const val barYPos = 1f
        /** Bar width is NextTurnButton.width minus background ninepatch's declared outer widths minus this */
        private const val removeHorizontalPad = 25f
        /** Speed of fading the bar in when it starts being rendered */
        private const val fadeInDuration = 1f
    }

    private var progress = -1
    private var progressMax = 0
    private var isDirty = false
    private var barWidth = 0f

    // Since we do UI update coroutine-decoupled there's a potential race conditon where the worldScreen
    // gets replaced, and a pending update comes too late. To prevent re-showing when it's outdated we
    // keep a hash in lieu of a weak reference - a normal reference might keep an outdated WorldScreen from
    // being garbage collected, and java.lang.ref.WeakReference.refersTo requires a language level 16 opt-in.
    private var worldScreenHash = 0

    init {
        background = BaseScreen.skinStrings.getUiBackground("WorldScreen/NextTurn/ProgressBar", tintColor = defaultRightColor)
        val leftColor = BaseScreen.skinStrings.getUIColor("WorldScreen/NextTurn/ProgressColor", Color.FOREST)
        add(ImageGetter.getDot(leftColor))  // active bar part
        add()  // Empty cell for the remainder portion of the bar
    }

    fun start(worldScreen: WorldScreen) {
        progress = 0
        val game = worldScreen.gameInfo
        worldScreenHash = worldScreen.hashCode()

        @Readonly fun GameParameters.isRandomNumberOfCivs() = randomNumberOfPlayers || randomNumberOfCityStates
        @Readonly fun GameParameters.minNumberOfCivs() =
            (if (randomNumberOfPlayers) minNumberOfPlayers else players.size) +
            (if (randomNumberOfCityStates) minNumberOfCityStates else numberOfCityStates)

        progressMax = 3 + // one extra step after clone and just before new worldscreen, 1 extra so it's never 100%
            when {
                // Later turns = two steps per city (startTurn and endTurn)
                // Note we ignore cities being founded or destroyed - after turn 0 that proportion
                // should be small, so the bar may clamp at max for a short while;
                // or the new WordScreen starts before it's full. Far simpler code this way.
                game.turns > 0 -> game.getCities().count() * 2
                // If we shouldn't disclose how many civs there are to Mr. Eagle Eye counting steps:
                game.gameParameters.isRandomNumberOfCivs() -> game.gameParameters.minNumberOfCivs()
                // One step per expected city to be founded (they get an endTurn, no startTurn)
                else -> game.civilizations.count { it.isMajorCiv() && it.isAI() || it.isCityState }
            }

        startUpdateProgress()
    }

    fun increment() {
        progress++
        startUpdateProgress()
    }

    private fun startUpdateProgress() {
        isDirty = true
        Concurrency.runOnGLThread {
            updateProgress()
        }
    }

    private fun updateProgress() {
        if (!isDirty) return
        isDirty = false

        val currentWorldScreenHash = GUI.getWorldScreenIfActive()?.hashCode() ?: -1
        if (progressMax == 0 || currentWorldScreenHash != worldScreenHash) {
            remove()
            return
        }

        // On first update the button text is not yet updated. To stabilize geometry, do it now
        if (progress == 0) nextTurnButton?.apply {
            disable()
            if (GUI.getWorldScreenIfActive()?.autoPlay?.isAutoPlaying() == true)
                updateButton(NextTurnAction.AutoPlay)
            else updateButton(NextTurnAction.Working)
            barWidth = width - removeHorizontalPad -
                (background.leftWidth + background.rightWidth)  // "cut off" the rounded parts of the button
            this@NextTurnProgress.setPosition((width - barWidth) / 2, barYPos)
        }

        val cellWidth = barWidth * progress.coerceAtMost(progressMax) / progressMax
        val cellHeight = background.minHeight.coerceAtLeast(defaultBarHeight)
        cells[0].actor.setSize(cellWidth, cellHeight)
        cells[1].width(barWidth - cellWidth)  // Necessary - Table has a quirk so a simple fillX() won't shrink
        setSize(barWidth, defaultBarHeight)

        if (parent == null) {
            color.a = 0f
            nextTurnButton?.addActor(this)
            addAction(Actions.fadeIn(fadeInDuration))  // Also helps hide the jerkiness when many cities are founded on turn 0
            nextTurnButton = null  // Release reference as early as possible
        }
    }
}
