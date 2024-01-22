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

/**
 *  Manages creation of [UnitAction] instances.
 *
 *  API for UI: [getUnitActions]
 *  API for Automation: [invokeUnitAction]
 *  API for unit tests: [getGiftAction]
 */
object UnitActions {

    fun getUnitActions(unit: MapUnit): Sequence<UnitAction> {
        return if (unit.showAdditionalActions) getAdditionalActions(unit)
        else getNormalActions(unit)
    }

    /**
     *  Get an instance of [UnitAction] of the [unitActionType] type for [unit] and execute its [action][UnitAction.action], if enabled.
     *
     *  Includes optimization for direct creation of the needed instance type, falls back to enumerating [getUnitActions] to look for the given type.
     *
     *  @return whether the action was invoked
     */
    fun invokeUnitAction(unit: MapUnit, unitActionType: UnitActionType): Boolean {
        val unitAction =
            if (unitActionType in actionTypeToFunctions)
                actionTypeToFunctions[unitActionType]!!  // we have a mapped getter...
                    .invoke(unit, unit.getTile())        // ...call it to get a collection...
                    .firstOrNull { it.action != null }   // ...then take the first enabled one.
            else
                (getNormalActions(unit) + getAdditionalActions(unit))                // No mapped getter: Enumerate all...
                    .firstOrNull { it.type == unitActionType && it.action != null }  // ...and take first enabled one to match the type.
        val internalAction = unitAction?.action ?: return false
        internalAction.invoke()
        return true
    }

    private val actionTypeToFunctions = linkedMapOf<UnitActionType, (unit: MapUnit, tile: Tile) -> Sequence<UnitAction>>(
        // Determined by unit uniques
        UnitActionType.Transform to UnitActionsFromUniques::getTransformActions,
        UnitActionType.Paradrop to UnitActionsFromUniques::getParadropActions,
        UnitActionType.AirSweep to UnitActionsFromUniques::getAirSweepActions,
        UnitActionType.SetUp to UnitActionsFromUniques::getSetupActions,
        UnitActionType.FoundCity to UnitActionsFromUniques::getFoundCityActions,
        UnitActionType.ConstructImprovement to UnitActionsFromUniques::getBuildingImprovementsActions,
        UnitActionType.ConnectRoad to UnitActionsFromUniques::getConnectRoadActions,
        UnitActionType.Repair to UnitActionsFromUniques::getRepairActions,
        UnitActionType.HurryResearch to UnitActionsGreatPerson::getHurryResearchActions,
        UnitActionType.HurryPolicy to UnitActionsGreatPerson::getHurryPolicyActions,
        UnitActionType.HurryWonder to UnitActionsGreatPerson::getHurryWonderActions,
        UnitActionType.HurryBuilding to UnitActionsGreatPerson::getHurryBuildingActions,
        UnitActionType.ConductTradeMission to UnitActionsGreatPerson::getConductTradeMissionActions,
        UnitActionType.FoundReligion to UnitActionsReligion::getFoundReligionActions,
        UnitActionType.EnhanceReligion to UnitActionsReligion::getEnhanceReligionActions,
        UnitActionType.CreateImprovement to UnitActionsFromUniques::getImprovementCreationActions,
        UnitActionType.SpreadReligion to UnitActionsReligion::getSpreadReligionActions,
        UnitActionType.RemoveHeresy to UnitActionsReligion::getRemoveHeresyActions,
        UnitActionType.TriggerUnique to UnitActionsFromUniques::getTriggerUniqueActions,
        UnitActionType.AddInCapital to UnitActionsFromUniques::getAddInCapitalActions
    )

    private fun shouldAutomationBePrimaryAction(unit:MapUnit) = unit.cache.hasUniqueToBuildImprovements || unit.hasUnique(UniqueType.AutomationPrimaryAction)

