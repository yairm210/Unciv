package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.utils.YesNoPopup
import com.unciv.ui.worldscreen.WorldScreen

private const val CAN_BUILD_IMPROVEMENT = "Can build improvement: "

object UnitActions {

    fun getUnitActions(unit: MapUnit, worldScreen: WorldScreen): Sequence<UnitAction> = sequence {
        val tile = unit.getTile()
        val unitTable = worldScreen.bottomUnitTable

        if (unit.action != null && unit.action!!.startsWith("moveTo"))
            yield(UnitAction(
                    type = UnitActionType.StopMovement,
                    action = { unit.action = null }))

        yieldSleepActions(unit, unitTable)
        yieldFortifyActions(unit, unitTable)
        yieldExploreAction(unit)
        yieldPromoteAction(unit)
        yieldUpgradeAction(unit, tile)
        yieldPillageAction(unit, tile)
        yieldSetupAction(unit)
        yieldFoundCityAction(unit, tile)
        yieldWorkerActions(unit, tile, worldScreen, unitTable)
        yieldConstructRoadsAction(tile, unit)
        yieldCreateWaterImprovements(tile, unit)

        yieldAll(getGreatPersonActions(unit, tile))

        yieldDisbandAction(unit, worldScreen)
    }

    private suspend fun SequenceScope<UnitAction>.yieldDisbandAction(unit: MapUnit, worldScreen: WorldScreen) {
        yield(UnitAction(
                type = UnitActionType.DisbandUnit,
                action = getLambdaOrNull(unit.currentMovement > 0) {
                    val disbandText = if (unit.currentTile.getOwner() == unit.civInfo)
                        "Disband this unit for [${unit.baseUnit.getDisbandGold()}] gold?".tr()
                    else "Do you really want to disband this unit?".tr()
                    YesNoPopup(disbandText, { unit.disband(); worldScreen.shouldUpdate = true }).open()
                }))
    }

    private suspend fun SequenceScope<UnitAction>.yieldCreateWaterImprovements(tile: TileInfo,
                                                                               unit: MapUnit) {
        val improvement = tile.getTileResourceOrNull()?.improvement
        if (improvement != null && tile.isWater && tile.improvement == null
                && unit.hasUnique("May create improvements on water resources")
                && unit.civInfo.tech.isResearched(
                        unit.civInfo.gameInfo.ruleSet.tileImprovements[improvement]!!
                                .techRequired!!))
            yield(UnitAction(
                    type = UnitActionType.Create,
                    title = "Create [$improvement]",
                    action = getWaterImprovementAction(unit, tile, improvement)))
    }

    fun getWaterImprovementAction(unit: MapUnit, tile: TileInfo, improvement: String): (() -> Unit)? =
            getLambdaOrNull(unit.currentMovement > 0) {
                tile.improvement = improvement
                unit.destroy()
            }

    private suspend fun SequenceScope<UnitAction>.yieldConstructRoadsAction(tile: TileInfo, unit: MapUnit) {
        if (tile.roadStatus == RoadStatus.None
                && tile.improvementInProgress != "Road"
                && tile.isLand
                && unit.hasUnique("Can construct roads")
                && unit.civInfo.tech.isResearched(RoadStatus.Road.improvement(unit.civInfo.gameInfo.ruleSet)!!.techRequired!!))
            yield(UnitAction(
                    type = UnitActionType.ConstructRoad,
                    action = getLambdaOrNull(unit.currentMovement > 0) {
                        tile.improvementInProgress = "Road"
                        tile.turnsToImprovement = 4
                    }))
    }

    fun getFoundCityAction(unit: MapUnit, tile: TileInfo): (() -> Unit)? =
            getLambdaOrNull(unit.currentMovement > 0
                    && !tile.getTilesInDistance(3).any { it.isCityCenter() }) {
                UncivGame.Current.settings.addCompletedTutorialTask("Found city")
                unit.civInfo.addCity(tile.position)
                tile.improvement = null
                unit.destroy()
            }

    private suspend fun SequenceScope<UnitAction>.yieldFoundCityAction(unit: MapUnit, tile: TileInfo) {
        if (!unit.isEmbarked() && unit.hasUnique("Founds a new city"))
            yield(UnitAction(
                    type = UnitActionType.FoundCity,
                    uncivSound = UncivSound.Chimes,
                    action = getFoundCityAction(unit, tile)))
    }

