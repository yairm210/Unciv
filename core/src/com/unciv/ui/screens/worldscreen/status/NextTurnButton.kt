package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.pickerscreens.DiplomaticVotePickerScreen
import com.unciv.ui.screens.pickerscreens.PantheonPickerScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.ReligiousBeliefsPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

class NextTurnButton : IconTextButton("", null, 30) {
    private var nextTurnAction = NextTurnAction.Default

    init {
//         label.setFontSize(30)
        labelCell.pad(10f)
        onActivation { nextTurnAction.action() }
        keyShortcuts.add(KeyboardBinding.NextTurn)
        keyShortcuts.add(KeyboardBinding.NextTurnAlternate)
        // Let unit actions override this for command "Wait".
        keyShortcuts.add(KeyboardBinding.Wait, -99)
    }

    fun update(worldScreen: WorldScreen) {
        nextTurnAction = getNextTurnAction(worldScreen)
        updateButton(nextTurnAction)

        isEnabled = !worldScreen.hasOpenPopups() && worldScreen.isPlayersTurn
                && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning()

        if (isEnabled) addTooltip(KeyboardBinding.NextTurn) else addTooltip("")
    }
    internal fun updateButton(nextTurnAction: NextTurnAction) {
        label.setText(nextTurnAction.text.tr())
        label.color = nextTurnAction.color
        if (nextTurnAction.icon != null && ImageGetter.imageExists(nextTurnAction.icon))
            iconCell.setActor(ImageGetter.getImage(nextTurnAction.icon).apply { setSize(30f) })
        else
            iconCell.clearActor()
        pack()
    }


