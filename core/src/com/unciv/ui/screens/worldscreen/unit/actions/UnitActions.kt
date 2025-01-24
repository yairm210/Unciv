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
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions.getActionDefaultPage
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions.getPagingActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions.getUnitActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions.invokeUnitAction

/**
 *  Manages creation of [UnitAction] instances.
 *
 *  API used by UI: [getUnitActions] without `unitActionType` parameter, [getActionDefaultPage], [getPagingActions]
 *  API used by Automation: [invokeUnitAction]
 *  API used by unit tests: [getUnitActions] with `unitActionType` parameter
 *      Note on unit test use: Some UnitAction factories access GUI helpers that crash from a unit test.
 *      Avoid testing actions that need WorldScreen context, and migrate any un-mapped ones you need to `actionTypeToFunctions`.
 */
object UnitActions {

    /**
     *  Get an instance of [UnitAction] of the [unitActionType] type for [unit] and execute its [action][UnitAction.action], if enabled.
     *
     *  Includes optimization for direct creation of the needed instance type, falls back to enumerating [getUnitActions] to look for the given type.
     *
     *  @return whether the action was invoked
     */
    fun invokeUnitAction(unit: MapUnit, unitActionType: UnitActionType): Boolean {
        val internalAction =
            getUnitActions(unit, unitActionType)
            .firstOrNull { it.action != null }   // If there's more than one, take the first enabled one.
            ?.action ?: return false
        internalAction.invoke()
        return true
    }

    /**
     *  Get all currently possible instances of [UnitAction] for [unit].
     */
    fun getUnitActions(unit: MapUnit) = sequence {
        val tile = unit.getTile()

        // Actions standardized with a directly callable invokeUnitAction
        for (getActionsFunction in actionTypeToFunctions.values)
            yieldAll(getActionsFunction(unit, tile))

        // Actions not migrated to actionTypeToFunctions
        addUnmappedUnitActions(unit)
    }

    /**
     *  Get all instances of [UnitAction] of the [unitActionType] type for [unit].
     *
     *  Includes optimization for direct creation of the needed instance type, falls back to enumerating [getUnitActions] to look for the given type.
     */
    fun getUnitActions(unit: MapUnit, unitActionType: UnitActionType) =
        if (unitActionType in actionTypeToFunctions)
            actionTypeToFunctions[unitActionType]!!  // we have a mapped getter...
                .invoke(unit, unit.getTile())        // ...call it to get a collection...
        else sequence {
            addUnmappedUnitActions(unit)             // No mapped getter: Enumerate all...
        }.filter { it.type == unitActionType }       // ...and take ones matching the type.

    private val actionTypeToFunctions = linkedMapOf<UnitActionType, (unit: MapUnit, tile: Tile) -> Sequence<UnitAction>>(
        // Determined by unit uniques
        UnitActionType.Transform to UnitActionsFromUniques::getTransformActions,
        UnitActionType.Paradrop to UnitActionsFromUniques::getParadropActions,
        UnitActionType.AirSweep to UnitActionsFromUniques::getAirSweepActions,
        UnitActionType.SetUp to UnitActionsFromUniques::getSetupActions,
        UnitActionType.Guard to UnitActionsFromUniques::getGuardActions,
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
        UnitActionType.AddInCapital to UnitActionsFromUniques::getAddInCapitalActions,
        UnitActionType.GiftUnit to UnitActions::getGiftActions
    )

    /** Gets the preferred "page" to display a [UnitAction] of type [unitActionType] on, possibly dynamic depending on the state or situation [unit] is in. */
    fun getActionDefaultPage(unit: MapUnit, unitActionType: UnitActionType) =
        actionTypeToPageGetter[unitActionType]?.invoke(unit) ?: unitActionType.defaultPage

    /** Only for action types that wish to change their "More/Back" page position depending on context.
     *  All others get a defaultPage statically from [UnitActionType].
     *  Note the returned "page numbers" are treated as suggestions, buttons may get redistributed when screen space is scarce.
     */
    private val actionTypeToPageGetter = linkedMapOf<UnitActionType, (unit: MapUnit) -> Int>(
        UnitActionType.Automate to { unit ->
            if (unit.cache.hasUniqueToBuildImprovements || unit.hasUnique(UniqueType.AutomationPrimaryAction)) 0 else 1
        },
        UnitActionType.Fortify to { unit ->
            // Fortify moves to second page if current action is FortifyUntilHealed or if unit is wounded and it's not already the current action
            if (unit.isFortifyingUntilHealed() || unit.health < 100 && !(unit.isFortified() && !unit.isActionUntilHealed())) 1 else 0
        },
        UnitActionType.FortifyUntilHealed to { unit ->
            // FortifyUntilHealed only moves to the second page if Fortify is the current action
            if (unit.isFortified() && !unit.isActionUntilHealed()) 1 else 0
        },
        UnitActionType.Sleep to { unit ->
            // Sleep moves to second page if current action is SleepUntilHealed or if unit is wounded and it's not already the current action
            if (unit.isSleepingUntilHealed() || unit.health < 100 && !(unit.isSleeping() && !unit.isActionUntilHealed())) 1 else 0
        },
        UnitActionType.SleepUntilHealed to { unit ->
            // SleepUntilHealed only moves to the second page if Sleep is the current action
            if (unit.isSleeping() && !unit.isActionUntilHealed()) 1 else 0
        },
        UnitActionType.Explore to { unit ->
            if (unit.isCivilian()) 1 else 0
        },
    )

