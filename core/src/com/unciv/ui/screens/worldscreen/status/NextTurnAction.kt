package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.civilization.NextTurnAutomation.tryVoteForDiplomaticVictory
import com.unciv.logic.automation.civilization.ReligionAutomation
import com.unciv.logic.automation.unit.EspionageAutomation
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.managers.ReligionManager
import com.unciv.models.Counter
import com.unciv.models.ruleset.BeliefType
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.overviewscreen.EspionageOverviewScreen
import com.unciv.ui.screens.pickerscreens.DiplomaticVotePickerScreen
import com.unciv.ui.screens.pickerscreens.PantheonPickerScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.ReligiousBeliefsPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import yairm210.purity.annotations.Readonly

enum class NextTurnAction(protected val text: String, val color: Color) {
    Default("", ImageGetter.CHARCOAL) {
        override val icon get() = null
        override fun isChoice(worldScreen: WorldScreen) = false
    },
    RetryUpload("Retry Upload", Color.RED) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.failedUpload
        override fun action(worldScreen: WorldScreen) =
            worldScreen.nextTurn()
    },
    AutoPlay("AutoPlay", Color.WHITE) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.autoPlay.isAutoPlaying()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.autoPlay.stopAutoPlay()
        override fun getSubText(worldScreen: WorldScreen): String? {
            return if (worldScreen.game.settings.autoPlay.autoPlayOneUnit)
                getIdleUnitsText(worldScreen)
                else null
        }
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
            worldScreen.game.pushScreen{ CityScreen(worldScreen.selectedGameView.getCityView(city)) }
        }
        
        override fun selectForAutomation(worldScreen: WorldScreen): Boolean {
            var selectedCity = worldScreen.bottomUnitTable.selectedCity?.city
            val constructionCity = getCityWithNoProductionSet(worldScreen)!!
            if (selectedCity == constructionCity) 
                return false
            worldScreen.mapHolder.setCenterPosition(constructionCity.getCenterTile().position, immediately = true, selectUnit = false)
            worldScreen.bottomUnitTable.citySelected(constructionCity)
            return true
        }

        override fun automate(worldScreen: WorldScreen) {
            var selectedCity = worldScreen.bottomUnitTable.selectedCity?.city
            NextTurnAutomation.automateCity(selectedCity!!)
        }
    },
    PickTech("Pick a tech", Color.SKY) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.shouldOpenTechPicker()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen{
                TechPickerScreen(worldScreen.selectedGameView.civView.getCiv(), null)
            }.let {  }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            NextTurnAutomation.chooseTechToResearch(civInfo)
        }
    },
    PickPolicy("Pick a policy", Color.VIOLET) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.shouldShowPolicyPicker()
        override fun action(worldScreen: WorldScreen) {
            worldScreen.game.pushScreen { PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState) }
            worldScreen.selectedGameView.civView.tryDismissPolicyPicker()
        }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            NextTurnAutomation.adoptPolicy(civInfo)
        }
    },
    MoveSpies("Move Spies", Color.WHITE) {
        override fun isChoice(worldScreen: WorldScreen) =
                worldScreen.gameInfo.isEspionageEnabled() && worldScreen.selectedGameView.civView.shouldShowMoveSpies()
        override fun action(worldScreen: WorldScreen) {
            worldScreen.game.pushScreen { EspionageOverviewScreen(worldScreen.selectedCiv, worldScreen) }
            worldScreen.selectedGameView.civView.tryDismissMoveSpies()
        }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            EspionageAutomation(civInfo).automateSpies()
        }
    },
    FoundPantheon("Found Pantheon", Color.valueOf(BeliefType.Pantheon.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.canFoundPantheon()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen { PantheonPickerScreen(worldScreen.selectedGameView.civView.getCiv()) }.let {  }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            ReligionAutomation.choosePantheon(civInfo)
        }
    },
    ExpandPantheon("Expand Pantheon", Color.valueOf(BeliefType.Pantheon.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.canExpandPantheon()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen { PantheonPickerScreen(worldScreen.selectedGameView.civView.getCiv()) }.let {  }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            ReligionAutomation.chooseReligiousBeliefs(civInfo)
        }
    },
    FoundReligion("Found Religion", Color.valueOf(BeliefType.Founder.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.isFoundingReligion()
        override fun action(worldScreen: WorldScreen) =
            openReligionPicker(worldScreen, true) { getBeliefsToChooseAtFounding() }.let {  }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            ReligionAutomation.foundReligion(civInfo)
        }
    },
    EnhanceReligion("Enhance a Religion", Color.valueOf(BeliefType.Enhancer.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.isEnhancingReligion()
        override fun action(worldScreen: WorldScreen) =
            openReligionPicker(worldScreen, false) { getBeliefsToChooseAtEnhancing() }.let {  }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            ReligionAutomation.enhanceReligion(civInfo)
        }
    },
    ReformReligion("Reform Religion", Color.valueOf(BeliefType.Enhancer.color)) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.hasFreeBeliefs()
        override fun action(worldScreen: WorldScreen) =
            openReligionPicker(worldScreen, false) { freeBeliefsAsEnums() }.let {  }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            ReligionAutomation.chooseFreeBeliefs(civInfo)
        }
    },
    WorldCongressVote("Vote for World Leader", Color.MAROON) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.mayVoteForDiplomaticVictory()
        override fun action(worldScreen: WorldScreen) =
            worldScreen.game.pushScreen { DiplomaticVotePickerScreen(worldScreen.selectedGameView.civView.getCiv()) }.let {  }
        override fun automate(worldScreen: WorldScreen) {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            tryVoteForDiplomaticVictory(civInfo)
        }
    },
    NextUnit("Next unit", Color.LIGHT_GRAY) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.game.settings.checkForDueUnits && worldScreen.selectedGameView.civView.dueUnitsCount() > 0
        override fun action(worldScreen: WorldScreen) {
            worldScreen.switchToNextUnit(!worldScreen.game.settings.checkForDueUnitsCycles)
        }
        override fun getSubText(worldScreen: WorldScreen): String? =
            getIdleUnitsText(worldScreen)
        override fun selectForAutomation(worldScreen: WorldScreen): Boolean {
            val civInfo = worldScreen.selectedGameView.civView.getCiv()
            var selectedUnit = worldScreen.bottomUnitTable.selectedUnit?.unit
            if (selectedUnit != null && selectedUnit in civInfo.units.getDueUnits())
                return false
            worldScreen.switchToNextUnit(!worldScreen.game.settings.checkForDueUnitsCycles, immediately = true)!!
            return true
        }
        override fun automate(worldScreen: WorldScreen) {
            UnitAutomation.automateUnitMoves(worldScreen.bottomUnitTable.selectedUnit?.unit!!)
        }
    },
    MoveAutomatedUnits("Move automated units", Color.LIGHT_GRAY) {
        override fun isChoice(worldScreen: WorldScreen) =
            worldScreen.canMoveAutomatedUnits()
        override fun action(worldScreen: WorldScreen) =
            moveAutomatedUnits(worldScreen)
    },
    NextTurn("Next turn", Color.WHITE) {
        override fun isChoice(worldScreen: WorldScreen) =
            true  // When none of the others is active..
        override fun action(worldScreen: WorldScreen) =
            worldScreen.confirmedNextTurn()
        override fun getSubText(worldScreen: WorldScreen): String? =
            getIdleUnitsText(worldScreen)
    },

    ;
    open val icon: String? get() = if (text != "AutoPlay") "NotificationIcons/$name" else "NotificationIcons/Working"
    open fun getText(worldScreen: WorldScreen) = text
    open fun getSubText(worldScreen: WorldScreen): String? = null
    abstract fun isChoice(worldScreen: WorldScreen): Boolean
    open fun action(worldScreen: WorldScreen) {}
    open fun selectForAutomation(worldScreen: WorldScreen): Boolean = false
    open fun automate(worldScreen: WorldScreen) {}

    companion object {
        // Readability helpers to allow concise enum instances
        @Readonly
        private fun getCityWithNoProductionSet(worldScreen: WorldScreen) =
            worldScreen.selectedGameView.civView.getCiv().cities
            .firstOrNull {
                !it.isPuppet && it.cityConstructions. currentConstructionName().isEmpty()
            }

        private fun openReligionPicker(
                worldScreen: WorldScreen,
                pickIconAndName: Boolean,
                getBeliefs: ReligionManager.() -> Counter<BeliefType>
            ) =
            worldScreen.game.pushScreen {
                ReligiousBeliefsPickerScreen(
                    worldScreen.selectedGameView.civView.getCiv(),
                    worldScreen.selectedGameView.civView.getCiv().religionManager.getBeliefs(),
                    pickIconAndName = pickIconAndName
                )
            }

        @Readonly
        private fun WorldScreen.canMoveAutomatedUnits(): Boolean {
            if (selectedGameView.civView.isSpectator()) return false
            if (game.settings.automatedUnitsMoveOnTurnStart) return false
            if (selectedGameView.civView.hasMovedAutomatedUnitsThisTurn()) return false
            return selectedGameView.civView.hasUnitsReadyToAutomate()
        }

        private fun moveAutomatedUnits(worldScreen: WorldScreen) {
            // Don't allow double-click of 'n' to spawn 2 processes trying to automate units
            if (!worldScreen.isPlayersTurn) return

            worldScreen.isPlayersTurn = false // Disable state changes
            worldScreen.selectedGameView.civView.tryMarkMovedAutomatedUnits()
            worldScreen.nextTurnButton.disable()
            Concurrency.run("Move automated units") {
                worldScreen.selectedGameView.civView.tryAutomateAllUnits()
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

        /**
        Show due units in next-unit and next-turn phase, encouraging the player to give order to
        idle units.
        It also serves to inform new players that the NextUnit-Button cycles units. That's easy
        to grasp, because the number doesn't change when repeatedly clicking the button.
        We also show due units on the NextTurn button, so players see due units in case the
        the NextTurn phase is disabled.
        */
        private fun getIdleUnitsText(worldScreen: WorldScreen): String? {
            val count = worldScreen.selectedGameView.civView.dueUnitsCount()
            if (count > 0) {
                return "[$count] units idle"
            }
            return null
        }
    }
}
