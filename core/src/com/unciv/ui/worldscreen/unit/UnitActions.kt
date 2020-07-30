package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.UniqueAbility
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.utils.YesNoPopup
import com.unciv.ui.utils.hasOpenPopups
import com.unciv.ui.worldscreen.WorldScreen

object UnitActions {

    const val CAN_UNDERTAKE = "Can undertake"

    fun getUnitActions(unit: MapUnit, worldScreen: WorldScreen): List<UnitAction> {
        val tile = unit.getTile()
        val unitTable = worldScreen.bottomUnitTable
        val actionList = ArrayList<UnitAction>()

        if (unit.action != null && unit.action!!.startsWith("moveTo")) {
            actionList += UnitAction(
                    type = UnitActionType.StopMovement,
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
                    isCurrentAction = true,
                    title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            )
        }

        addExplorationActions(unit, actionList)
        addPromoteAction(unit, actionList)
        addUnitUpgradeAction(unit, actionList)
        addPillageAction(unit, actionList)
        addSetupAction(unit, actionList)
        addFoundCityAction(unit, actionList, tile)
        addWorkerActions(unit, actionList, tile, worldScreen, unitTable)
        addConstructRoadsAction(unit, tile, actionList)
        addCreateWaterImprovements(unit, actionList)
        addGreatPersonActions(unit, actionList, tile)
        actionList += getImprovementConstructionActions(unit, tile)
        addDisbandAction(actionList, unit, worldScreen)

        return actionList
    }


    private fun addDisbandAction(actionList: ArrayList<UnitAction>, unit: MapUnit, worldScreen: WorldScreen) {
        actionList += UnitAction(
                type = UnitActionType.DisbandUnit,
                action = {
                    if (!worldScreen.hasOpenPopups()) {
                        val disbandText = if (unit.currentTile.getOwner() == unit.civInfo)
                            "Disband this unit for [${unit.baseUnit.getDisbandGold()}] gold?".tr()
                        else "Do you really want to disband this unit?".tr()
                        YesNoPopup(disbandText, { unit.disband(); worldScreen.shouldUpdate = true }).open()
                    }
                }.takeIf {unit.currentMovement > 0} )
    }

    private fun addCreateWaterImprovements(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        val waterImprovementAction = getWaterImprovementAction(unit)
        if(waterImprovementAction!=null) actionList += waterImprovementAction
    }

    fun getWaterImprovementAction(unit: MapUnit): UnitAction? {
        val tile = unit.currentTile
        for (improvement in listOf("Fishing Boats", "Oil well")) {
            if (unit.hasUnique("May create improvements on water resources") && tile.resource != null
                    && tile.isWater // because fishing boats can enter cities, and if there's oil in the city... ;)
                    && tile.improvement == null
                    && tile.getTileResource().improvement == improvement
                    && unit.civInfo.tech.isResearched(unit.civInfo.gameInfo.ruleSet.tileImprovements[improvement]!!.techRequired!!)
            )
                return UnitAction(
                        type = UnitActionType.Create,
                        title = "Create [$improvement]",
                        action = {
                            tile.improvement = improvement
                            unit.destroy()
                        }.takeIf {unit.currentMovement > 0})
        }
        return null
    }

    private fun addConstructRoadsAction(unit: MapUnit, tile: TileInfo, actionList: ArrayList<UnitAction>) {
        val improvement = RoadStatus.Road.improvement(unit.civInfo.gameInfo.ruleSet) ?: return
        if (unit.hasUnique("Can construct roads")
                && tile.roadStatus == RoadStatus.None
                && tile.improvementInProgress != "Road"
                && tile.isLand
                && (improvement.techRequired==null || unit.civInfo.tech.isResearched(improvement.techRequired!!)))
            actionList += UnitAction(
                    type = UnitActionType.ConstructRoad,
                    action = {
                        tile.improvementInProgress = "Road"
                        tile.turnsToImprovement = improvement.getTurnsToBuild(unit.civInfo)
                    }.takeIf { unit.currentMovement > 0 })
    }

