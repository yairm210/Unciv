package com.unciv.logic.map.mapunit.actions

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
import yairm210.purity.annotations.Readonly

/**
 *  Manages creation of [UnitAction] instances.
 *
 *  API used by UI: [getUnitActions] without `unitActionType` parameter (via `UiUnitActions`)
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
        yieldAll(UnitActionsPillage.getPillageActions(unit, tile))

        addSleepActions(unit, tile)
        addFortifyActions(unit)

        addExplorationActions(unit)

        addSkipAction(unit)

        addEscortAction(unit)
        addDisbandAction(unit)
    }

    private suspend fun SequenceScope<UnitAction>.addEscortAction(unit: MapUnit) {
        // Air units cannot escort
        if (unit.baseUnit.isAirUnit()) return

        val worldScreen = GUI.getWorldScreen()
        val selectedUnits = worldScreen.bottomUnitTable.selectedUnits
        if (selectedUnits.size == 2) {
            // We can still create a formation in the case that we have two units selected
            // and they are on the same tile. We still have to manualy confirm they are on the same tile here.
            val tile = selectedUnits.first().getTile()
            if (selectedUnits.last().getTile() != tile) return
            if (selectedUnits.any { it.getUnit().baseUnit.isAirUnit() }) return
        } else if (selectedUnits.size != 1) {
            return
        }
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
            }.takeIf { unit.hasMovement() }
        ))
    }

    private suspend fun SequenceScope<UnitAction>.addExplorationActions(unit: MapUnit) {
        if (unit.baseUnit.isAirUnit()) return
        if (unit.isExploring()) return
        yield(UnitAction(UnitActionType.Explore) {
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
            yield(UnitAction(UnitActionType.GiftUnit, action = null))
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
        yield(UnitAction(UnitActionType.GiftUnit, action = giftAction))
    }

    private suspend fun SequenceScope<UnitAction>.addAutomateActions(unit: MapUnit) {
        if (unit.isAutomated()) return
        yield(UnitAction(UnitActionType.Automate,
            isCurrentAction = unit.isAutomated(),
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
            action = {
                unit.due = !unit.due
                // If it's on, skips to next unit due to worldScreen.switchToNextUnit() in activateAction
                // We don't want to switch twice since then we skip units :)
                if (!unit.due && !UncivGame.Current.settings.autoUnitCycle)
                    GUI.getWorldScreen().switchToNextUnit()
            }.takeIf { unit.hasMovement() },
            isCurrentAction = !unit.due
        ))
    }

}
