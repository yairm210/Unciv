package com.unciv.logic.map.mapunit.actions

import com.unciv.view.isAirUnit
import com.unciv.view.GameView
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr

/**
 *  Manages creation of [UnitAction] instances.
 *
 *  API used by UI: [getUnitActions] without `unitActionType` parameter (via `UiUnitActions`)
 *  API used by Automation: [invokeUnitAction]
 *  API used by unit tests: [getUnitActions] with `unitActionType` parameter
 *      Note on unit test use: Some UnitAction factories access GUI helpers that crash from a unit test.
 *      Avoid testing actions that need WorldScreen context, and migrate any un-mapped ones you need to `actionTypeToSingleFunctions` or `actionTypeToMultiFunctions`.
 */
object UnitActions {

    /**
     *  Get an instance of [UnitAction] of the [unitActionType] type for [unit] and execute its [action][UnitAction.action], if enabled.
     *
     *  Includes optimization for direct creation of the needed instance type, falls back to enumerating [getUnitActions] to look for the given type.
     *
     *  @param id Selects among several actions of the same type, see [UnitAction.id] - `null` accepts any
     *  @return whether the action was invoked
     */
    fun invokeUnitAction(unit: MapUnit, unitActionType: UnitActionType, id: String? = null): Boolean {
        val internalAction =
            getUnitActions(unit, unitActionType)
            .firstOrNull { it.action != null // available
                    && (id == null || it.id == id) }   // If there's more than one, take the first enabled one.
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
        for (getActionFunction in actionTypeToSingleFunctions.values)
            getActionFunction(unit, tile)?.let { yield(it) }
        for (getActionsFunction in actionTypeToMultiFunctions.values)
            yieldAll(getActionsFunction(unit, tile))

        // Actions not migrated to the actionTypeTo*Functions maps
        addUnmappedUnitActions(unit)
    }

    /**
     *  Get all instances of [UnitAction] of the [unitActionType] type for [unit].
     *
     *  Includes optimization for direct creation of the needed instance type, falls back to enumerating [getUnitActions] to look for the given type.
     */
    fun getUnitActions(unit: MapUnit, unitActionType: UnitActionType): Sequence<UnitAction> {
        val singleFunction = actionTypeToSingleFunctions[unitActionType]
        if (singleFunction != null)                  // we have a mapped getter for a single action...
            return sequenceOf(singleFunction(unit, unit.getTile())).filterNotNull()
        val multiFunction = actionTypeToMultiFunctions[unitActionType]
        if (multiFunction != null)                   // ...or for a collection - call it...
            return multiFunction(unit, unit.getTile())
        return sequence {
            addUnmappedUnitActions(unit)             // No mapped getter: Enumerate all...
        }.filter { it.type == unitActionType }       // ...and take ones matching the type.
    }

    /** Action types that yield at most one action */
    private val actionTypeToSingleFunctions = linkedMapOf<UnitActionType, (unit: MapUnit, tile: Tile) -> UnitAction?>(
        // Determined by unit uniques
        UnitActionType.Paradrop to UnitActionsFromUniques::getParadropAction,
        UnitActionType.AirSweep to UnitActionsFromUniques::getAirSweepAction,
        UnitActionType.SetUp to UnitActionsFromUniques::getSetupAction,
        UnitActionType.Guard to UnitActionsFromUniques::getGuardAction,
        UnitActionType.FoundCity to UnitActionsFromUniques::getFoundCityAction,
        UnitActionType.Repair to { unit, _ -> UnitActionsFromUniques.getRepairAction(unit) },
        UnitActionType.HurryResearch to UnitActionsGreatPerson::getHurryResearchAction,
        UnitActionType.HurryPolicy to UnitActionsGreatPerson::getHurryPolicyAction,
        UnitActionType.HurryWonder to UnitActionsGreatPerson::getHurryWonderAction,
        UnitActionType.HurryBuilding to UnitActionsGreatPerson::getHurryBuildingAction,
        UnitActionType.ConductTradeMission to UnitActionsGreatPerson::getConductTradeMissionAction,
        UnitActionType.FoundReligion to UnitActionsReligion::getFoundReligionAction,
        UnitActionType.EnhanceReligion to UnitActionsReligion::getEnhanceReligionAction,
        UnitActionType.SpreadReligion to UnitActionsReligion::getSpreadReligionAction,
        UnitActionType.RemoveHeresy to UnitActionsReligion::getRemoveHeresyAction,
        UnitActionType.AddInCapital to UnitActionsFromUniques::getAddInCapitalAction,
        UnitActionType.GiftUnit to UnitActions::getGiftAction,
        UnitActionType.Pillage to UnitActionsPillage::getPillageAction
    )