    private fun getNextTurnAction(worldScreen: WorldScreen): NextTurnAction {
        return when {
            worldScreen.isNextTurnUpdateRunning() ->
                NextTurnAction.Working
            !worldScreen.isPlayersTurn && worldScreen.gameInfo.gameParameters.isOnlineMultiplayer ->
                NextTurnAction("Waiting for [${worldScreen.gameInfo.currentPlayerCiv}]...", Color.GRAY,
                    "NotificationIcons/Waiting") {}
            !worldScreen.isPlayersTurn && !worldScreen.gameInfo.gameParameters.isOnlineMultiplayer ->
                NextTurnAction.Waiting

            worldScreen.viewingCiv.cities.any {
                !it.isPuppet &&
                        it.cityConstructions.currentConstructionFromQueue == ""
            } ->
                NextTurnAction("Pick construction", Color.CORAL,
                    "NotificationIcons/PickConstruction") {
                    val cityWithNoProductionSet = worldScreen.viewingCiv.cities
                        .firstOrNull {
                            !it.isPuppet &&
                                    it.cityConstructions.currentConstructionFromQueue == ""
                        }
                    if (cityWithNoProductionSet != null) worldScreen.game.pushScreen(
                        CityScreen(cityWithNoProductionSet)
                    )
                }

            worldScreen.viewingCiv.shouldOpenTechPicker() ->
                NextTurnAction("Pick a tech", Color.SKY, "NotificationIcons/PickTech") {
                    worldScreen.game.pushScreen(
                        TechPickerScreen(worldScreen.viewingCiv, null, worldScreen.viewingCiv.tech.freeTechs != 0)
                    )
                }

            worldScreen.viewingCiv.policies.shouldOpenPolicyPicker
                    || worldScreen.viewingCiv.policies.freePolicies > 0 && worldScreen.viewingCiv.policies.canAdoptPolicy() ->
                NextTurnAction("Pick a policy", Color.VIOLET, "NotificationIcons/PickPolicy") {
                    worldScreen.game.pushScreen(PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState))
                    worldScreen.viewingCiv.policies.shouldOpenPolicyPicker = false
                }

            worldScreen.viewingCiv.religionManager.canFoundOrExpandPantheon() -> {
                val displayString = if (worldScreen.viewingCiv.religionManager.religionState == ReligionState.Pantheon)
                    "Expand Pantheon"
                else "Found Pantheon"
                NextTurnAction(displayString, Color.valueOf(BeliefType.Pantheon.color),
                    "NotificationIcons/FoundPantheon") {
                    worldScreen.game.pushScreen(PantheonPickerScreen(worldScreen.viewingCiv))
                }
            }

            worldScreen.viewingCiv.religionManager.religionState == ReligionState.FoundingReligion ->
                NextTurnAction("Found Religion", Color.valueOf(BeliefType.Founder.color),
                    "NotificationIcons/FoundReligion") {
                    worldScreen.game.pushScreen(
                        ReligiousBeliefsPickerScreen(
                            worldScreen.viewingCiv,
                            worldScreen.viewingCiv.religionManager.getBeliefsToChooseAtFounding(),
                            pickIconAndName = true,
                            usingFreeBeliefs = false
                        )
                    )
                }

            worldScreen.viewingCiv.religionManager.religionState == ReligionState.EnhancingReligion ->
                NextTurnAction("Enhance a Religion", Color.valueOf(BeliefType.Enhancer.color),
                    "NotificationIcons/EnhanceReligion") {
                    worldScreen.game.pushScreen(
                        ReligiousBeliefsPickerScreen(
                            worldScreen.viewingCiv,
                            worldScreen.viewingCiv.religionManager.getBeliefsToChooseAtEnhancing(),
                            pickIconAndName = false,
                            usingFreeBeliefs = false
                        )
                    )
                }

            worldScreen.viewingCiv.religionManager.hasFreeBeliefs() ->
                NextTurnAction("Reform Religion", Color.valueOf(BeliefType.Enhancer.color),
                    "NotificationIcons/ReformReligion") {
                    worldScreen.game.pushScreen(
                        ReligiousBeliefsPickerScreen(
                            worldScreen.viewingCiv,
                            worldScreen.viewingCiv.religionManager.freeBeliefsAsEnums(),
                            pickIconAndName = false,
                            usingFreeBeliefs = true
                        )
                    )
                }

            worldScreen.viewingCiv.mayVoteForDiplomaticVictory() ->
                NextTurnAction("Vote for World Leader", Color.MAROON,
                    "NotificationIcons/WorldCongressVote") {
                    worldScreen.game.pushScreen(DiplomaticVotePickerScreen(worldScreen.viewingCiv))
                }

            worldScreen.viewingCiv.units.shouldGoToDueUnit() ->
                NextTurnAction("Next unit", Color.LIGHT_GRAY,
                    "NotificationIcons/NextUnit") { worldScreen.switchToNextUnit() }

            !worldScreen.game.settings.automatedUnitsMoveOnTurnStart
                    && !worldScreen.viewingCiv.hasMovedAutomatedUnits
                    && worldScreen.viewingCiv.units.getCivUnits()
                .any { it.currentMovement > Constants.minimumMovementEpsilon && (it.isMoving() || it.isAutomated() || it.isExploring()) } ->
                NextTurnAction("Move automated units", Color.LIGHT_GRAY,
                    "NotificationIcons/MoveAutomatedUnits") {
                    // Don't allow double-click of 'n' to spawn 2 processes trying to automate units
                    if (!worldScreen.isPlayersTurn) return@NextTurnAction

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

            else ->
                NextTurnAction("Next turn", Color.WHITE,
                    "NotificationIcons/NextTurn") {
                    val action = {
                        worldScreen.game.settings.addCompletedTutorialTask("Pass a turn")
                        worldScreen.nextTurn()
                    }
                    if (worldScreen.game.settings.confirmNextTurn) {
                        ConfirmPopup(worldScreen, "Confirm next turn", "Next turn",
                            true, action = action).open()
                    } else action()
                }
        }
    }
}

class NextTurnAction(val text: String, val color: Color, val icon: String? = null, val action: () -> Unit) {
    companion object Prefabs {
        val Default = NextTurnAction("", Color.BLACK) {}
        val Working = NextTurnAction(Constants.working, Color.GRAY, "NotificationIcons/Working") {}
        val Waiting = NextTurnAction("Waiting for other players...",Color.GRAY, "NotificationIcons/Waiting") {}
    }
}