    private suspend fun SequenceScope<UnitAction>.yieldPromoteAction(unit: MapUnit) {
        // promotion does not consume movement points, so we can do it always
        if (!unit.type.isCivilian() && unit.promotions.canBePromoted())
            yield(UnitAction(
                    type = UnitActionType.Promote,
                    uncivSound = UncivSound.Promote,
                    action = {
                        UncivGame.Current.setScreen(PromotionPickerScreen(unit))
                    }))
    }

    private suspend fun SequenceScope<UnitAction>.yieldSetupAction(unit: MapUnit) {
        if (!unit.isEmbarked() && unit.hasUnique("Must set up to ranged attack")) {
            val isSetUp = unit.action == "Set Up"
            yield(UnitAction(
                    type = UnitActionType.SetUp,
                    isCurrentAction = isSetUp,
                    uncivSound = UncivSound.Setup,
                    action = getLambdaOrNull(unit.currentMovement > 0 && !isSetUp) {
                        unit.action = Constants.unitActionSetUp
                        unit.useMovementPoints(1f)
                    }))
        }
    }

    private suspend fun SequenceScope<UnitAction>.yieldPillageAction(unit: MapUnit, tile: TileInfo) {
        if (!unit.type.isCivilian()) {
            val action = getPillageAction(unit, tile)
            if (action != null)
                yield(UnitAction(
                    type = UnitActionType.Pillage,
                    action = action))
        }
    }

    fun getPillageAction(unit: MapUnit, tile: TileInfo): (() -> Unit)? =
        getLambdaOrNull(unit.currentMovement > 0 && canPillage(unit, tile)) {
            // http://well-of-souls.com/civ/civ5_improvements.html says that naval improvements are destroyed upon pilllage
            //    and I can't find any other sources so I'll go with that
            if (tile.isLand) {
                tile.improvementInProgress = tile.improvement
                tile.turnsToImprovement = 2
            }
            tile.improvement = null
            if (!unit.hasUnique("No movement cost to pillage")) unit.useMovementPoints(1f)
            unit.healBy(25)
        }

    private suspend fun SequenceScope<UnitAction>.yieldExploreAction(unit: MapUnit) {
        if (!unit.type.isAirUnit())
            yield(if (unit.action == Constants.unitActionExplore)
                UnitAction(
                        type = UnitActionType.StopExploration,
                        action = { unit.action = null }
                )
            else
                UnitAction(
                        type = UnitActionType.Explore,
                        action = {
                            UnitAutomation().automatedExplore(unit)
                            unit.action = Constants.unitActionExplore
                        }))
    }

    private suspend fun SequenceScope<UnitAction>.yieldUpgradeAction(unit: MapUnit, tile: TileInfo) {
        if (unit.baseUnit().upgradesTo != null && tile.getOwner() == unit.civInfo
                && unit.canUpgrade()) {

            val goldCostOfUpgrade = unit.getCostOfUpgrade()
            val upgradedUnit = unit.getUnitToUpgradeTo()

            yield(UnitAction(
                    type = UnitActionType.Upgrade,
                    title = "Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)",
                    uncivSound = UncivSound.Upgrade,
                    action = getUpgradeAction(unit, tile, upgradedUnit, goldCostOfUpgrade)))
        }
    }


    fun getUpgradeAction(unit: MapUnit, unitTile: TileInfo, upgradedUnit: BaseUnit,
                         goldCostOfUpgrade: Int): (() -> Unit)? =
            getLambdaOrNull(unit.civInfo.gold >= goldCostOfUpgrade
            && !unit.isEmbarked()
            && unit.currentMovement == unit.getMaxMovement().toFloat()) {
                unit.civInfo.gold -= goldCostOfUpgrade
                unit.destroy()
                val newunit = unit.civInfo.placeUnitNearTile(unitTile.position, upgradedUnit.name)!!
                newunit.health = unit.health
                newunit.promotions = unit.promotions

                for (promotion in newunit.baseUnit.promotions)
                    if (promotion !in newunit.promotions.promotions)
                        newunit.promotions.addPromotion(promotion, true)

                newunit.updateUniques()
                newunit.updateVisibleTiles()
                newunit.currentMovement = 0f
            }

