package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.actions.UnitActionModifiers
import com.unciv.logic.map.mapunit.actions.UnitActionModifiers.getUseFrequency
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen

/**
 *  Actions offered as buttons that don't change the game by themselves, but only open some UI element
 *  (pickers) or switch a UI mode. They are not [UnitActions][com.unciv.logic.map.mapunit.actions.UnitActions].
 */
object UiOnlyUnitActions {

    fun getUnitActions(unit: MapUnit): Sequence<UiUnitAction> = sequenceOf(
        getConstructImprovementAction(unit),
        getConnectRoadAction(unit),
        getPromoteAction(unit),
        getShowUnitDestinationAction(unit),
        getSwapAction(unit)
    ).filterNotNull()

    private fun getPromoteAction(unit: MapUnit): UiUnitAction? {
        if (!unit.promotions.canBePromoted()) return null
        // promotion does not consume movement points, but is not allowed if a unit has exhausted its movement or has attacked
        return UiUnitAction(UiUnitActionType.Promote,
            action = {
                UncivGame.Current.pushScreen { PromotionPickerScreen(GUI.getWorldScreen().selectedGameView.getMapUnitView(unit)) }
            }.takeIf { unit.hasMovement() && unit.attacksThisTurn == 0 }
        )
    }

    private fun getShowUnitDestinationAction(unit: MapUnit): UiUnitAction? {
        if (!unit.isMoving()) return null
        return UiUnitAction(UiUnitActionType.ShowUnitDestination) {
            GUI.getMap().setCenterPosition(unit.getMovementDestination().position, true)
        }
    }

    private fun getSwapAction(unit: MapUnit): UiUnitAction? {
        // Air units cannot swap
        if (unit.baseUnit.isAirUnit()) return null
        // Disable unit swapping if multiple units are selected. It would make little sense.
        // In principle, the unit swapping mode /will/ function with multiselect: it will simply
        // only consider the first selected unit, and ignore the other selections. However, it does
        // have the visual bug that the tile overlays for the eligible swap locations are drawn for
        // /all/ selected units instead of only the first one. This could be fixed, but again,
        // swapping makes little sense for multiselect anyway.
        val worldScreen = GUI.getWorldScreen()
        if (worldScreen.bottomUnitTable.selectedUnits.size > 1) return null
        // Only show the swap action if there is at least one possible swap movement
        if (unit.movement.getUnitSwappableTiles().none()) return null
        return UiUnitAction(
            uiType = UiUnitActionType.SwapUnits,
            isCurrentAction = worldScreen.bottomUnitTable.selectedUnitIsSwapping,
            action = {
                worldScreen.bottomUnitTable.selectedUnitIsSwapping =
                    !worldScreen.bottomUnitTable.selectedUnitIsSwapping
                worldScreen.shouldUpdate = true
            }
        )
    }

    private fun getConnectRoadAction(unit: MapUnit): UiUnitAction? {
        if (!unit.hasUnique(UniqueType.BuildImprovements)) return null
        val unitCivBestRoad = unit.civ.tech.getBestRoadAvailable()
        if (unitCivBestRoad == RoadStatus.None) return null

        val uniquesToCheck = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.BuildImprovements)

        // If a unit has terrainFilter "Land" or improvementFilter "All", then we may proceed.
        // If a unit only had improvement filter "Road" or "Railroad", then we need to also check if that tech is unlocked
        val unique = uniquesToCheck.firstOrNull { it.params[0] == "Land" || it.params[0] in Constants.all
                || (it.params[0] == "Road" && (unitCivBestRoad == RoadStatus.Road || unitCivBestRoad == RoadStatus.Railroad))
                || (it.params[0] == "Railroad" && (unitCivBestRoad == RoadStatus.Railroad))
        } ?: return null
        val useFrequency = getUseFrequency(unit, unique, UiUnitActionType.ConnectRoad.defaultUseFrequency)

        val worldScreen = GUI.getWorldScreen()
        return UiUnitAction(UiUnitActionType.ConnectRoad, useFrequency, // Press once for a multiturn command, it doesn't need to be used that frequently
            isCurrentAction = unit.isAutomatingRoadConnection(),
            action = {
                worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad =
                    !worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad
                worldScreen.shouldUpdate = true
            }
        )
    }

    private fun getConstructImprovementAction(unit: MapUnit): UiUnitAction? {
        if (!unit.cache.hasUniqueToBuildImprovements) return null
        // Conditional uniques (e.g. <when above [0] [Stockpile]>) may no longer apply even though
        // the cache flag was set true - use firstOrNull to avoid NoSuchElementException (#15114)
        val unique = unit.getMatchingUniques(UniqueType.BuildImprovements).firstOrNull()
            ?: return null
        val tile = unit.getTile()

        val couldConstruct = unit.hasMovement()
            && !tile.isCityCenter()
            && unit.civ.gameInfo.ruleset.tileImprovements.values.any {
            ImprovementPickerScreen.canReport(
                tile.improvementFunctions.getImprovementBuildingProblems(
                    it,
                    unit.cache.state
                ).toSet()
            )
                && unit.canBuildImprovement(it)
        }
        val useFrequency = getUseFrequency(unit, unique, UiUnitActionType.ConstructImprovement.defaultUseFrequency)

        return UiUnitAction(UiUnitActionType.ConstructImprovement, useFrequency,
            isCurrentAction = tile.hasImprovementInProgress(),
            action = {
                GUI.pushScreen { ImprovementPickerScreen(tile, unit) {
                    if (GUI.getSettings().autoUnitCycle)
                        GUI.getWorldScreen().switchToNextUnit()
                } }
            }.takeIf { couldConstruct }
        )
    }

    /**
     *  Creates the "paging" actions for:
     *  - [first][Pair.first] - [UiUnitActionType.ShowAdditionalActions] (page forward)
     *  - [second][Pair.second] - [UiUnitActionType.HideAdditionalActions] (page back)
     */
    internal fun getPagingActions(unit: MapUnit, actionsTable: UnitActionsTable): Pair<UiUnitAction, UiUnitAction> =
        UiUnitAction(UiUnitActionType.ShowAdditionalActions) { actionsTable.changePage(1, unit) } to
            UiUnitAction(UiUnitActionType.HideAdditionalActions) { actionsTable.changePage(-1, unit) }
}