    private fun getNormalActions(unit: MapUnit) = sequence {
        val tile = unit.getTile()

        // Actions standardized with a directly callable invokeUnitAction
        for (getActionsFunction in actionTypeToFunctions.values)
            yieldAll(getActionsFunction(unit, tile))

        // General actions

        if (shouldAutomationBePrimaryAction(unit))
            addAutomateActions(unit)
        if (unit.isMoving())
            yield(UnitAction(UnitActionType.StopMovement) { unit.action = null })
        if (unit.isExploring())
            yield(UnitAction(UnitActionType.StopExploration) { unit.action = null })
        if (unit.isAutomated())
            yield(UnitAction(UnitActionType.StopAutomation) {
                unit.action = null
                unit.automated = false
            })

        addPromoteActions(unit)
        yieldAll(UnitActionsUpgrade.getUnitUpgradeActions(unit, tile))
        yieldAll(UnitActionsPillage.getPillageActions(unit, tile))

        addSleepActions(unit, tile)
        addSleepUntilHealedActions(unit, tile)

        addFortifyActions(unit, false)

        if (unit.isMilitary())
            addExplorationActions(unit)

        addWaitAction(unit)

        addToggleActionsAction(unit)
    }

    private fun getAdditionalActions(unit: MapUnit) = sequence {
        if (unit.isMoving()) {
            yield(UnitAction(UnitActionType.ShowUnitDestination) {
                GUI.getMap().setCenterPosition(unit.getMovementDestination().position, true)
            })
        }
        addFortifyActions(unit, true)
        if (!shouldAutomationBePrimaryAction(unit))
            addAutomateActions(unit)

        addSwapAction(unit)
        addDisbandAction(unit)
        addGiftAction(unit, unit.getTile())
        if (unit.isCivilian())
            addExplorationActions(unit)

        addToggleActionsAction(unit)
    }

