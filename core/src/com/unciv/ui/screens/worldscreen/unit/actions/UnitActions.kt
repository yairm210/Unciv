package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen

object UnitActions {

    fun getUnitActions(unit: MapUnit): List<UnitAction> {
        return if (unit.showAdditionalActions) getAdditionalActions(unit)
        else getNormalActions(unit)
    }

    /** Returns whether the action was invoked */
    fun invokeUnitAction(unit: MapUnit, unitActionType: UnitActionType): Boolean {
        val unitAction = actionTypeToFunctions[unitActionType]?.invoke(unit, unit.getTile())?.firstOrNull()
            ?: getNormalActions(unit).firstOrNull { it.type == unitActionType }
            ?: getAdditionalActions(unit).firstOrNull { it.type == unitActionType }
        val internalAction = unitAction?.action ?: return false
        internalAction.invoke()
        return true
    }

    private val actionTypeToFunctions = linkedMapOf<UnitActionType, (unit:MapUnit, tile:Tile) -> Iterable<UnitAction>>(
        // Determined by unit uniques
        UnitActionType.Transform to UnitActionsFromUniques::getTransformActions,
        UnitActionType.Paradrop to UnitActionsFromUniques::getParadropActions,
        UnitActionType.AirSweep to UnitActionsFromUniques::getAirSweepActions,
        UnitActionType.SetUp to UnitActionsFromUniques::getSetupActions,
        UnitActionType.FoundCity to UnitActionsFromUniques::getFoundCityActions,
        UnitActionType.ConstructImprovement to UnitActionsFromUniques::getBuildingImprovementsActions,
        UnitActionType.Repair to UnitActionsFromUniques::getRepairActions,
        UnitActionType.HurryResearch to UnitActionsGreatPerson::getHurryResearchActions,
        UnitActionType.HurryWonder to UnitActionsGreatPerson::getHurryWonderActions,
        UnitActionType.HurryBuilding to UnitActionsGreatPerson::getHurryBuildingActions,
        UnitActionType.ConductTradeMission to UnitActionsGreatPerson::getConductTradeMissionActions,
        UnitActionType.FoundReligion to UnitActionsReligion::getFoundReligionActions,
        UnitActionType.EnhanceReligion to UnitActionsReligion::getEnhanceReligionActions
    )

    private fun getNormalActions(unit: MapUnit): List<UnitAction> {
        val tile = unit.getTile()
        val actionList = ArrayList<UnitAction>()

        for (getActionsFunction in actionTypeToFunctions.values)
            actionList.addAll(getActionsFunction(unit, tile))

        // Determined by unit uniques
        UnitActionsFromUniques.addCreateWaterImprovements(unit, actionList)
        actionList += UnitActionsFromUniques.getImprovementConstructionActions(unit, tile)
        UnitActionsReligion.addSpreadReligionActions(unit, actionList)
        UnitActionsReligion.addRemoveHeresyActions(unit, actionList)

        UnitActionsFromUniques.addTriggerUniqueActions(unit, actionList)
        UnitActionsFromUniques.addAddInCapitalAction(unit, actionList, tile)

        // General actions
        addAutomateAction(unit, actionList, true)
        if (unit.isMoving()) {
            actionList += UnitAction(UnitActionType.StopMovement) { unit.action = null }
        }
        if (unit.isExploring())
            actionList += UnitAction(UnitActionType.StopExploration) { unit.action = null }
        if (unit.isAutomated())
            actionList += UnitAction(UnitActionType.StopAutomation) {
                unit.action = null
                unit.automated = false
            }

        addPromoteAction(unit, actionList)
        UnitActionsUpgrade.addUnitUpgradeAction(unit, actionList)
        UnitActionsPillage.addPillageAction(unit, actionList)
        addSleepActions(actionList, unit, false)
        addFortifyActions(actionList, unit, false)


        if (unit.isMilitary()) addExplorationActions(unit, actionList)

        addWaitAction(unit, actionList)

        addToggleActionsAction(unit, actionList)

        return actionList
    }

    private fun getAdditionalActions(unit: MapUnit): List<UnitAction> {
        val tile = unit.getTile()
        val actionList = ArrayList<UnitAction>()

        if (unit.isMoving()) {
            actionList += UnitAction(UnitActionType.ShowUnitDestination) {
                GUI.getMap().setCenterPosition(unit.getMovementDestination().position, true)
            }
        }
        addSleepActions(actionList, unit, true)
        addFortifyActions(actionList, unit, true)
        addAutomateAction(unit, actionList, false)

        addSwapAction(unit, actionList)
        addDisbandAction(actionList, unit)
        addGiftAction(unit, actionList, tile)
        if (unit.isCivilian()) addExplorationActions(unit, actionList)


        addToggleActionsAction(unit, actionList)

        return actionList
    }

