package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import kotlin.math.min

@Suppress("UNUSED_PARAMETER") // references need to have the signature expected by UnitActions.actionTypeToFunctions
object UnitActionsGreatPerson {

    internal fun getHurryResearchActions(unit: MapUnit, tile: Tile) = sequence {
        for (unique in unit.getMatchingUniques(UniqueType.CanHurryResearch)){
            yield(UnitAction(
                UnitActionType.HurryResearch, 76f,
                action = {
                    unit.civ.tech.addScience(unit.civ.tech.getScienceFromGreatScientist())
                    unit.consume()
                }.takeIf {
                    unit.hasMovement()
                        && unit.civ.tech.currentTechnologyName() != null
                        && !unit.civ.tech.currentTechnology()!!.hasUnique(UniqueType.CannotBeHurried)
                }
            ))
        }
    }

    internal fun getHurryPolicyActions(unit: MapUnit, tile: Tile) = sequence {
        for (unique in unit.getMatchingUniques(UniqueType.CanHurryPolicy)){
            yield(UnitAction(
                UnitActionType.HurryPolicy, 76f,
                action = {
                    unit.civ.policies.addCulture(unit.civ.policies.getCultureFromGreatWriter())
                    unit.consume()
                }.takeIf {unit.hasMovement()}
            ))
        }
    }

    internal fun getHurryWonderActions(unit: MapUnit, tile: Tile) = sequence {
        for (unique in unit.getMatchingUniques(UniqueType.CanSpeedupWonderConstruction)) {
            val canHurryWonder =
                if (!tile.isCityCenter()) false
                else tile.getCity()!!.cityConstructions.isBuildingWonder()
                    && tile.getCity()!!.cityConstructions.canBeHurried()

            yield(UnitAction(
                UnitActionType.HurryWonder, 75f,
                action = {
                    tile.getCity()!!.cityConstructions.apply {
                        //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                        addProductionPoints(((300 + 30 * tile.getCity()!!.population.population) * unit.civ.gameInfo.speed.productionCostModifier).toInt())
                        constructIfEnough()
                    }

                    unit.consume()
                }.takeIf { unit.hasMovement() && canHurryWonder }
            ))
        }
    }

    internal fun getHurryBuildingActions(unit: MapUnit, tile: Tile) = sequence {
        for (unique in unit.getMatchingUniques(UniqueType.CanSpeedupConstruction)) {
            if (!tile.isCityCenter()) {
                yield(UnitAction(UnitActionType.HurryBuilding, 75f, action = null))
                continue
            }

            val cityConstructions = tile.getCity()!!.cityConstructions
            val canHurryConstruction = cityConstructions.getCurrentConstruction() is Building
                && cityConstructions.canBeHurried()

            //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
            val productionPointsToAdd = min(
                (300 + 30 * tile.getCity()!!.population.population) * unit.civ.gameInfo.speed.productionCostModifier,
                cityConstructions.getRemainingWork(cityConstructions. currentConstructionName()).toFloat() - 1
            ).toInt()
            if (productionPointsToAdd <= 0) continue

            yield(UnitAction(
                UnitActionType.HurryBuilding, 75f,
                title = "Hurry Construction (+[$productionPointsToAdd]⚙)",
                action = {
                    cityConstructions.apply {
                        addProductionPoints(productionPointsToAdd)
                        constructIfEnough()
                    }

                    unit.consume()
                }.takeIf { unit.hasMovement() && canHurryConstruction }
            ))
        }
    }

    internal fun getConductTradeMissionActions(unit: MapUnit, tile: Tile) = sequence {
        for (unique in unit.getMatchingUniques(UniqueType.CanTradeWithCityStateForGoldAndInfluence)) {
            val canConductTradeMission = tile.owningCity?.civ?.isCityState == true
                && tile.owningCity?.civ != unit.civ
                && tile.owningCity?.civ?.isAtWarWith(unit.civ) == false
            val influenceEarned = unique.params[0].toFloat()

            yield(UnitAction(
                UnitActionType.ConductTradeMission, 70f,
                action = {
                    // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                    var goldEarned = (350 + 50 * unit.civ.getEraNumber()) * unit.civ.gameInfo.speed.goldCostModifier

                    // Apply the gold trade mission modifier
                    for (goldUnique in unit.getMatchingUniques(UniqueType.PercentGoldFromTradeMissions, checkCivInfoUniques = true))
                        goldEarned *= goldUnique.params[0].toPercent()

                    val goldEarnedInt = goldEarned.toInt()
                    unit.civ.addGold(goldEarnedInt)
                    val tileOwningCiv = tile.owningCity!!.civ

                    tileOwningCiv.getDiplomacyManager(unit.civ)!!.addInfluence(influenceEarned)
                    unit.civ.addNotification("Your trade mission to [$tileOwningCiv] has earned you [$goldEarnedInt] gold and [$influenceEarned] influence!",
                        NotificationCategory.General, tileOwningCiv.civName, NotificationIcon.Gold, NotificationIcon.Culture)
                    unit.consume()
                }.takeIf { unit.hasMovement() && canConductTradeMission }
            ))
        }
    }
}
