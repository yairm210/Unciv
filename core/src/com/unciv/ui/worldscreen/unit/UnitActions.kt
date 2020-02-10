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
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.utils.YesNoPopup
import com.unciv.ui.worldscreen.WorldScreen

class UnitActions {

    fun getUnitActions(unit: MapUnit, worldScreen: WorldScreen): List<UnitAction> {
        val tile = unit.getTile()
        val unitTable = worldScreen.bottomUnitTable
        val actionList = ArrayList<UnitAction>()

        if (unit.action != null && unit.action!!.startsWith("moveTo")) {
            actionList += UnitAction(
                    type = UnitActionType.StopMovement,
                    canAct = true,
                    action = { unit.action = null }
            )
        }

        val workingOnImprovement = unit.hasUnique("Can build improvements on tiles")
                                   && unit.currentTile.hasImprovementInProgress()
        if (!unit.isFortified() && !unit.canFortify()
                && unit.currentMovement > 0 && !workingOnImprovement) {
            addSleepActions(actionList, unit, unitTable)
        }

        if (unit.canFortify()) {
            addFortifyActions(actionList, unit, unitTable)
        } else if (unit.isFortified()) {
            actionList += UnitAction(
                    type = if (unit.action!!.endsWith(" until healed"))
                                UnitActionType.FortifyUntilHealed else
                                UnitActionType.Fortify,
                    canAct = false,
                    isCurrentAction = true,
                    title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            )
        }

        addExplorationActions(unit, actionList)
        addPromoteAction(unit, actionList)
        addUnitUpgradeAction(unit, tile, actionList, worldScreen)
        addPillageAction(unit, tile, actionList)
        addSetupAction(unit, actionList)
        addFoundCityAction(unit, actionList, tile)
        addWorkerActions(unit, actionList, tile, worldScreen, unitTable)
        addConstructRoadsAction(unit, tile, actionList)
        addCreateWaterImprovements(unit, tile, actionList)
        addGreatPersonActions(unit, actionList, tile)
        addDisbandAction(actionList, unit, worldScreen)

        return actionList
    }

    private fun addDisbandAction(actionList: ArrayList<UnitAction>, unit: MapUnit, worldScreen: WorldScreen) {
        actionList += UnitAction(
                type = UnitActionType.DisbandUnit,
                canAct = unit.currentMovement > 0,
                action = {
                    val disbandText = if (unit.currentTile.getOwner() == unit.civInfo)
                        "Disband this unit for [${unit.baseUnit.getDisbandGold()}] gold?".tr()
                    else "Do you really want to disband this unit?".tr()
                    YesNoPopup(disbandText, { unit.disband(); worldScreen.shouldUpdate = true }).open()
                })
    }

    private fun addCreateWaterImprovements(unit: MapUnit, tile: TileInfo, actionList: ArrayList<UnitAction>) {
        for (improvement in listOf("Fishing Boats", "Oil well")) {
            if (unit.hasUnique("May create improvements on water resources") && tile.resource != null
                    && tile.isWater // because fishing boats can enter cities, and if there's oil in the city... ;)
                    && tile.improvement == null
                    && tile.getTileResource().improvement == improvement
                    && unit.civInfo.tech.isResearched(unit.civInfo.gameInfo.ruleSet.tileImprovements[improvement]!!.techRequired!!)
            )
                actionList += UnitAction(
                        type = UnitActionType.Create,
                        title = "Create [$improvement]",
                        canAct = unit.currentMovement > 0,
                        action = {
                            tile.improvement = improvement
                            unit.destroy()
                        })
        }
    }

    private fun addConstructRoadsAction(unit: MapUnit, tile: TileInfo, actionList: ArrayList<UnitAction>) {
        if (unit.hasUnique("Can construct roads")
                && tile.roadStatus == RoadStatus.None
                && tile.improvementInProgress != "Road"
                && tile.isLand
                && unit.civInfo.tech.isResearched(RoadStatus.Road.improvement(unit.civInfo.gameInfo.ruleSet)!!.techRequired!!))
            actionList += UnitAction(
                    type = UnitActionType.ConstructRoad,
                    canAct = unit.currentMovement > 0,
                    action = {
                        tile.improvementInProgress = "Road"
                        tile.turnsToImprovement = 4
                    })
    }