    private fun addSwapAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        val worldScreen = GUI.getWorldScreen()
        // Air units cannot swap
        if (unit.baseUnit.movesLikeAirUnits()) return
        // Disable unit swapping if multiple units are selected. It would make little sense.
        // In principle, the unit swapping mode /will/ function with multiselect: it will simply
        // only consider the first selected unit, and ignore the other selections. However, it does
        // have the visual bug that the tile overlays for the eligible swap locations are drawn for
        // /all/ selected units instead of only the first one. This could be fixed, but again,
        // swapping makes little sense for multiselect anyway.
        if (worldScreen.bottomUnitTable.selectedUnits.size > 1) return
        // Only show the swap action if there is at least one possible swap movement
        if (unit.movement.getUnitSwappableTiles().none()) return
        actionList += UnitAction(
            type = UnitActionType.SwapUnits,
            isCurrentAction = worldScreen.bottomUnitTable.selectedUnitIsSwapping,
            action = {
                worldScreen.bottomUnitTable.selectedUnitIsSwapping =
                    !worldScreen.bottomUnitTable.selectedUnitIsSwapping
                worldScreen.shouldUpdate = true
            }
        )
    }

    private fun addDisbandAction(actionList: ArrayList<UnitAction>, unit: MapUnit) {
        val worldScreen = GUI.getWorldScreen()
        actionList += UnitAction(type = UnitActionType.DisbandUnit, action = {
            if (!worldScreen.hasOpenPopups()) {
                val disbandText = if (unit.currentTile.getOwner() == unit.civ)
                    "Disband this unit for [${unit.baseUnit.getDisbandGold(unit.civ)}] gold?".tr()
                else "Do you really want to disband this unit?".tr()
                ConfirmPopup(worldScreen, disbandText, "Disband unit") {
                    unit.disband()
                    unit.civ.updateStatsForNextTurn() // less upkeep!
                    GUI.setUpdateWorldOnNextRender()
                    if (GUI.getSettings().autoUnitCycle)
                        worldScreen.switchToNextUnit()
                }.open()
            }
        }.takeIf { unit.currentMovement > 0 })
    }


    private fun addPromoteAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.isCivilian() || !unit.promotions.canBePromoted()) return
        // promotion does not consume movement points, but is not allowed if a unit has exhausted its movement or has attacked
        actionList += UnitAction(UnitActionType.Promote,
            action = {
                UncivGame.Current.pushScreen(PromotionPickerScreen(unit))
            }.takeIf { unit.currentMovement > 0 && unit.attacksThisTurn == 0 })
    }

    private fun addExplorationActions(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.baseUnit.movesLikeAirUnits()) return
        if (unit.isExploring()) return
        actionList += UnitAction(UnitActionType.Explore) {
            unit.action = UnitActionType.Explore.value
            if (unit.currentMovement > 0) UnitAutomation.automatedExplore(unit)
        }
    }


    private fun addFortifyActions(
        actionList: ArrayList<UnitAction>,
        unit: MapUnit,
        showingAdditionalActions: Boolean
    ) {
        if (unit.isFortified() && !showingAdditionalActions) {
            actionList += UnitAction(
                type = if (unit.isActionUntilHealed())
                    UnitActionType.FortifyUntilHealed else
                    UnitActionType.Fortify,
                isCurrentAction = true,
                title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            )
            return
        }

        if (!unit.canFortify()) return
        if (unit.currentMovement == 0f) return

        val isFortified = unit.isFortified()
        val isDamaged = unit.health < 100

        if (isDamaged && !showingAdditionalActions && unit.rankTileForHealing(unit.currentTile) != 0)
            actionList += UnitAction(UnitActionType.FortifyUntilHealed,
                action = { unit.fortifyUntilHealed() }.takeIf { !unit.isFortifyingUntilHealed() })
        else if (isDamaged || !showingAdditionalActions)
            actionList += UnitAction(UnitActionType.Fortify,
                action = { unit.fortify() }.takeIf { !isFortified })
    }

    private fun addSleepActions(
        actionList: ArrayList<UnitAction>,
        unit: MapUnit,
        showingAdditionalActions: Boolean
    ) {
        if (unit.isFortified() || unit.canFortify() || unit.currentMovement == 0f) return
        // If this unit is working on an improvement, it cannot sleep
        if (unit.currentTile.hasImprovementInProgress()
            && unit.canBuildImprovement(unit.currentTile.getTileImprovementInProgress()!!)
        ) return
        val isSleeping = unit.isSleeping()
        val isDamaged = unit.health < 100

        if (isDamaged && !showingAdditionalActions) {
            actionList += UnitAction(UnitActionType.SleepUntilHealed,
                action = { unit.action = UnitActionType.SleepUntilHealed.value }
                    .takeIf { !unit.isSleepingUntilHealed() && unit.canHealInCurrentTile() }
            )
        } else if (isDamaged || !showingAdditionalActions) {
            actionList += UnitAction(UnitActionType.Sleep,
                action = { unit.action = UnitActionType.Sleep.value }.takeIf { !isSleeping }
            )
        }
    }

    private fun addGiftAction(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: Tile) {
        val getGiftAction = getGiftAction(unit, tile)
        if (getGiftAction != null) actionList += getGiftAction
    }

    fun getGiftAction(unit: MapUnit, tile: Tile): UnitAction? {
        val recipient = tile.getOwner()
        // We need to be in another civs territory.
        if (recipient == null || recipient.isCurrentPlayer()) return null

        if (recipient.isCityState()) {
            if (recipient.isAtWarWith(unit.civ)) return null // No gifts to enemy CS
            // City States only take military units (and units specifically allowed by uniques)
            if (!unit.isMilitary()
                && unit.getMatchingUniques(
                    UniqueType.GainInfluenceWithUnitGiftToCityState,
                    checkCivInfoUniques = true
                )
                    .none { unit.matchesFilter(it.params[1]) }
            ) return null
        }
        // If gifting to major civ they need to be friendly
        else if (!tile.isFriendlyTerritory(unit.civ)) return null

        // Transported units can't be gifted
        if (unit.isTransported) return null

        if (unit.currentMovement <= 0)
            return UnitAction(UnitActionType.GiftUnit, action = null)

        val giftAction = {
            if (recipient.isCityState()) {
                for (unique in unit.getMatchingUniques(
                    UniqueType.GainInfluenceWithUnitGiftToCityState,
                    checkCivInfoUniques = true
                )) {
                    if (unit.matchesFilter(unique.params[1])) {
                        recipient.getDiplomacyManager(unit.civ)
                            .addInfluence(unique.params[0].toFloat() - 5f)
                        break
                    }
                }

                recipient.getDiplomacyManager(unit.civ).addInfluence(5f)
            } else recipient.getDiplomacyManager(unit.civ)
                .addModifier(DiplomaticModifiers.GaveUsUnits, 5f)

            if (recipient.isCityState() && unit.isGreatPerson())
                unit.destroy()  // City states don't get GPs
            else
                unit.gift(recipient)
            GUI.setUpdateWorldOnNextRender()
        }

        return UnitAction(UnitActionType.GiftUnit, action = giftAction)
    }

    private fun addAutomateAction(
        unit: MapUnit,
        actionList: ArrayList<UnitAction>,
        showingAdditionalActions: Boolean
    ) {

        // If either of these are true it goes in primary actions, else in additional actions
        if ((unit.hasUnique(UniqueType.AutomationPrimaryAction) || unit.cache.hasUniqueToBuildImprovements) != showingAdditionalActions)
            return

        if (unit.isAutomated()) return

        actionList += UnitAction(UnitActionType.Automate,
            isCurrentAction = unit.isAutomated(),
            action = {
                // Temporary, for compatibility - we want games serialized *moving through old versions* to come out the other end with units still automated
                unit.action = UnitActionType.Automate.value
                unit.automated = true
                UnitAutomation.automateUnitMoves(unit)
            }.takeIf { unit.currentMovement > 0 }
        )
    }

    private fun addWaitAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        actionList += UnitAction(
            type = UnitActionType.Wait,
            action = {
                unit.due = false
                GUI.getWorldScreen().switchToNextUnit()
            }
        )
    }

    private fun addToggleActionsAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        actionList += UnitAction(
            type = if (unit.showAdditionalActions) UnitActionType.HideAdditionalActions
            else UnitActionType.ShowAdditionalActions,
            action = {
                unit.showAdditionalActions = !unit.showAdditionalActions
                GUI.getWorldScreen().bottomUnitTable.update()
            }
        )
    }


}