    /** Action types that can yield several actions, e.g. one per matching unique */
    private val actionTypeToMultiFunctions = linkedMapOf<UnitActionType, (unit: MapUnit, tile: Tile) -> Sequence<UnitAction>>(
        UnitActionType.Transform to UnitActionsFromUniques::getTransformActions,
        UnitActionType.CreateImprovement to UnitActionsFromUniques::getImprovementCreationActions,
        UnitActionType.TriggerUnique to UnitActionsFromUniques::getTriggerUniqueActions
    )

    private suspend fun SequenceScope<UnitAction>.addUnmappedUnitActions(unit: MapUnit) {
        val tile = unit.getTile()

        // General actions
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

        yieldAll(UnitActionsUpgrade.getUpgradeActions(unit))

        addSleepActions(unit, tile)
        addFortifyActions(unit)

        addExplorationActions(unit)

        addSkipAction(unit)

        addEscortAction(unit)
    }

    private suspend fun SequenceScope<UnitAction>.addEscortAction(unit: MapUnit) {
        // Air units cannot escort
        if (unit.baseUnit.isAirUnit()) return

        if (unit.getOtherEscortUnit() == null) return
        if (!unit.isEscorting()) {
            yield(UnitAction(
                type = UnitActionType.EscortFormation,
                action = {
                    unit.startEscorting()
                }))
        } else {
            yield(UnitAction(
                type = UnitActionType.StopEscortFormation,
                action = {
                    unit.stopEscorting()
                }))
        }
    }

    private suspend fun SequenceScope<UnitAction>.addExplorationActions(unit: MapUnit) {
        if (unit.baseUnit.isAirUnit()) return
        if (unit.isExploring()) return
        yield(UnitAction(UnitActionType.Explore) {
            unit.action = UnitActionType.Explore.value
            if (unit.hasMovement()) UnitAutomation.automatedExplore(GameView(unit.civ.gameInfo, unit.civ).getMapUnitView(unit))
        })
    }

    private suspend fun SequenceScope<UnitAction>.addFortifyActions(unit: MapUnit) {
        if (unit.isFortified()) {
            yield(UnitAction(
                type = if (unit.isActionUntilHealed())
                    UnitActionType.FortifyUntilHealed else
                    UnitActionType.Fortify,
                isCurrentAction = true,
                title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            ))
            return
        }

        if (!unit.canFortify() || !unit.hasMovement()) return

        yield(UnitAction(UnitActionType.Fortify,
            action = { unit.fortify() }.takeIf { !unit.isFortified() || unit.isFortifyingUntilHealed() },
        ))

        if (unit.health == 100) return
        yield(UnitAction(UnitActionType.FortifyUntilHealed,
            action = { unit.fortifyUntilHealed() }
                .takeIf { !unit.isFortifyingUntilHealed() && unit.canHealInCurrentTile() },
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addSleepActions(unit: MapUnit, tile: Tile) {
        if (unit.isFortified() || unit.canFortify() || unit.isGuarding() || !unit.hasMovement()) return
        if (tile.hasImprovementInProgress() && unit.canBuildImprovement(tile.getTileImprovementInProgress()!!)) return

        yield(UnitAction(UnitActionType.Sleep,
            action = { unit.action = UnitActionType.Sleep.value }.takeIf { !unit.isSleeping() || unit.isSleepingUntilHealed() }
        ))

        if (unit.health == 100) return
        yield(UnitAction(UnitActionType.SleepUntilHealed,
            action = { unit.action = UnitActionType.SleepUntilHealed.value }
                .takeIf { !unit.isSleepingUntilHealed() && unit.canHealInCurrentTile() }
        ))
    }

    private fun getGiftAction(unit: MapUnit, tile: Tile): UnitAction? {
        val recipient = tile.getOwner()
        // We need to be in another civs territory.
        if (recipient == null || recipient.isCurrentPlayer()) return null

        if (recipient.isCityState) {
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

        if (!unit.hasMovement()) {
            return UnitAction(UnitActionType.GiftUnit, action = null)
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
        }
        return UnitAction(UnitActionType.GiftUnit, action = giftAction)
    }

    private suspend fun SequenceScope<UnitAction>.addAutomateActions(unit: MapUnit) {
        if (unit.isAutomated()) return
        yield(UnitAction(UnitActionType.Automate,
            isCurrentAction = unit.isAutomated(),
            action = {
                unit.automated = true
                UnitAutomation.automateUnitMoves(GameView(unit.civ.gameInfo, unit.civ).getMapUnitView(unit))
            }.takeIf { unit.hasMovement() }
        ))
    }

    // Skip one turn: marks a unit as due=false and doesn't cycle back in the queue
    private suspend fun SequenceScope<UnitAction>.addSkipAction(unit: MapUnit) {
        yield(UnitAction(
            type = UnitActionType.Skip,
            action = {
                unit.due = !unit.due
            }.takeIf { unit.hasMovement() },
            isCurrentAction = !unit.due
        ))
    }

}
