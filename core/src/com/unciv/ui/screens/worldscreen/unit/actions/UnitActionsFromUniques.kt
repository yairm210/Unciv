package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.removeConditionals
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.pickerscreens.ImprovementPickerScreen

@Suppress("UNUSED_PARAMETER") // These methods are used as references in UnitActions.actionTypeToFunctions and need identical signature
object UnitActionsFromUniques {

    internal fun getFoundCityActions(unit: MapUnit, tile: Tile) = sequenceOf(getFoundCityAction(unit, tile)).filterNotNull()

    /** Produce a [UnitAction] for founding a city.
     * @param unit The unit to do the founding.
     * @param tile The tile to found a city on.
     * @return null if impossible (the unit lacks the ability to found),
     * or else a [UnitAction] 'defining' the founding.
     * The [action][UnitAction.action] field will be null if the action cannot be done here and now
     * (no movement left, too close to another city).
     */
    internal fun getFoundCityAction(unit: MapUnit, tile: Tile): UnitAction? {
        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.FoundCity)
            .firstOrNull() ?: return null

        if (tile.isWater || tile.isImpassible()) return null
        // Spain should still be able to build Conquistadors in a one city challenge - but can't settle them
        if (unit.civ.isOneCityChallenger() && unit.civ.hasEverOwnedOriginalCapital) return null

        if (unit.currentMovement <= 0 || !tile.canBeSettled())
            return UnitAction(UnitActionType.FoundCity, action = null)

