package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.managers.ReligionManager
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.Counter
import com.unciv.models.ruleset.BeliefType
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.pickerscreens.DiplomaticVotePickerScreen
import com.unciv.ui.screens.pickerscreens.PantheonPickerScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.ReligiousBeliefsPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

enum class NextTurnAction(protected val text: String, val color: Color) {
    Default("", Color.BLACK) {
        override val icon get() = null
        override fun isChoice(worldScreen: WorldScreen) = false
    },
    AutoPlay("AutoPlay", Color.WHITE) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.autoPlay.isAutoPlaying()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.autoPlay.stopAutoPlay()
    },
    Working(Constants.working, Color.GRAY) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.isNextTurnUpdateRunning()
    },
    Waiting("Waiting for other players...",Color.GRAY) {
        override fun getText(worldScreen: WorldScreen) =
            if (worldScreen.gameInfo.gameParameters.isOnlineMultiplayer)
                "Waiting for [${worldScreen.gameInfo.currentPlayerCiv}]..."
            else text
        override fun isChoice(worldScreen: WorldScreen) =
            !worldScreen.isPlayersTurn
    },
    PickConstruction("Pick construction", Color.CORAL) {
        override fun isChoice(worldScreen: WorldScreen) =
            getCityWithNoProductionSet(worldScreen) != null
        override fun action(worldScreen: WorldScreen) {
            val city = getCityWithNoProductionSet(worldScreen) ?: return
            worldScreen.game.pushScreen(CityScreen(city))
        }
    },
    PickTech("Pick a tech", Color.SKY) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.shouldOpenTechPicker()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen(
                TechPickerScreen(worldScreen.viewingCiv, null, worldScreen.viewingCiv.tech.freeTechs != 0)
            )
    },
    PickPolicy("Pick a policy", Color.VIOLET) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.policies.shouldShowPolicyPicker()
        override fun action(worldScreen: WorldScreen) {
            worldScreen.game.pushScreen(PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState))
            worldScreen.viewingCiv.policies.shouldOpenPolicyPicker = false
        }
    },
    FoundPantheon("Found Pantheon", Color.valueOf(BeliefType.Pantheon.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.religionManager.run {
                religionState != ReligionState.Pantheon && canFoundOrExpandPantheon()
            }
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen(PantheonPickerScreen(worldScreen.viewingCiv))
    },
    ExpandPantheon("Expand Pantheon", Color.valueOf(BeliefType.Pantheon.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.religionManager.run {
                religionState == ReligionState.Pantheon && canFoundOrExpandPantheon()
            }
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen(PantheonPickerScreen(worldScreen.viewingCiv))
    },
    FoundReligion("Found Religion", Color.valueOf(BeliefType.Founder.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.religionManager.religionState == ReligionState.FoundingReligion
        override fun action(worldScreen: WorldScreen) =
            openReligionPicker(worldScreen, true) { getBeliefsToChooseAtFounding() }
    },
    EnhanceReligion("Enhance a Religion", Color.valueOf(BeliefType.Enhancer.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.religionManager.religionState == ReligionState.EnhancingReligion
        override fun action(worldScreen: WorldScreen) =
            openReligionPicker(worldScreen, false) { getBeliefsToChooseAtEnhancing() }
    },
    ReformReligion("Reform Religion", Color.valueOf(BeliefType.Enhancer.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.religionManager.hasFreeBeliefs()
        override fun action(worldScreen: WorldScreen) =
            openReligionPicker(worldScreen, false) { freeBeliefsAsEnums() }
    },
    WorldCongressVote("Vote for World Leader", Color.MAROON) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.mayVoteForDiplomaticVictory()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen(DiplomaticVotePickerScreen(worldScreen.viewingCiv))
    },
    NextUnit("Next unit", Color.LIGHT_GRAY) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.units.shouldGoToDueUnit()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.switchToNextUnit()
    },
    MoveAutomatedUnits("Move automated units", Color.LIGHT_GRAY) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.isMoveAutomatedUnits()
        override fun action(worldScreen: WorldScreen) =
            moveAutomatedUnits(worldScreen)
    },
    NextTurn("Next turn", Color.WHITE) {
        override fun isChoice(worldScreen: WorldScreen) =
            true  // When none of the others is active..
        override fun action(worldScreen: WorldScreen) =
            worldScreen.confirmedNextTurn()
    },

    ;
    open val icon: String? get() = if (text != "AutoPlay") "NotificationIcons/$name" else "NotificationIcons/Working"
    open fun getText(worldScreen: WorldScreen) = text
    abstract fun isChoice(worldScreen: WorldScreen): Boolean
    open fun action(worldScreen: WorldScreen) {}

    companion object {
        // Readability helpers to allow concise enum instances
        private fun getCityWithNoProductionSet(worldScreen: WorldScreen) =
            worldScreen.viewingCiv.cities
            .firstOrNull {
                !it.isPuppet && it.cityConstructions.currentConstructionFromQueue.isEmpty()
            }

        private fun openReligionPicker(
                worldScreen: WorldScreen,
                pickIconAndName: Boolean,
                getBeliefs: ReligionManager.() -> Counter<BeliefType>
            ) =
            worldScreen.game.pushScreen(
                ReligiousBeliefsPickerScreen(
                    worldScreen.viewingCiv,
                    worldScreen.viewingCiv.religionManager.getBeliefs(),
                    pickIconAndName = pickIconAndName
                )
            )

        private fun WorldScreen.isMoveAutomatedUnits(): Boolean {
            if (game.settings.automatedUnitsMoveOnTurnStart || viewingCiv.hasMovedAutomatedUnits)
                return false
            return viewingCiv.units.getCivUnits()
                .any {
                    it.currentMovement > Constants.minimumMovementEpsilon
                    && (it.isMoving() || it.isAutomated() || it.isExploring())
                }
        }

        private fun moveAutomatedUnits(worldScreen: WorldScreen) {
            // Don't allow double-click of 'n' to spawn 2 processes trying to automate units
            if (!worldScreen.isPlayersTurn) return

            worldScreen.isPlayersTurn = false // Disable state changes
            worldScreen.viewingCiv.hasMovedAutomatedUnits = true
            worldScreen.nextTurnButton.disable()
            Concurrency.run("Move automated units") {
                for (unit in worldScreen.viewingCiv.units.getCivUnits())
                    unit.doAction()
                launchOnGLThread {
                    worldScreen.shouldUpdate = true
                    worldScreen.isPlayersTurn = true //Re-enable state changes
                    worldScreen.nextTurnButton.enable()
                }
            }
        }

        private fun WorldScreen.confirmedNextTurn() {
            fun action() {
                game.settings.addCompletedTutorialTask("Pass a turn")
                nextTurn()
            }
            if (game.settings.confirmNextTurn) {
                ConfirmPopup(this, "Confirm next turn", "Next turn",
                    true, action = ::action).open()
            } else action()
        }
    }
}