    private suspend fun SequenceScope<UnitAction>.addSwapAction(unit: MapUnit) {
        // Air units cannot swap
        if (unit.baseUnit.movesLikeAirUnits()) return
        // Disable unit swapping if multiple units are selected. It would make little sense.
        // In principle, the unit swapping mode /will/ function with multiselect: it will simply
        // only consider the first selected unit, and ignore the other selections. However, it does
        // have the visual bug that the tile overlays for the eligible swap locations are drawn for
        // /all/ selected units instead of only the first one. This could be fixed, but again,
        // swapping makes little sense for multiselect anyway.
        val worldScreen = GUI.getWorldScreen()
        if (worldScreen.bottomUnitTable.selectedUnits.size > 1) return
        // Only show the swap action if there is at least one possible swap movement
        if (unit.movement.getUnitSwappableTiles().none()) return
        yield(UnitAction(
            type = UnitActionType.SwapUnits,
            isCurrentAction = worldScreen.bottomUnitTable.selectedUnitIsSwapping,
            action = {
                worldScreen.bottomUnitTable.selectedUnitIsSwapping =
                    !worldScreen.bottomUnitTable.selectedUnitIsSwapping
                worldScreen.shouldUpdate = true
            }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addDisbandAction(unit: MapUnit) {
        yield(UnitAction(type = UnitActionType.DisbandUnit,
            action = {
                val worldScreen = GUI.getWorldScreen()
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
            }.takeIf { unit.currentMovement > 0 }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addPromoteActions(unit: MapUnit) {
        if (unit.isCivilian() || !unit.promotions.canBePromoted()) return
        // promotion does not consume movement points, but is not allowed if a unit has exhausted its movement or has attacked
        yield(UnitAction(UnitActionType.Promote,
            action = {
                UncivGame.Current.pushScreen(PromotionPickerScreen(unit))
            }.takeIf { unit.currentMovement > 0 && unit.attacksThisTurn == 0 }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addExplorationActions(unit: MapUnit) {
        if (unit.baseUnit.movesLikeAirUnits()) return
        if (unit.isExploring()) return
        yield(UnitAction(UnitActionType.Explore) {
            unit.action = UnitActionType.Explore.value
            if (unit.currentMovement > 0) UnitAutomation.automatedExplore(unit)
        })
    }


    private suspend fun SequenceScope<UnitAction>.addFortifyActions(unit: MapUnit, showingAdditionalActions: Boolean) {
        if (unit.isFortified() && !showingAdditionalActions) {
            yield(UnitAction(
                type = if (unit.isActionUntilHealed())
                    UnitActionType.FortifyUntilHealed else
                    UnitActionType.Fortify,
                isCurrentAction = true,
                title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            ))
            return
        }

        if (!unit.canFortify()) return
        if (unit.currentMovement == 0f) return

        val isFortified = unit.isFortified()
        val isDamaged = unit.health < 100

        if (isDamaged && !showingAdditionalActions && unit.rankTileForHealing(unit.currentTile) != 0)
            yield(UnitAction(UnitActionType.FortifyUntilHealed,
                action = { unit.fortifyUntilHealed() }.takeIf { !unit.isFortifyingUntilHealed() }
            ))
        else if (isDamaged || !showingAdditionalActions)
            yield(UnitAction(UnitActionType.Fortify,
                action = { unit.fortify() }.takeIf { !isFortified }
            ))
    }

    private fun shouldHaveSleepAction(unit: MapUnit, tile: Tile): Boolean {
        if (unit.isFortified() || unit.canFortify() || unit.currentMovement == 0f) return false
        return !(tile.hasImprovementInProgress()
                && unit.canBuildImprovement(tile.getTileImprovementInProgress()!!))
    }
    private suspend fun SequenceScope<UnitAction>.addSleepActions(unit: MapUnit, tile: Tile) {
        if (!shouldHaveSleepAction(unit, tile)) return
        if (unit.health < 100) return
        yield(UnitAction(UnitActionType.Sleep,
            action = { unit.action = UnitActionType.Sleep.value }.takeIf { !unit.isSleeping() }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addSleepUntilHealedActions(unit: MapUnit, tile: Tile) {
        if (!shouldHaveSleepAction(unit, tile)) return
        if (unit.health == 100) return
        yield(UnitAction(UnitActionType.SleepUntilHealed,
            action = { unit.action = UnitActionType.SleepUntilHealed.value }
                .takeIf { !unit.isSleepingUntilHealed() && unit.canHealInCurrentTile() }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addGiftAction(unit: MapUnit, tile: Tile) {
        val recipient = tile.getOwner()
        // We need to be in another civs territory.
        if (recipient == null || recipient.isCurrentPlayer()) return

        if (recipient.isCityState()) {
            if (recipient.isAtWarWith(unit.civ)) return // No gifts to enemy CS
            // City States only take military units (and units specifically allowed by uniques)
            if (!unit.isMilitary()
                && unit.getMatchingUniques(
                    UniqueType.GainInfluenceWithUnitGiftToCityState,
                    checkCivInfoUniques = true
                )
                    .none { unit.matchesFilter(it.params[1]) }
            ) return
        }
        // If gifting to major civ they need to be friendly
        else if (!tile.isFriendlyTerritory(unit.civ)) return

        // Transported units can't be gifted
        if (unit.isTransported) return

        if (unit.currentMovement <= 0) {
            yield(UnitAction(UnitActionType.GiftUnit, action = null))
            return
        }

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
        yield(UnitAction(UnitActionType.GiftUnit, action = giftAction))
    }

    /** This exists exclusively for the unit tests, which cannot call getUnitActions.filter(type==GiftUnit) because some bad boys call GUI.worldScreen */
    fun getGiftAction(unit: MapUnit, tile: Tile): UnitAction = sequence {
            addGiftAction(unit, tile)
        }.first()

    private suspend fun SequenceScope<UnitAction>.addAutomateActions(unit: MapUnit) {
        if (unit.isAutomated()) return
        yield(UnitAction(UnitActionType.Automate,
            isCurrentAction = unit.isAutomated(),
            action = {
                // Temporary, for compatibility - we want games serialized *moving through old versions* to come out the other end with units still automated
                unit.action = UnitActionType.Automate.value
                unit.automated = true
                UnitAutomation.automateUnitMoves(unit)
            }.takeIf { unit.currentMovement > 0 }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addWaitAction(unit: MapUnit) {
        yield(UnitAction(
            type = UnitActionType.Wait,
            action = {
                unit.due = false
                GUI.getWorldScreen().switchToNextUnit()
            }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addToggleActionsAction(unit: MapUnit) {
        yield(UnitAction(
            type = if (unit.showAdditionalActions) UnitActionType.HideAdditionalActions
            else UnitActionType.ShowAdditionalActions,
            action = {
                unit.showAdditionalActions = !unit.showAdditionalActions
                GUI.getWorldScreen().bottomUnitTable.update()
            }
        ))
    }

}