    private suspend fun SequenceScope<UnitAction>.yieldWorkerActions(unit: MapUnit, tile: TileInfo,
                                                                     worldScreen: WorldScreen,
                                                                     unitTable: UnitTable) {
        if (unit.hasUnique("Can build improvements on tiles"))
            yieldAll(sequence {
                yield(if (Constants.unitActionAutomation == unit.action)
                    UnitAction(
                            type = UnitActionType.StopAutomation,
                            action = { unit.action = null })
                else
                    UnitAction(
                            type = UnitActionType.Automate,
                            action = getLambdaOrNull(unit.currentMovement > 0) {
                                unit.action = Constants.unitActionAutomation
                                WorkerAutomation(unit).automateWorkerAction()
                            })
                )

                // Allow automate/unautomate when embarked, but not building improvements - see #1963
                if (!unit.isEmbarked())
                    yield(UnitAction(
                            type = UnitActionType.ConstructImprovement,
                            isCurrentAction = unit.currentTile.hasImprovementInProgress(),
                            action = getLambdaOrNull(unit.currentMovement > 0
                                    && !tile.isCityCenter()
                                    && unit.civInfo.gameInfo.ruleSet.tileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) }) {
                                worldScreen.game.setScreen(ImprovementPickerScreen(tile) { unitTable.selectedUnit = null })
                            }))
            })
    }

    private fun getLambdaOrNull(test: Boolean, lambda: () -> Unit): (() -> Unit)? =
            if (test) lambda else null

    fun getGreatPersonBuildActions(unit: MapUnit, tile: TileInfo): Sequence<() -> Unit> =
            getGreatPersonBuildActionsPair(unit, tile).map { it.second }

    private fun getGreatPersonBuildActionsPair(unit: MapUnit, tile: TileInfo): Sequence<Pair<String, () -> Unit>> =
        unit.getUniques().asSequence()
        .filter { it.startsWith(CAN_BUILD_IMPROVEMENT) }
        .map {
            val improvement = it.substring(CAN_BUILD_IMPROVEMENT.length)
            val action: (() -> Unit)? = getLambdaOrNull(unit.currentMovement > 0f
                    && !tile.isWater && !tile.isCityCenter()
                    && !tile.getLastTerrain().unbuildable) {
                tile.terrainFeature = null // remove forest/jungle/marsh
                tile.improvement = improvement
                tile.improvementInProgress = null
                tile.turnsToImprovement = 0
            }
            if (action == null) null else (improvement to action)
        }.filterNotNull()

    private fun getGreatPersonActions(unit: MapUnit, tile: TileInfo): Sequence<UnitAction> = sequence {
        yieldAll(getGreatPersonBuildActionsPair(unit, tile)
                .map {
                    UnitAction(
                            type = UnitActionType.Create,
                            title = "Create [${it.first}]",
                            uncivSound = UncivSound.Chimes,
                            action = it.second)
                })

        if (!unit.isEmbarked()) {
            when (unit.name) {
                "Great Scientist" ->
                    yield(UnitAction(
                            type = UnitActionType.HurryResearch,
                            uncivSound = UncivSound.Chimes,
                            action = getLambdaOrNull(unit.currentMovement > 0
                                    && unit.civInfo.tech.currentTechnologyName() != null) {
                                unit.civInfo.tech.hurryResearch()
                                unit.destroy()
                            }))
                "Great Engineer" ->
                    yield(UnitAction(
                            type = UnitActionType.HurryWonder,
                            uncivSound = UncivSound.Chimes,
                            action = getLambdaOrNull(if (unit.currentMovement == 0f || !tile.isCityCenter()) false
                            else {
                                val currentConstruction = tile.getCity()!!.cityConstructions.getCurrentConstruction()
                                if (currentConstruction !is Building) false
                                else currentConstruction.isWonder || currentConstruction.isNationalWonder
                            }) {
                                tile.getCity()!!.cityConstructions.apply {
                                    addProductionPoints(
                                            300 + 30 * tile.getCity()!!.population.population
                                    ) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                                    constructIfEnough()
                                }
                                unit.destroy()
                            }))
                "Great Merchant" ->
                    yield(UnitAction(
                            type = UnitActionType.ConductTradeMission,
                            uncivSound = UncivSound.Chimes,
                            action = getLambdaOrNull(tile.owningCity?.civInfo?.isCityState() == true
                                    && tile.owningCity?.civInfo?.isAtWarWith(unit.civInfo) == false
                                    && unit.currentMovement > 0) {
                                // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                                var goldEarned = (350 + 50 * unit.civInfo.getEra().ordinal) *
                                        unit.civInfo.gameInfo.gameParameters.gameSpeed.modifier
                                if (unit.civInfo.policies.isAdopted("Commerce Complete"))
                                    goldEarned *= 2
                                unit.civInfo.gold += goldEarned.toInt()
                                val relevantUnique = unit.getUniques().first { it.startsWith("Can undertake") }
                                val influenceEarned = Regex("\\d+")
                                        .find(relevantUnique)!!.value.toInt()
                                tile.owningCity!!.civInfo.getDiplomacyManager(unit.civInfo)
                                        .influence += influenceEarned
                                unit.civInfo.addNotification(
                                        "Your trade mission to [${tile.owningCity!!.civInfo}] " +
                                                "has earned you [${goldEarned.toInt()}] gold and " +
                                                "[$influenceEarned] influence!", null, Color.GOLD)
                                unit.destroy()
                            }))
            }

            if (unit.hasUnique("Can start an 8-turn golden age"))
                yield(UnitAction(
                        type = UnitActionType.StartGoldenAge,
                        uncivSound = UncivSound.Chimes,
                        action = getLambdaOrNull(unit.currentMovement > 0) {
                            unit.civInfo.goldenAges.enterGoldenAge()
                            unit.destroy()
                        }))
        }
    }

    private suspend fun SequenceScope<UnitAction>.yieldFortifyActions(unit: MapUnit,
                                                                      unitTable: UnitTable) {
        if (unit.canFortify())
            yieldAll(sequence {
                val action = UnitAction(
                        type = UnitActionType.Fortify,
                        uncivSound = UncivSound.Fortify,
                        action = getLambdaOrNull(unit.currentMovement > 0) {
                            unit.fortify()
                            unitTable.selectedUnit = null
                        })

                if (unit.health < 100) {
                    yield(action.copy(
                            type = UnitActionType.FortifyUntilHealed,
                            title = UnitActionType.FortifyUntilHealed.value,
                            action = getLambdaOrNull(action.action != null) {
                                unit.fortifyUntilHealed()
                                unitTable.selectedUnit = null
                            }))
                }

                yield(action)
            })
        else if (unit.isFortified())
            yield(UnitAction(
                    type = if (unit.action!!.endsWith(" until healed"))
                        UnitActionType.FortifyUntilHealed else
                        UnitActionType.Fortify,
                    isCurrentAction = true,
                    title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            ))
    }

    private suspend fun SequenceScope<UnitAction>.yieldSleepActions(unit: MapUnit,
                                                                    unitTable: UnitTable) {
        val workingOnImprovement = unit.hasUnique("Can build improvements on tiles")
                && unit.currentTile.hasImprovementInProgress()
        if (!workingOnImprovement && unit.currentMovement > 0 && !unit.canFortify()) {
            yieldAll(sequence {
                val isSleeping = unit.isSleeping()

                val action = UnitAction(
                        type = UnitActionType.Sleep,
                        isCurrentAction = isSleeping,
                        action = getLambdaOrNull(!isSleeping) {
                            unit.action = Constants.unitActionSleep
                            unitTable.selectedUnit = null
                        })

                if (unit.health < 100 && !isSleeping) {
                    yield(action.copy(
                            type = UnitActionType.SleepUntilHealed,
                            title = UnitActionType.SleepUntilHealed.value,
                            action = getLambdaOrNull(action.action != null) {
                                unit.action = Constants.unitActionSleepUntilHealed
                                unitTable.selectedUnit = null
                            }))
                }

                yield(action)
            })
        }
    }

    fun canPillage(unit: MapUnit, tile: TileInfo): Boolean {
        if (tile.improvement == null || tile.improvement == Constants.barbarianEncampment
                || tile.improvement == Constants.ancientRuins
                || tile.improvement == "City ruins") return false
        val tileOwner = tile.getOwner()
        // Can't pillage friendly tiles, just like you can't attack them - it's an 'act of war' thing
        return tileOwner == null || tileOwner == unit.civInfo || unit.civInfo.isAtWarWith(tileOwner)
    }
}