        val hasActionModifiers = unique.conditionals.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }
        val foundAction = {
            if (unit.civ.playerType != PlayerType.AI)
                UncivGame.Current.settings.addCompletedTutorialTask("Found city")
            unit.civ.addCity(tile.position, unit)

            if (hasActionModifiers) UnitActionModifiers.activateSideEffects(unit, unique)
            else unit.destroy()
            GUI.setUpdateWorldOnNextRender() // Set manually, since this could be triggered from the ConfirmPopup and not from the UnitActionsTable
        }

        if (unit.civ.playerType == PlayerType.AI)
            return UnitAction(UnitActionType.FoundCity, action = foundAction)

        val title =
            if (hasActionModifiers) UnitActionModifiers.actionTextWithSideEffects(
                UnitActionType.FoundCity.value,
                unique,
                unit
            )
            else UnitActionType.FoundCity.value

        return UnitAction(
            type = UnitActionType.FoundCity,
            title = title,
            uncivSound = UncivSound.Chimes,
            action = {
                // check if we would be breaking a promise
                val leadersPromisedNotToSettleNear = getLeadersWePromisedNotToSettleNear(unit.civ, tile)
                if (leadersPromisedNotToSettleNear == null)
                    foundAction()
                else {
                    // ask if we would be breaking a promise
                    val text = "Do you want to break your promise to [$leadersPromisedNotToSettleNear]?"
                    ConfirmPopup(
                        GUI.getWorldScreen(),
                        text,
                        "Break promise",
                        action = foundAction
                    ).open(force = true)
                }
            }
        )
    }

    /**
     * Checks whether a civ founding a city on a certain tile would break a promise.
     * @param civInfo The civilization trying to found a city
     * @param tile The tile where the new city would go
     * @return null if no promises broken, else a String listing the leader(s) we would p* off.
     */
    private fun getLeadersWePromisedNotToSettleNear(civInfo: Civilization, tile: Tile): String? {
        val leadersWePromisedNotToSettleNear = HashSet<String>()
        for (otherCiv in civInfo.getKnownCivs().filter { it.isMajorCiv() && !civInfo.isAtWarWith(it) }) {
            val diplomacyManager = otherCiv.getDiplomacyManager(civInfo)
            if (diplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs)) {
                val citiesWithin6Tiles = otherCiv.cities
                    .filter { it.getCenterTile().aerialDistanceTo(tile) <= 6 }
                    .filter { otherCiv.hasExplored(it.getCenterTile()) }
                if (citiesWithin6Tiles.isNotEmpty()) leadersWePromisedNotToSettleNear += otherCiv.getLeaderDisplayName()
            }
        }
        return if(leadersWePromisedNotToSettleNear.isEmpty()) null else leadersWePromisedNotToSettleNear.joinToString(", ")
    }

    internal fun getSetupActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        if (!unit.hasUnique(UniqueType.MustSetUp) || unit.isEmbarked()) return emptySequence()
        val isSetUp = unit.isSetUpForSiege()
        return sequenceOf(UnitAction(UnitActionType.SetUp,
            isCurrentAction = isSetUp,
            action = {
                unit.action = UnitActionType.SetUp.value
                unit.useMovementPoints(1f)
            }.takeIf { unit.currentMovement > 0 && !isSetUp })
        )
    }

    internal fun getParadropActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        val paradropUniques =
            unit.getMatchingUniques(UniqueType.MayParadrop)
        if (!paradropUniques.any() || unit.isEmbarked()) return emptySequence()
        unit.cache.paradropRange = paradropUniques.maxOfOrNull { it.params[0] }!!.toInt()
        return sequenceOf(UnitAction(UnitActionType.Paradrop,
            isCurrentAction = unit.isPreparingParadrop(),
            action = {
                if (unit.isPreparingParadrop()) unit.action = null
                else unit.action = UnitActionType.Paradrop.value
            }.takeIf {
                !unit.hasUnitMovedThisTurn() &&
                        tile.isFriendlyTerritory(unit.civ) &&
                        !tile.isWater
            })
        )
    }

    internal fun getAirSweepActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        val airsweepUniques =
            unit.getMatchingUniques(UniqueType.CanAirsweep)
        if (!airsweepUniques.any()) return emptySequence()
        return sequenceOf(UnitAction(UnitActionType.AirSweep,
            isCurrentAction = unit.isPreparingAirSweep(),
            action = {
                if (unit.isPreparingAirSweep()) unit.action = null
                else unit.action = UnitActionType.AirSweep.value
            }.takeIf {
                unit.canAttack()
            }
        ))
    }

    internal fun getTriggerUniqueActions(unit: MapUnit, tile: Tile) = sequence {
        for (unique in unit.getUniques()) {
            // not a unit action
            if (unique.conditionals.none { it.type?.targetTypes?.contains(UniqueTarget.UnitActionModifier) == true }) continue
            // extends an existing unit action
            if (unique.conditionals.any { it.type == UniqueType.UnitActionExtraLimitedTimes }) continue
            if (!unique.isTriggerable) continue
            if (!unique.conditionalsApply(StateForConditionals(civInfo = unit.civ, unit = unit, tile = unit.currentTile))) continue
            if (!UnitActionModifiers.canUse(unit, unique)) continue

            val baseTitle = when (unique.type) {
                UniqueType.OneTimeEnterGoldenAgeTurns -> {
                    unique.placeholderText.fillPlaceholders(
                        unit.civ.goldenAges.calculateGoldenAgeLength(
                            unique.params[0].toInt()).toString())
                    }
                UniqueType.OneTimeGainStatSpeed -> {
                    val stat = unique.params[1]
                    val modifier = unit.civ.gameInfo.speed.statCostModifiers[Stat.safeValueOf(stat)]
                        ?: unit.civ.gameInfo.speed.modifier
                    UniqueType.OneTimeGainStat.placeholderText.fillPlaceholders(
                        (unique.params[0].toInt() * modifier).toString(), stat
                    )
                }
                else -> unique.text.removeConditionals()
            }
            val title = UnitActionModifiers.actionTextWithSideEffects(baseTitle, unique, unit)

            yield(UnitAction(UnitActionType.TriggerUnique, title) {
                UniqueTriggerActivation.triggerUnique(unique, unit)
                UnitActionModifiers.activateSideEffects(unit, unique)
            })
        }
    }

    internal fun getAddInCapitalActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        if (!unit.hasUnique(UniqueType.AddInCapital)) return emptySequence()
        return sequenceOf(UnitAction(UnitActionType.AddInCapital,
            title = "Add to [${
                unit.getMatchingUniques(UniqueType.AddInCapital).first().params[0]
            }]",
            action = {
                unit.civ.victoryManager.currentsSpaceshipParts.add(unit.name, 1)
                unit.destroy()
            }.takeIf {
                tile.isCityCenter() && tile.getCity()!!
                    .isCapital() && tile.getCity()!!.civ == unit.civ
            }
        ))
    }

    internal fun getImprovementCreationActions(unit: MapUnit, tile: Tile) = sequence {
        val waterImprovementAction = getWaterImprovementAction(unit, tile)
        if (waterImprovementAction != null) yield(waterImprovementAction)
        yieldAll(getImprovementConstructionActionsFromGeneralUnique(unit, tile))
    }

    private fun getWaterImprovementAction(unit: MapUnit, tile: Tile): UnitAction? {
        if (!tile.isWater || !unit.hasUnique(UniqueType.CreateWaterImprovements) || tile.resource == null) return null

        val improvementName = tile.tileResource.getImprovingImprovement(tile, unit.civ) ?: return null
        val improvement = tile.ruleset.tileImprovements[improvementName] ?: return null
        if (!tile.improvementFunctions.canBuildImprovement(improvement, unit.civ)) return null

        return UnitAction(UnitActionType.CreateImprovement, "Create [$improvementName]",
            action = {
                tile.changeImprovement(improvementName, unit.civ, unit)
                unit.destroy()  // Modders may wish for a nondestructive way, but that should be another Unique
            }.takeIf { unit.currentMovement > 0 })
    }

    // Not internal: Used in SpecificUnitAutomation
    fun getImprovementConstructionActionsFromGeneralUnique(unit: MapUnit, tile: Tile) = sequence {
        val uniquesToCheck = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.ConstructImprovementInstantly)

        val civResources = unit.civ.getCivResourcesByName()

        for (unique in uniquesToCheck) {
            val improvementFilter = unique.params[0]
            val improvements = tile.ruleset.tileImprovements.values.filter { it.matchesFilter(improvementFilter) }

            for (improvement in improvements) {
                // Try to skip Improvements we can never build
                // (getImprovementBuildingProblems catches those so the button is always disabled, but it nevertheless looks nicer)
                if (tile.improvementFunctions.getImprovementBuildingProblems(improvement, unit.civ).any { it.permanent })
                    continue

                val resourcesAvailable = improvement.getMatchingUniques(UniqueType.ConsumesResources).none { improvementUnique ->
                        (civResources[improvementUnique.params[1]] ?: 0) < improvementUnique.params[0].toInt()
                }

                yield(UnitAction(UnitActionType.CreateImprovement,
                    title = UnitActionModifiers.actionTextWithSideEffects(
                        "Create [${improvement.name}]",
                        unique,
                        unit
                    ),
                    action = {
                        val unitTile = unit.getTile()
                        unitTile.changeImprovement(improvement.name, unit.civ, unit)

                        unit.civ.cache.updateViewableTiles() // to update 'last seen improvement'

                        UnitActionModifiers.activateSideEffects(unit, unique)
                    }.takeIf {
                        resourcesAvailable
                            && unit.currentMovement > 0f
                            && tile.improvementFunctions.canBuildImprovement(improvement, unit.civ)
                            // Next test is to prevent interfering with UniqueType.CreatesOneImprovement -
                            // not pretty, but users *can* remove the building from the city queue an thus clear this:
                            && !tile.isMarkedForCreatesOneImprovement()
                            && !tile.isImpassible() // Not 100% sure that this check is necessary...
                    }
                ))
            }
        }
    }

    internal fun getConnectRoadActions(unit: MapUnit, tile: Tile) = sequence {
        if (!unit.hasUnique(UniqueType.BuildImprovements)) return@sequence
        val unitCivBestRoad = unit.civ.tech.getBestRoadAvailable()
        if (unitCivBestRoad == RoadStatus.None) return@sequence

        val uniquesToCheck = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.BuildImprovements)

        // If a unit has terrainFilter "Land" or improvementFilter "All", then we may proceed.
        // If a unit only had improvement filter "Road" or "Railroad", then we need to also check if that tech is unlocked
        val unitCanBuildRoad = uniquesToCheck.any { it.params[0] == "Land" || it.params[0] == "All" }
            || uniquesToCheck.any {it.params[0] == "Road" } && (unitCivBestRoad == RoadStatus.Road || unitCivBestRoad == RoadStatus.Railroad)
            || uniquesToCheck.any {it.params[0] == "Railroad"} && (unitCivBestRoad == RoadStatus.Railroad)

        if(!unitCanBuildRoad) return@sequence

        val worldScreen = GUI.getWorldScreen()
        yield(UnitAction(UnitActionType.ConnectRoad,
               isCurrentAction = unit.isAutomatingRoadConnection(),
               action = {
                   worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad =
                       !worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad
                   worldScreen.shouldUpdate = true
               }
           )
        )
    }

    internal fun getTransformActions(unit: MapUnit, tile: Tile) = sequence {
        val unitTile = unit.getTile()
        val civInfo = unit.civ
        val stateForConditionals =
            StateForConditionals(unit = unit, civInfo = civInfo, tile = unitTile)

        for (unique in unit.getMatchingUniques(UniqueType.CanTransform, stateForConditionals)) {
            val unitToTransformTo = civInfo.getEquivalentUnit(unique.params[0])

            if (unitToTransformTo.getMatchingUniques(
                    UniqueType.OnlyAvailable,
                    StateForConditionals.IgnoreConditionals
                )
                    .any { !it.conditionalsApply(stateForConditionals) })
                continue

            // Check _new_ resource requirements
            // Using Counter to aggregate is a bit exaggerated, but - respect the mad modder.
            val resourceRequirementsDelta = Counter<String>()
            for ((resource, amount) in unit.getResourceRequirementsPerTurn())
                resourceRequirementsDelta.add(resource, -amount)
            for ((resource, amount) in unitToTransformTo.getResourceRequirementsPerTurn(StateForConditionals(unit.civ, unit = unit)))
                resourceRequirementsDelta.add(resource, amount)
            val newResourceRequirementsString = resourceRequirementsDelta.entries
                .filter { it.value > 0 }
                .joinToString { "${it.value} {${it.key}}".tr() }

            val title = if (newResourceRequirementsString.isEmpty())
                "Transform to [${unitToTransformTo.name}]"
            else "Transform to [${unitToTransformTo.name}]\n([$newResourceRequirementsString])"

            yield(UnitAction(UnitActionType.Transform,
                title = title,
                action = {
                    unit.destroy()
                    val newUnit =
                        civInfo.units.placeUnitNearTile(unitTile.position, unitToTransformTo)

                    /** We were UNABLE to place the new unit, which means that the unit failed to upgrade!
                     * The only known cause of this currently is "land units upgrading to water units" which fail to be placed.
                     */

                    /** We were UNABLE to place the new unit, which means that the unit failed to upgrade!
                     * The only known cause of this currently is "land units upgrading to water units" which fail to be placed.
                     */
                    if (newUnit == null) {
                        val resurrectedUnit =
                            civInfo.units.placeUnitNearTile(unitTile.position, unit.baseUnit)!!
                        unit.copyStatisticsTo(resurrectedUnit)
                    } else { // Managed to upgrade
                        unit.copyStatisticsTo(newUnit)
                        newUnit.currentMovement = 0f
                    }
                }.takeIf {
                    unit.currentMovement > 0 && !unit.isEmbarked()
                }
            ))
        }
    }

    internal fun getBuildingImprovementsActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        if (!unit.cache.hasUniqueToBuildImprovements) return emptySequence()

        val couldConstruct = unit.currentMovement > 0
            && !tile.isCityCenter()
            && unit.civ.gameInfo.ruleset.tileImprovements.values.any {
            ImprovementPickerScreen.canReport(
                tile.improvementFunctions.getImprovementBuildingProblems(
                    it,
                    unit.civ
                ).toSet()
            )
                && unit.canBuildImprovement(it)
        }

        return sequenceOf(UnitAction(UnitActionType.ConstructImprovement,
            isCurrentAction = tile.hasImprovementInProgress(),
            action = {
                GUI.pushScreen(ImprovementPickerScreen(tile, unit) {
                    if (GUI.getSettings().autoUnitCycle)
                        GUI.getWorldScreen().switchToNextUnit()
                })
            }.takeIf { couldConstruct }
        ))
    }

    internal fun getRepairTurns(unit: MapUnit): Int {
        val tile = unit.currentTile
        if (!tile.isPillaged()) return 0
        if (tile.improvementInProgress == Constants.repair) return tile.turnsToImprovement
        var repairTurns = tile.ruleset.tileImprovements[Constants.repair]!!.getTurnsToBuild(unit.civ, unit)

        val pillagedImprovement = tile.getImprovementToRepair()!!
        val turnsToBuild = pillagedImprovement.getTurnsToBuild(unit.civ, unit)
        // cap repair to number of turns to build original improvement
        if (turnsToBuild < repairTurns) repairTurns = turnsToBuild
        return repairTurns
    }

    internal fun getRepairActions(unit: MapUnit, tile: Tile) = sequenceOf(getRepairAction(unit)).filterNotNull()

    // Public - used in WorkerAutomation
    fun getRepairAction(unit: MapUnit) : UnitAction? {
        if (!unit.currentTile.ruleset.tileImprovements.containsKey(Constants.repair)) return null
        if (!unit.cache.hasUniqueToBuildImprovements) return null
        if (unit.isEmbarked()) return null
        val tile = unit.getTile()
        if (tile.isCityCenter()) return null
        if (!tile.isPillaged()) return null

        val couldConstruct = unit.currentMovement > 0
            && !tile.isCityCenter() && tile.improvementInProgress != Constants.repair

        val turnsToBuild = getRepairTurns(unit)

        return UnitAction(UnitActionType.Repair,
            title = "${UnitActionType.Repair} [${unit.currentTile.getImprovementToRepair()!!.name}] - [${turnsToBuild}${Fonts.turn}]",
            action = {
                tile.turnsToImprovement = getRepairTurns(unit)
                tile.improvementInProgress = Constants.repair
            }.takeIf { couldConstruct }
        )
    }
}