    private fun addFoundCityAction(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {
        val getFoundCityAction = getFoundCityAction(unit, tile)
        if (getFoundCityAction != null) actionList += getFoundCityAction
    }

    fun getFoundCityAction(unit:MapUnit, tile: TileInfo): UnitAction? {
        if (!unit.hasUnique("Founds a new city") || tile.isWater) return null
        return UnitAction(
                type = UnitActionType.FoundCity,
                uncivSound = UncivSound.Chimes,
                action = {
                    UncivGame.Current.settings.addCompletedTutorialTask("Found city")
                    unit.civInfo.addCity(tile.position)
                    tile.improvement = null
                    unit.destroy()
                }.takeIf { unit.currentMovement > 0 && !tile.getTilesInDistance(3).any { it.isCityCenter() } })
    }

    private fun addPromoteAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.type.isCivilian() || !unit.promotions.canBePromoted()) return
        // promotion does not consume movement points, so we can do it always
        actionList += UnitAction(
                type = UnitActionType.Promote,
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
                isCurrentAction = isSetUp,
                uncivSound = UncivSound.Setup,
                action = {
                    unit.action = Constants.unitActionSetUp
                    unit.useMovementPoints(1f)
                }.takeIf { unit.currentMovement > 0 && !isSetUp })
    }

    private fun addPillageAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        val pillageAction = getPillageAction(unit)
        if(pillageAction!=null) actionList += pillageAction
    }

    fun getPillageAction(unit: MapUnit): UnitAction? {
        val tile = unit.currentTile
        if (unit.type.isCivilian() || tile.improvement == null) return null
        return UnitAction(
                type = UnitActionType.Pillage,
                action = {
                    // http://well-of-souls.com/civ/civ5_improvements.html says that naval improvements are destroyed upon pilllage
                    //    and I can't find any other sources so I'll go with that
                    if (tile.isLand) {
                        tile.improvementInProgress = tile.improvement
                        tile.turnsToImprovement = 2
                    }
                    tile.improvement = null
                    if (tile.resource!=null) tile.getOwner()?.updateDetailedCivResources()    // this might take away a resource

                    if (!unit.hasUnique("No movement cost to pillage") &&
                            (!unit.type.isMelee() || unit.civInfo.nation.unique != UniqueAbility.VIKING_FURY))
                                    unit.useMovementPoints(1f)

                    unit.healBy(25)

                }.takeIf { unit.currentMovement > 0 && canPillage(unit, tile) })
    }

    private fun addExplorationActions(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.type.isAirUnit()) return
        if (unit.action != Constants.unitActionExplore) {
            actionList += UnitAction(
                    type = UnitActionType.Explore,
                    action = {
                        unit.action = Constants.unitActionExplore
                        if(unit.currentMovement>0) UnitAutomation.automatedExplore(unit)
                    })
        } else {
            actionList += UnitAction(
                    type = UnitActionType.StopExploration,
                    action = { unit.action = null }
            )
        }
    }

    private fun addUnitUpgradeAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        val upgradeAction = getUpgradeAction(unit)
        if(upgradeAction!=null) actionList += upgradeAction
    }

    fun getUpgradeAction(unit: MapUnit): UnitAction? {
        val tile = unit.currentTile
        if (unit.baseUnit().upgradesTo == null || tile.getOwner() != unit.civInfo
                || !unit.canUpgrade()) return null
        val goldCostOfUpgrade = unit.getCostOfUpgrade()
        val upgradedUnit = unit.getUnitToUpgradeTo()

        return UnitAction(
                type = UnitActionType.Upgrade,
                title = "Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)",
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
                }.takeIf {
                    unit.civInfo.gold >= goldCostOfUpgrade && !unit.isEmbarked()
                            && unit.currentMovement == unit.getMaxMovement().toFloat()
                })
    }

    private fun addWorkerActions(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo, worldScreen: WorldScreen, unitTable: UnitTable) {
        if (!unit.hasUnique("Can build improvements on tiles")) return

        // Allow automate/unautomate when embarked, but not building improvements - see #1963
        if (Constants.unitActionAutomation == unit.action) {
            actionList += UnitAction(
                    type = UnitActionType.StopAutomation,
                    action = { unit.action = null }
            )
        } else {
            actionList += UnitAction(
                    type = UnitActionType.Automate,
                    action = {
                        unit.action = Constants.unitActionAutomation
                        WorkerAutomation(unit).automateWorkerAction()
                    }.takeIf { unit.currentMovement > 0 })
        }

        if(unit.isEmbarked()) return

        val canConstruct =unit.currentMovement > 0
                && !tile.isCityCenter()
                && unit.civInfo.gameInfo.ruleSet.tileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) }
        actionList += UnitAction(
                type = UnitActionType.ConstructImprovement,
                isCurrentAction = unit.currentTile.hasImprovementInProgress(),
                action = {
                    worldScreen.game.setScreen(ImprovementPickerScreen(tile) { unitTable.selectedUnit = null })
                }.takeIf { canConstruct })
    }

    private fun addGreatPersonActions(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {

        if (unit.hasUnique("Can hurry technology research") && !unit.isEmbarked()) {
            actionList += UnitAction(
                    type = UnitActionType.HurryResearch,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        unit.civInfo.tech.hurryResearch()
                        addGoldPerGreatPersonUsage(unit.civInfo)
                        unit.destroy()
                    }.takeIf { unit.civInfo.tech.currentTechnologyName() != null && unit.currentMovement > 0 })
        }

        if (unit.hasUnique("Can start an 8-turn golden age") && !unit.isEmbarked()) {
            actionList += UnitAction(
                    type = UnitActionType.StartGoldenAge,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        unit.civInfo.goldenAges.enterGoldenAge()
                        addGoldPerGreatPersonUsage(unit.civInfo)
                        unit.destroy()
                    }.takeIf { unit.currentMovement > 0 })
        }

        if (unit.hasUnique("Can speed up construction of a wonder") && !unit.isEmbarked()) {
            val canHurryWonder = if (unit.currentMovement == 0f || !tile.isCityCenter()) false
            else {
                val currentConstruction = tile.getCity()!!.cityConstructions.getCurrentConstruction()
                if (currentConstruction !is Building) false
                else currentConstruction.isWonder || currentConstruction.isNationalWonder
            }
            actionList += UnitAction(
                    type = UnitActionType.HurryWonder,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        tile.getCity()!!.cityConstructions.apply {
                            addProductionPoints(300 + 30 * tile.getCity()!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                            constructIfEnough()
                        }
                        addGoldPerGreatPersonUsage(unit.civInfo)
                        unit.destroy()
                    }.takeIf { canHurryWonder })
        }

        if (unit.hasUnique("Can undertake a trade mission with City-State, giving a large sum of gold and [30] Influence")
                && !unit.isEmbarked()) {
            val canConductTradeMission = tile.owningCity?.civInfo?.isCityState() == true
                    && tile.owningCity?.civInfo?.isAtWarWith(unit.civInfo) == false
                    && unit.currentMovement > 0
            actionList += UnitAction(
                    type = UnitActionType.ConductTradeMission,
                    uncivSound = UncivSound.Chimes,
                    action = {
                        // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                        var goldEarned = (350 + 50 * unit.civInfo.getEraNumber()) * unit.civInfo.gameInfo.gameParameters.gameSpeed.modifier
                        if (unit.civInfo.hasUnique("Double gold from Great Merchant trade missions"))
                            goldEarned *= 2
                        unit.civInfo.gold += goldEarned.toInt()
                        val relevantUnique = unit.getUniques().first { it.startsWith(CAN_UNDERTAKE) }
                        val influenceEarned = Regex("\\d+").find(relevantUnique)!!.value.toInt()
                        tile.owningCity!!.civInfo.getDiplomacyManager(unit.civInfo).influence += influenceEarned
                        unit.civInfo.addNotification("Your trade mission to [${tile.owningCity!!.civInfo}] has earned you [${goldEarned.toInt()}] gold and [$influenceEarned] influence!", null, Color.GOLD)
                        addGoldPerGreatPersonUsage(unit.civInfo)
                        unit.destroy()
                    }.takeIf { canConductTradeMission })
        }
    }


    fun getImprovementConstructionActions(unit: MapUnit, tile: TileInfo): ArrayList<UnitAction> {
        val finalActions = ArrayList<UnitAction>()
        for (unique in unit.getUniques().filter { it.equalsPlaceholderText("Can construct []") }) {
            val improvementName = unique.getPlaceholderParameters()[0]
            finalActions +=  UnitAction(
                    type = UnitActionType.Create,
                    title = "Create [$improvementName]",
                    uncivSound = UncivSound.Chimes,
                    action = {
                        val unitTile = unit.getTile()
                        if (unitTile.terrainFeature != null &&
                                unitTile.ruleset.tileImprovements.containsKey("Remove " + unitTile.terrainFeature))
                            unitTile.terrainFeature = null // remove forest/jungle/marsh
                        unitTile.improvement = improvementName
                        unitTile.improvementInProgress = null
                        unitTile.turnsToImprovement = 0
                        if (improvementName == Constants.citadel)
                            takeOverTilesAround(unit)
                        val city = unitTile.getCity()
                        if (city != null) {
                            city.cityStats.update()
                            city.civInfo.updateDetailedCivResources()
                        }
                        addGoldPerGreatPersonUsage(unit.civInfo)
                        unit.destroy()
                    }.takeIf { unit.currentMovement > 0f && !tile.isWater &&
                            !tile.isCityCenter() && !tile.isImpassible() &&
                            tile.improvement != improvementName &&
                            // citadel can be built only next to or within own borders
                            (improvementName != Constants.citadel ||
                                    tile.neighbors.any { it.getOwner() == unit.civInfo })})
        }
        return finalActions
    }

    private fun takeOverTilesAround(unit: MapUnit) {
        // one of the neighbour tile must belong to unit's civ, so nearestCity will be never `null`
        val nearestCity = unit.currentTile.neighbors.first { it.getOwner() == unit.civInfo }.getCity()
        // capture all tiles which do not belong to unit's civ and are not enemy cities
        // we use getTilesInDistance here, not neighbours to include the current tile as well
        val tilesToTakeOver = unit.currentTile.getTilesInDistance(1)
                .filter { !it.isCityCenter() && it.getOwner() != unit.civInfo }
        // make a set of civs to be notified (a set - in order to not repeat notification on each tile)
        val notifications = mutableSetOf<CivilizationInfo>()
        // take over the ownership
        for (tile in tilesToTakeOver) {
            val otherCiv = tile.getOwner()
            if (otherCiv != null) {
                // decrease relations for -10 pt/tile
                if(!otherCiv.knows(unit.civInfo)) otherCiv.meetCivilization(unit.civInfo)
                otherCiv.getDiplomacyManager(unit.civInfo).addModifier(DiplomaticModifiers.StealingTerritory, -10f)
                notifications.add(otherCiv)
            }
            nearestCity!!.expansion.takeOwnership(tile)
        }
        for (otherCiv in notifications)
            otherCiv.addNotification("[${unit.civInfo}] has stolen your territory!", unit.currentTile.position, Color.RED)
    }

    private fun addGoldPerGreatPersonUsage(civInfo: CivilizationInfo) {
        val uniqueText = "Provides a sum of gold each time you spend a Great Person"
        val cityWithMausoleum = civInfo.cities.firstOrNull { it.containsBuildingUnique(uniqueText) }
                ?: return
        val goldEarned = (100 * civInfo.gameInfo.gameParameters.gameSpeed.modifier).toInt()
        civInfo.gold += goldEarned

        val mausoleum = cityWithMausoleum.cityConstructions.getBuiltBuildings().first { it.uniques.contains(uniqueText) }
        civInfo.addNotification("[${mausoleum.name}] has provided [$goldEarned] Gold!", cityWithMausoleum.location, Color.GOLD)
    }

    private fun addFortifyActions(actionList: ArrayList<UnitAction>, unit: MapUnit, unitTable: UnitTable) {

        val action = UnitAction(
                type = UnitActionType.Fortify,
                uncivSound = UncivSound.Fortify,
                action = {
                    unit.fortify()
                    unitTable.selectedUnit = null
                }.takeIf { unit.currentMovement > 0 })

        if (unit.health < 100) {
            val actionForWounded = action.copy(
                    type = UnitActionType.FortifyUntilHealed,
                    title = UnitActionType.FortifyUntilHealed.value,
                    action = {
                        unit.fortifyUntilHealed()
                        unitTable.selectedUnit = null
                    }.takeIf { unit.currentMovement > 0 })
            actionList += actionForWounded
        }

        actionList += action
    }

    private fun addSleepActions(actionList: ArrayList<UnitAction>, unit: MapUnit, unitTable: UnitTable) {

        val isSleeping = unit.isSleeping()

        val action = UnitAction(
                type = UnitActionType.Sleep,
                isCurrentAction = isSleeping,
                action = {
                    unit.action = Constants.unitActionSleep
                    unitTable.selectedUnit = null
                }.takeIf { !isSleeping })

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