    private suspend fun SequenceScope<UnitAction>.addUnmappedUnitActions(unit: MapUnit) {
        val tile = unit.getTile()

        // General actions
        addAutomateActions(unit)
        if (unit.isMoving())
            yield(UnitAction(UnitActionType.StopMovement, 20f) { unit.action = null })
        if (unit.isExploring())
            yield(UnitAction(UnitActionType.StopExploration, 20f) { unit.action = null })
        if (unit.isAutomated())
            yield(UnitAction(UnitActionType.StopAutomation, 10f) {
                unit.action = null
                unit.automated = false
            })

        addPromoteActions(unit)
        yieldAll(UnitActionsUpgrade.getUpgradeActions(unit))
        yieldAll(UnitActionsPillage.getPillageActions(unit, tile))

        addSleepActions(unit, tile)
        addFortifyActions(unit)

        addExplorationActions(unit)

        addSkipAction(unit)

        // From here we have actions defaulting to the second page
        if (unit.isMoving()) {
            yield(UnitAction(UnitActionType.ShowUnitDestination, 30f) {
                GUI.getMap().setCenterPosition(unit.getMovementDestination().position, true)
            })
        }
        addEscortAction(unit)
        addSwapAction(unit)
        addDisbandAction(unit)
    }

    private suspend fun SequenceScope<UnitAction>.addEscortAction(unit: MapUnit) {
        // Air units cannot escort
        if (unit.baseUnit.movesLikeAirUnits) return

        val worldScreen = GUI.getWorldScreen()
        val selectedUnits = worldScreen.bottomUnitTable.selectedUnits
        if (selectedUnits.size == 2) {
            // We can still create a formation in the case that we have two units selected
            // and they are on the same tile. We still have to manualy confirm they are on the same tile here.
            val tile = selectedUnits.first().getTile()
            if (selectedUnits.last().getTile() != tile) return
            if (selectedUnits.any { it.baseUnit.movesLikeAirUnits }) return
        } else if (selectedUnits.size != 1) {
            return
        }
        if (unit.getOtherEscortUnit() == null) return
        if (!unit.isEscorting()) {
            yield(UnitAction(
                type = UnitActionType.EscortFormation,
                useFrequency = 50f,
                action = {
                    unit.startEscorting()
                }))
        } else {
            yield(UnitAction(
                type = UnitActionType.StopEscortFormation,
                useFrequency = 50f,
                action = {
                    unit.stopEscorting()
                }))
        }
    }

