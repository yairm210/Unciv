package com.unciv.logic.map.mapunit.actions

import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toPercent
import kotlin.math.min

@Suppress("UNUSED_PARAMETER") // references need to have the signature expected by UnitActions.actionTypeToSingleFunctions and actionTypeToMultiFunctions
object UnitActionsGreatPerson {

    // The hurried amount doesn't depend on the unique, so the first matching one is enough
    internal fun getHurryResearchAction(unit: MapUnit, tile: Tile): UnitAction? {
        val unique = unit.getMatchingUniques(UniqueType.CanHurryResearch).firstOrNull() ?: return null
        return UnitAction(
            UnitActionType.HurryResearch,
            associatedUnique = unique,
            action = {
                unit.civ.tech.addScience(unit.civ.tech.getScienceFromGreatScientist())
                unit.consume()
            }.takeIf {
                unit.hasMovement()
                    && unit.civ.tech.currentTechnologyName() != null
                    && !unit.civ.tech.currentTechnology()!!.hasUnique(UniqueType.CannotBeHurried)
            }
        )
    }

    internal fun getHurryPolicyAction(unit: MapUnit, tile: Tile): UnitAction? {
        val unique = unit.getMatchingUniques(UniqueType.CanHurryPolicy).firstOrNull() ?: return null
        return UnitAction(
            UnitActionType.HurryPolicy,
            associatedUnique = unique,
            action = {
                unit.civ.policies.addCulture(unit.civ.policies.getCultureFromGreatWriter())
                unit.consume()
            }.takeIf {unit.hasMovement()}
        )
    }

    internal fun getHurryWonderAction(unit: MapUnit, tile: Tile): UnitAction? {
        val unique = unit.getMatchingUniques(UniqueType.CanSpeedupWonderConstruction).firstOrNull() ?: return null
        val canHurryWonder =
            if (!tile.isCityCenter()) false
            else tile.getCity()!!.cityConstructions.isBuildingWonder()
                && tile.getCity()!!.cityConstructions.canBeHurried()

        return UnitAction(
            UnitActionType.HurryWonder,
            associatedUnique = unique,
            action = {
                tile.getCity()!!.cityConstructions.apply {
                    //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                    addProductionPoints(((300 + 30 * tile.getCity()!!.population.population) * unit.civ.gameInfo.speed.productionCostModifier).toInt())
                    constructIfEnough()
                }

                unit.consume()
            }.takeIf { unit.hasMovement() && canHurryWonder }
        )
    }

    internal fun getHurryBuildingAction(unit: MapUnit, tile: Tile): UnitAction? {
        val unique = unit.getMatchingUniques(UniqueType.CanSpeedupConstruction).firstOrNull() ?: return null
        if (!tile.isCityCenter())
            return UnitAction(UnitActionType.HurryBuilding, associatedUnique = unique, action = null)

        val cityConstructions = tile.getCity()!!.cityConstructions
        val currentName = cityConstructions.currentConstructionName() // Once: the queue can change between two reads
        val canHurryConstruction = cityConstructions.getConstruction(currentName) is Building
            && cityConstructions.canBeHurried()

        //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
        val productionPointsToAdd = min(
            (300 + 30 * tile.getCity()!!.population.population) * unit.civ.gameInfo.speed.productionCostModifier,
            cityConstructions.getRemainingWork(currentName).toFloat() - 1
        ).toInt()
        if (productionPointsToAdd <= 0) return null

        return UnitAction(
            UnitActionType.HurryBuilding,
            title = "Hurry Construction (+[$productionPointsToAdd]⚙)",
            associatedUnique = unique,
            action = {
                cityConstructions.apply {
                    addProductionPoints(productionPointsToAdd)
                    constructIfEnough()
                }

                unit.consume()
            }.takeIf { unit.hasMovement() && canHurryConstruction }
        )
    }

    internal fun getConductTradeMissionAction(unit: MapUnit, tile: Tile): UnitAction? {
        val canConductTradeMission = tile.owningCity?.civ?.isCityState == true
            && tile.owningCity?.civ != unit.civ
            && tile.owningCity?.civ?.isAtWarWith(unit.civ) == false
        // Enabled-ness doesn't depend on the unique, so the one giving the most influence is the best
        val unique = unit.getMatchingUniques(UniqueType.CanTradeWithCityStateForGoldAndInfluence)
            .maxByOrNull { it.params[0].toFloat() } ?: return null
        val influenceEarned = unique.params[0].toFloat()

        return UnitAction(
            UnitActionType.ConductTradeMission,
            associatedUnique = unique,
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
                unit.civ.addNotification("Your trade mission to [$tileOwningCiv] has earned you [${goldEarnedInt.tr()}] gold and [${influenceEarned.tr()}] influence!",
                    NotificationCategory.General, tileOwningCiv.civName, NotificationIcon.Gold, NotificationIcon.Culture)
                unit.consume()
            }.takeIf { unit.hasMovement() && canConductTradeMission }
        )
    }
}