    private fun addFoundCityAction(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {
        if (!unit.hasUnique("Founds a new city") || unit.isEmbarked()) return
        actionList += UnitAction(
                type = UnitActionType.FoundCity,
                canAct = unit.currentMovement > 0 && !tile.getTilesInDistance(3).any { it.isCityCenter() },
                uncivSound = UncivSound.Chimes,
                action = {
                    UncivGame.Current.settings.addCompletedTutorialTask("Found city")
                    unit.civInfo.addCity(tile.position)
                    tile.improvement = null
                    unit.destroy()
                })
    }

    private fun addPromoteAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.type.isCivilian() || !unit.promotions.canBePromoted()) return
        // promotion does not consume movement points, so we can do it always
        actionList += UnitAction(
                type = UnitActionType.Promote,
                canAct = true,
                uncivSound = UncivSound.Promote,
                action = {
                    UncivGame.Current.setScreen(PromotionPickerScreen(unit))
                })
    }

    private fun addSetupAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.hasUnique("Must set up to ranged attack") || unit.isEmbarked()) return
        val isSetUp = unit.action == "Set Up"
        actionList += UnitAction(
                type = UnitActionType.SetUp,
                canAct = unit.currentMovement > 0 && !isSetUp,
                isCurrentAction = isSetUp,
                uncivSound = UncivSound.Setup,
                action = {
                    unit.action = Constants.unitActionSetUp
                    unit.useMovementPoints(1f)
                })
    }

    private fun addPillageAction(unit: MapUnit, tile: TileInfo, actionList: ArrayList<UnitAction>) {
        if (unit.type.isCivilian() || tile.improvement == null) return
        actionList += UnitAction(
                type = UnitActionType.Pillage,
                canAct = unit.currentMovement > 0 && canPillage(unit, tile),
                action = {
                    // http://well-of-souls.com/civ/civ5_improvements.html says that naval improvements are destroyed upon pilllage
                    //    and I can't find any other sources so I'll go with that
                    if (tile.isLand) {
                        tile.improvementInProgress = tile.improvement
                        tile.turnsToImprovement = 2
                    }
                    tile.improvement = null
                    if (!unit.hasUnique("No movement cost to pillage")) unit.useMovementPoints(1f)
                    unit.healBy(25)
                })
    }

    private fun addExplorationActions(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.type.isAirUnit()) return
        if (unit.action != Constants.unitActionExplore) {
            actionList += UnitAction(
                    type = UnitActionType.Explore,
                    canAct = true,
                    action = {
                        UnitAutomation().automatedExplore(unit)
                        unit.action = Constants.unitActionExplore
                    })
        } else {
            actionList += UnitAction(
                    type = UnitActionType.StopExploration,
                    canAct = true,
                    action = { unit.action = null }
            )
        }
    }

    private fun addUnitUpgradeAction(unit: MapUnit, tile: TileInfo, actionList: ArrayList<UnitAction>, worldScreen: WorldScreen) {
        if (unit.baseUnit().upgradesTo == null || tile.getOwner() != unit.civInfo) return
        if (!unit.canUpgrade()) return
        val goldCostOfUpgrade = unit.getCostOfUpgrade()
        val upgradedUnit = unit.getUnitToUpgradeTo()

        actionList += UnitAction(
                type = UnitActionType.Upgrade,
                title = "Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)",
                canAct = unit.civInfo.gold >= goldCostOfUpgrade && !unit.isEmbarked() && unit.currentMovement == unit.getMaxMovement().toFloat(),
                uncivSound = UncivSound.Upgrade,
                action = {
                    unit.civInfo.gold -= goldCostOfUpgrade
                    val unitTile = unit.getTile()
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
                    worldScreen.shouldUpdate = true
                })
    }

    private fun addWorkerActions(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo, worldScreen: WorldScreen, unitTable: UnitTable) {
        if (!unit.hasUnique("Can build improvements on tiles") || unit.isEmbarked()) return
        actionList += UnitAction(
                type = UnitActionType.ConstructImprovement,
                canAct = unit.currentMovement > 0
                        && !tile.isCityCenter()
                        && unit.civInfo.gameInfo.ruleSet.tileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) },
                isCurrentAction = unit.currentTile.hasImprovementInProgress(),
                action = {
                    worldScreen.game.setScreen(ImprovementPickerScreen(tile) { unitTable.selectedUnit = null })
                })

        if (Constants.unitActionAutomation == unit.action) {
            actionList += UnitAction(
                    type = UnitActionType.StopAutomation,
                    canAct = true,
                    action = { unit.action = null }
            )
        } else {
            actionList += UnitAction(
                    type = UnitActionType.Automate,
                    canAct = unit.currentMovement > 0,
                    action = {
                        unit.action = Constants.unitActionAutomation
                        WorkerAutomation(unit).automateWorkerAction()
                    })
        }
    }

    private fun addGreatPersonActions(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {
        for (unique in unit.getUniques().filter { it.startsWith("Can build improvement: ") }) {
            val improvementName = unique.replace("Can build improvement: ", "")
            actionList += UnitAction(
                    type = UnitActionType.Create,
                    title = "Create [$improvementName]",
                    canAct = unit.currentMovement > 0f && !tile.isWater && !tile.isCityCenter() && !tile.getLastTerrain().unbuildable,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        unit.getTile().terrainFeature = null // remove forest/jungle/marsh
                        unit.getTile().improvement = improvementName
                        unit.getTile().improvementInProgress = null
                        unit.getTile().turnsToImprovement = 0
                        unit.destroy()
                    })
        }


        if (unit.name == "Great Scientist" && !unit.isEmbarked()) {
            actionList += UnitAction(
                    type = UnitActionType.HurryResearch,
                    canAct = unit.civInfo.tech.currentTechnologyName() != null && unit.currentMovement > 0,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        unit.civInfo.tech.hurryResearch()
                        unit.destroy()
                    })
        }

        if (unit.hasUnique("Can start an 8-turn golden age") && !unit.isEmbarked()) {
            actionList += UnitAction(
                    type = UnitActionType.StartGoldenAge,
                    canAct = unit.currentMovement > 0,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        unit.civInfo.goldenAges.enterGoldenAge()
                        unit.destroy()
                    })
        }

        if (unit.name == "Great Engineer" && !unit.isEmbarked()) {
            val canHurryWonder = if (unit.currentMovement == 0f || !tile.isCityCenter()) false
            else {
                val currentConstruction = tile.getCity()!!.cityConstructions.getCurrentConstruction()
                if (currentConstruction !is Building) false
                else currentConstruction.isWonder || currentConstruction.isNationalWonder
            }
            actionList += UnitAction(
                    type = UnitActionType.HurryWonder,
                    canAct = canHurryWonder,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        tile.getCity()!!.cityConstructions.apply {
                            addProductionPoints(300 + 30 * tile.getCity()!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                            constructIfEnough()
                        }
                        unit.destroy()
                    })
        }

        if (unit.name == "Great Merchant" && !unit.isEmbarked()) {
            val canConductTradeMission = tile.owningCity?.civInfo?.isCityState() == true
                    && tile.owningCity?.civInfo?.isAtWarWith(unit.civInfo) == false
                    && unit.currentMovement > 0
            actionList += UnitAction(
                    type = UnitActionType.ConductTradeMission,
                    canAct = canConductTradeMission,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                        var goldEarned = (350 + 50 * unit.civInfo.getEra().ordinal) * unit.civInfo.gameInfo.gameParameters.gameSpeed.modifier
                        if (unit.civInfo.policies.isAdopted("Commerce Complete"))
                            goldEarned *= 2
                        unit.civInfo.gold += goldEarned.toInt()
                        val relevantUnique = unit.getUniques().first { it.startsWith("Can undertake") }
                        val influenceEarned = Regex("\\d+").find(relevantUnique)!!.value.toInt()
                        tile.owningCity!!.civInfo.getDiplomacyManager(unit.civInfo).influence += influenceEarned
                        unit.civInfo.addNotification("Your trade mission to [${tile.owningCity!!.civInfo}] has earned you [${goldEarned.toInt()}] gold and [$influenceEarned] influence!", null, Color.GOLD)
                        unit.destroy()
                    })
        }
    }

    private fun addFortifyActions(actionList: ArrayList<UnitAction>, unit: MapUnit, unitTable: UnitTable) {

        val action = UnitAction(
                type = UnitActionType.Fortify,
                canAct = unit.currentMovement > 0,
                uncivSound = UncivSound.Fortify,
                action = {
                    unit.fortify()
                    unitTable.selectedUnit = null
                })

        if (unit.health < 100) {
            val actionForWounded = action.copy(
                    type = UnitActionType.FortifyUntilHealed,
                    title = UnitActionType.FortifyUntilHealed.value,
                    action = {
                        unit.fortifyUntilHealed()
                        unitTable.selectedUnit = null
                    })
            actionList += actionForWounded
        }

        actionList += action
    }

    private fun addSleepActions(actionList: ArrayList<UnitAction>, unit: MapUnit, unitTable: UnitTable) {

        val isSleeping = unit.isSleeping()

        val action = UnitAction(
                type = UnitActionType.Sleep,
                canAct = !isSleeping,
                isCurrentAction = isSleeping,
                action = {
                    unit.action = Constants.unitActionSleep
                    unitTable.selectedUnit = null
                })

        if (unit.health < 100 && !isSleeping) {
            val actionForWounded = action.copy(
                    type = UnitActionType.SleepUntilHealed,
                    title = UnitActionType.SleepUntilHealed.value,
                    action = {
                unit.action = Constants.unitActionSleepUntilHealed
                unitTable.selectedUnit = null
            })
            actionList += actionForWounded
        }

        actionList += action
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