    private suspend fun SequenceScope<UnitAction>.addSwapAction(unit: MapUnit) {
        // Air units cannot swap
        if (unit.baseUnit.movesLikeAirUnits) return
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
            useFrequency = 60f,
            action = {
                worldScreen.bottomUnitTable.selectedUnitIsSwapping =
                    !worldScreen.bottomUnitTable.selectedUnitIsSwapping
                worldScreen.shouldUpdate = true
            }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addDisbandAction(unit: MapUnit) {
        yield(UnitAction(type = UnitActionType.DisbandUnit,
            useFrequency = 0f, // Only can happen once per unit
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
            }.takeIf { unit.hasMovement() }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addPromoteActions(unit: MapUnit) {
        if (!unit.promotions.canBePromoted()) return
        // promotion does not consume movement points, but is not allowed if a unit has exhausted its movement or has attacked
        yield(UnitAction(UnitActionType.Promote,
            useFrequency = 150f, // We want to show the player that they can promote
            action = {
                UncivGame.Current.pushScreen(PromotionPickerScreen(unit))
            }.takeIf { unit.hasMovement() && unit.attacksThisTurn == 0 }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addExplorationActions(unit: MapUnit) {
        if (unit.baseUnit.movesLikeAirUnits) return
        if (unit.isExploring()) return
        yield(UnitAction(UnitActionType.Explore, 5f) {
            unit.action = UnitActionType.Explore.value
            if (unit.hasMovement()) UnitAutomation.automatedExplore(unit)
        })
    }

    private suspend fun SequenceScope<UnitAction>.addFortifyActions(unit: MapUnit) {
        if (unit.isFortified()) {
            yield(UnitAction(
                type = if (unit.isActionUntilHealed())
                    UnitActionType.FortifyUntilHealed else
                    UnitActionType.Fortify,
                useFrequency = 10f,
                isCurrentAction = true,
                title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            ))
            return
        }

        if (!unit.canFortify() || !unit.hasMovement()) return

        yield(UnitAction(UnitActionType.Fortify,
            action = { unit.fortify() }.takeIf { !unit.isFortified() || unit.isFortifyingUntilHealed() },
            useFrequency = 30f
        ))

        if (unit.health == 100) return
        yield(UnitAction(UnitActionType.FortifyUntilHealed,
            action = { unit.fortifyUntilHealed() }
                .takeIf { !unit.isFortifyingUntilHealed() && unit.canHealInCurrentTile() },
            useFrequency = 45f
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addSleepActions(unit: MapUnit, tile: Tile) {
        if (unit.isFortified() || unit.canFortify() || unit.isGuarding() || !unit.hasMovement()) return
        if (tile.hasImprovementInProgress() && unit.canBuildImprovement(tile.getTileImprovementInProgress()!!)) return

        yield(UnitAction(UnitActionType.Sleep,
            useFrequency = if (!unit.isSleeping()) 29f else 21f,
            action = { unit.action = UnitActionType.Sleep.value }.takeIf { !unit.isSleeping() || unit.isSleepingUntilHealed() }
        ))

        if (unit.health == 100) return
        yield(UnitAction(UnitActionType.SleepUntilHealed,
            useFrequency = if (!unit.isSleepingUntilHealed()) 44f else 20f,
            action = { unit.action = UnitActionType.SleepUntilHealed.value }
                .takeIf { !unit.isSleepingUntilHealed() && unit.canHealInCurrentTile() }
        ))
    }

    private fun getGiftActions(unit: MapUnit, tile: Tile) = sequence {
        val recipient = tile.getOwner()
        // We need to be in another civs territory.
        if (recipient == null || recipient.isCurrentPlayer()) return@sequence

        if (recipient.isCityState) {
            if (recipient.isAtWarWith(unit.civ)) return@sequence // No gifts to enemy CS
            // City States only take military units (and units specifically allowed by uniques)
            if (!unit.isMilitary()
                && unit.getMatchingUniques(
                    UniqueType.GainInfluenceWithUnitGiftToCityState,
                    checkCivInfoUniques = true
                )
                    .none { unit.matchesFilter(it.params[1]) }
            ) return@sequence
        }
        // If gifting to major civ they need to be friendly
        else if (!tile.isFriendlyTerritory(unit.civ)) return@sequence

        // Transported units can't be gifted
        if (unit.isTransported) return@sequence

        if (!unit.hasMovement()) {
            yield(UnitAction(UnitActionType.GiftUnit, 1f, action = null))
            return@sequence
        }

        val giftAction = {
            if (recipient.isCityState) {
                for (unique in unit.getMatchingUniques(
                    UniqueType.GainInfluenceWithUnitGiftToCityState,
                    checkCivInfoUniques = true
                )) {
                    if (unit.matchesFilter(unique.params[1])) {
                        recipient.getDiplomacyManager(unit.civ)!!
                            .addInfluence(unique.params[0].toFloat() - 5f)
                        break
                    }
                }

                recipient.getDiplomacyManager(unit.civ)!!.addInfluence(5f)
            } else recipient.getDiplomacyManager(unit.civ)!!
                .addModifier(DiplomaticModifiers.GaveUsUnits, 5f)

            if (recipient.isCityState && unit.isGreatPerson())
                unit.destroy()  // City states don't get GPs
            else
                unit.gift(recipient)
            GUI.setUpdateWorldOnNextRender()
        }
        yield(UnitAction(UnitActionType.GiftUnit, 5f, action = giftAction))
    }

    private suspend fun SequenceScope<UnitAction>.addAutomateActions(unit: MapUnit) {
        if (unit.isAutomated()) return
        yield(UnitAction(UnitActionType.Automate,
            isCurrentAction = unit.isAutomated(),
            useFrequency = 25f,
            action = {
                unit.automated = true
                UnitAutomation.automateUnitMoves(unit)
            }.takeIf { unit.hasMovement() }
        ))
    }

    // Skip one turn: marks a unit as due=false and doesn't cycle back in the queue
    private suspend fun SequenceScope<UnitAction>.addSkipAction(unit: MapUnit) {
        yield(UnitAction(
            type = UnitActionType.Skip,
            useFrequency = 0f, // Last on first page (defaultPage=0)
            action = {
                // If it's on, skips to next unit due to worldScreen.switchToNextUnit() in activateAction
                // We don't want to switch twice since then we skip units :)
                if (!UncivGame.Current.settings.autoUnitCycle)
                    GUI.getWorldScreen().switchToNextUnit()
                unit.due = false 
            }
        ))
    }

    /**
     *  Creates the "paging" [UnitAction]s for:
     *  - [first][Pair.first] - [UnitActionType.ShowAdditionalActions] (page forward)
     *  - [second][Pair.second] - [UnitActionType.HideAdditionalActions] (page back)
     *
     *  These are not returned as part of [getUnitActions]!
     */
    // This function is here for historic reasons, and to keep UnitActionType implementations closer together.
    // The code might move to UnitActionsTable altogether, no big difference.
    internal fun getPagingActions(unit: MapUnit, actionsTable: UnitActionsTable): Pair<UnitAction, UnitAction> {
        return UnitAction(UnitActionType.ShowAdditionalActions, 0f) { actionsTable.changePage(1, unit) } to
            UnitAction(UnitActionType.HideAdditionalActions, 0f) { actionsTable.changePage(-1, unit) }
    }

}
