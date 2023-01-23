package com.unciv.ui.worldscreen.unit.actions

import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.extensions.toPercent
import kotlin.math.min

object UnitActionsGreatPerson {

    internal fun addGreatPersonActions(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: Tile) {

        if (unit.currentMovement > 0) for (unique in unit.getUniques()) when (unique.type) {
            UniqueType.CanHurryResearch -> {
                actionList += UnitAction(
                    UnitActionType.HurryResearch,
                    action = {
                        unit.civInfo.tech.addScience(unit.civInfo.tech.getScienceFromGreatScientist())
                        unit.consume()
                    }.takeIf { unit.civInfo.tech.currentTechnologyName() != null
                            && !unit.civInfo.tech.currentTechnology()!!.hasUnique(UniqueType.CannotBeHurried) }
                )
            }
            UniqueType.StartGoldenAge -> {
                val turnsToGoldenAge = unique.params[0].toInt()
                actionList += UnitAction(
                    UnitActionType.StartGoldenAge,
                    action = {
                        unit.civInfo.goldenAges.enterGoldenAge(turnsToGoldenAge)
                        unit.consume()
                    }.takeIf { unit.currentTile.getOwner() != null && unit.currentTile.getOwner() == unit.civInfo }
                )
            }
            UniqueType.CanSpeedupWonderConstruction -> {
                val canHurryWonder =
                        if (!tile.isCityCenter()) false
                        else tile.getCity()!!.cityConstructions.isBuildingWonder()
                                && tile.getCity()!!.cityConstructions.canBeHurried()

                actionList += UnitAction(
                    UnitActionType.HurryWonder,
                    action = {
                        tile.getCity()!!.cityConstructions.apply {
                            //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                            addProductionPoints(((300 + 30 * tile.getCity()!!.population.population) * unit.civInfo.gameInfo.speed.productionCostModifier).toInt())
                            constructIfEnough()
                        }

                        unit.consume()
                    }.takeIf { canHurryWonder }
                )
            }

            UniqueType.CanSpeedupConstruction -> {
                if (!tile.isCityCenter()) {
                    actionList += UnitAction(UnitActionType.HurryBuilding, action = null)
                    continue
                }

                val cityConstructions = tile.getCity()!!.cityConstructions
                val canHurryConstruction = cityConstructions.getCurrentConstruction() is Building
                        && cityConstructions.canBeHurried()

                //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                val productionPointsToAdd = min(
                    (300 + 30 * tile.getCity()!!.population.population) * unit.civInfo.gameInfo.speed.productionCostModifier,
                    cityConstructions.getRemainingWork(cityConstructions.currentConstructionFromQueue).toFloat() - 1
                ).toInt()
                if (productionPointsToAdd <= 0) continue

                actionList += UnitAction(
                    UnitActionType.HurryBuilding,
                    title = "Hurry Construction (+[$productionPointsToAdd]⚙)",
                    action = {
                        cityConstructions.apply {
                            addProductionPoints(productionPointsToAdd)
                            constructIfEnough()
                        }

                        unit.consume()
                    }.takeIf { canHurryConstruction }
                )
            }
            UniqueType.CanTradeWithCityStateForGoldAndInfluence -> {
                val canConductTradeMission = tile.owningCity?.civInfo?.isCityState() == true
                        && tile.owningCity?.civInfo?.isAtWarWith(unit.civInfo) == false
                val influenceEarned = unique.params[0].toFloat()
                actionList += UnitAction(
                    UnitActionType.ConductTradeMission,
                    action = {
                        // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                        var goldEarned = (350 + 50 * unit.civInfo.getEraNumber()) * unit.civInfo.gameInfo.speed.goldCostModifier
                        for (goldUnique in unit.civInfo.getMatchingUniques(UniqueType.PercentGoldFromTradeMissions))
                            goldEarned *= goldUnique.params[0].toPercent()
                        unit.civInfo.addGold(goldEarned.toInt())
                        tile.owningCity!!.civInfo.getDiplomacyManager(unit.civInfo).addInfluence(influenceEarned)
                        unit.civInfo.addNotification("Your trade mission to [${tile.owningCity!!.civInfo}] has earned you [${goldEarned}] gold and [$influenceEarned] influence!",
                            NotificationCategory.General, tile.owningCity!!.civInfo.civName, NotificationIcon.Gold, NotificationIcon.Culture)
                        unit.consume()
                    }.takeIf { canConductTradeMission }
                )
            }
            else -> {}
        }
    }

}
