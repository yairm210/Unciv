package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions

object ReligiousUnitAutomation {

    fun automateMissionary(unit: MapUnit) {
        if (unit.religion != unit.civ.religionManager.religion?.name || unit.religion == null)
            return unit.disband()

        val ourCitiesWithoutReligion = unit.civ.cities.filter {
            it.religion.getMajorityReligion() != unit.civ.religionManager.religion
        }

        val city =
            if (ourCitiesWithoutReligion.any())
                ourCitiesWithoutReligion.minByOrNull { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            else unit.civ.gameInfo.getCities()
                .filter { it.religion.getMajorityReligion() != unit.civ.religionManager.religion }
                .filter { it.civ.knows(unit.civ) && !it.civ.isAtWarWith(unit.civ) }
                .filterNot { it.religion.isProtectedByInquisitor(unit.religion) }
                .minByOrNull { it.getCenterTile().aerialDistanceTo(unit.getTile()) }

        if (city == null) return
        val destination = city.getTiles()
            .filter { unit.movement.canMoveTo(it) || it == unit.getTile() }
            .sortedBy { it.aerialDistanceTo(unit.getTile()) }
            .firstOrNull { unit.movement.canReach(it) } ?: return

        unit.movement.headTowards(destination)

        if (unit.getTile() in city.getTiles() && unit.civ.religionManager.maySpreadReligionNow(unit)) {
            UnitActions.invokeUnitAction(unit, UnitActionType.SpreadReligion)
        }
    }

    fun automateInquisitor(unit: MapUnit) {
        val civReligion = unit.civ.religionManager.religion

        if (unit.religion != civReligion?.name || unit.religion == null)
            return unit.disband() // No need to keep a unit we can't use, as it only blocks religion spreads of religions other that its own

        val holyCity = unit.civ.religionManager.getHolyCity()
        val cityToConvert = determineBestInquisitorCityToConvert(unit) // Also returns null if the inquisitor can't convert cities
        val pressureDeficit =
            if (cityToConvert == null) 0
            else cityToConvert.religion.getPressureDeficit(civReligion?.name)

        val citiesToProtect = unit.civ.cities.asSequence()
            .filter { it.religion.getMajorityReligion() == civReligion }
            // We only look at cities that are not currently protected or are protected by us
            .filter { !it.religion.isProtectedByInquisitor() || unit.getTile() in it.getCenterTile().getTilesInDistance(1) }

        // cities with most populations will be prioritized by the AI
        val cityToProtect = citiesToProtect.maxByOrNull { it.population.population }

        val destinationCity: City? = when {
            cityToConvert != null
                && (cityToConvert == holyCity
                || pressureDeficit > Constants.aiPreferInquisitorOverMissionaryPressureDifference
                || cityToConvert.religion.isBlockedHolyCity && cityToConvert.religion.religionThisIsTheHolyCityOf == civReligion?.name
                ) && unit.hasUnique(UniqueType.CanRemoveHeresy) -> {
                cityToConvert
            }
            cityToProtect != null && unit.hasUnique(UniqueType.PreventSpreadingReligion) -> {
                if (holyCity != null && !holyCity.religion.isProtectedByInquisitor())
                    holyCity
                else cityToProtect
            }
            cityToConvert != null -> cityToConvert
            else -> null
        }

        if (destinationCity == null) return
        var destinationTile = destinationCity.getCenterTile()

        if (!unit.movement.canReach(destinationTile)
            // Wait for the addInCapital units to go to the city!
            || CivilianUnitAutomation.shouldClearTileForAddInCapitalUnits(unit, destinationTile)) {
            destinationTile = destinationTile.neighbors
                .filter { unit.movement.canMoveTo(it) || it == unit.getTile() }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movement.canReach(it) }
                ?: return
        }

        unit.movement.headTowards(destinationTile)

        if (cityToConvert != null && unit.getTile().getCity() == destinationCity) {
            UnitActions.invokeUnitAction(unit, UnitActionType.RemoveHeresy)
        }
    }

    private fun determineBestInquisitorCityToConvert(
        unit: MapUnit,
    ): City? {
        if (unit.religion != unit.civ.religionManager.religion?.name || !unit.hasUnique(UniqueType.CanRemoveHeresy))
            return null

        val holyCity = unit.civ.religionManager.getHolyCity()
        if (holyCity != null && holyCity.religion.getMajorityReligion() != unit.civ.religionManager.religion!!)
            return holyCity

        val blockedHolyCity = unit.civ.cities.firstOrNull { it.religion.isBlockedHolyCity && it.religion.religionThisIsTheHolyCityOf == unit.religion }
        if (blockedHolyCity != null)
            return blockedHolyCity

        return unit.civ.cities.asSequence()
            .filter { it.religion.getMajorityReligion() != null }
            .filter { it.religion.getMajorityReligion()!! != unit.civ.religionManager.religion }
            // Don't go if it takes too long
            .filter { it.getCenterTile().aerialDistanceTo(unit.currentTile) <= 20 }
            .maxByOrNull { it.religion.getPressureDeficit(unit.civ.religionManager.religion?.name) }
    }


    fun foundReligion(unit: MapUnit) {
        val cityToFoundReligionAt =
            if (unit.getTile().isCityCenter() && !unit.getTile().owningCity!!.isHolyCity()) unit.getTile().owningCity
            else unit.civ.cities.firstOrNull {
                !it.isHolyCity()
                    && unit.movement.canMoveTo(it.getCenterTile())
                    && unit.movement.canReach(it.getCenterTile())
            }
        if (cityToFoundReligionAt == null) return
        if (unit.getTile() != cityToFoundReligionAt.getCenterTile()) {
            unit.movement.headTowards(cityToFoundReligionAt.getCenterTile())
            return
        }

        UnitActions.invokeUnitAction(unit, UnitActionType.FoundReligion)
    }

    fun enhanceReligion(unit: MapUnit) {
        // Try go to a nearby city
        if (!unit.getTile().isCityCenter())
            UnitAutomation.tryEnterOwnClosestCity(unit)

        // If we were unable to go there this turn, unable to do anything else
        if (!unit.getTile().isCityCenter())
            return

        UnitActions.invokeUnitAction(unit, UnitActionType.EnhanceReligion)
    }

}
